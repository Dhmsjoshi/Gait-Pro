package com.gait.biomedicaltwin.services.postprocess;

import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import com.gait.biomedicaltwin.repositories.GaitSessionRepository;
import com.gait.biomedicaltwin.services.snapshot.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class GaitPostProcessingServiceImpl implements GaitPostProcessingService{
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSessionRepository sessionRepository;
    private final SnapshotService snapshotService;

    private static final double FATIGUE_CADENCE_THRESHOLD = 70.0;
    private static final double TEMP_CRITICAL_THRESHOLD = 38.5;
    private static final int BATCH_SIZE = 100;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void processSessionMetricsAsync(UUID sessionId) {
        log.info("⏳ [POST-PROCESS] Starting Pipeline for Session ID: {}", sessionId);

        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        List<GaitDataPoint> allPoints = dataPointRepository.findBySessionId(sessionId);
        if (allPoints.isEmpty()) {
            log.warn("⚠️ [POST-PROCESS] No data points found in DB for session: {}, aborting.", sessionId);
            return;
        }

        log.info("📊 [POST-PROCESS] Loaded {} points from DB. Grouping by foot sides...", allPoints.size());

        Map<FootSide, List<GaitDataPoint>> groupedByFoot = allPoints.stream()
                .collect(Collectors.groupingBy(GaitDataPoint::getFootSide));

        List<GaitDataPoint> leftPoints = groupedByFoot.getOrDefault(FootSide.LEFT, List.of());
        List<GaitDataPoint> rightPoints = groupedByFoot.getOrDefault(FootSide.RIGHT, List.of());

        int fatigueCount = 0;
        int validSymmetryCount = 0;

        // ==========================================
        // PHASE 1: Complete Mathematical Calculation In-Memory
        // ==========================================
        log.info("🧮 [POST-PROCESS] Running calculation formulas over loops...");
        for (GaitDataPoint point : allPoints) {

            // 1. Fatigue Logic + Explicit Logs
            Double temp = point.getTemperatureC();
            Integer cadence = point.getCurrentCadence();

            boolean isFatigued = (temp != null && temp > TEMP_CRITICAL_THRESHOLD) &&
                    (cadence != null && cadence < FATIGUE_CADENCE_THRESHOLD);

            point.setIsFatigued(isFatigued);
            if (isFatigued) {
                fatigueCount++;
                log.debug("🔥 [FATIGUE-HIT] Point ID: {} | Temp: {}°C | Cadence: {}", point.getId(), temp, cadence);
            }

            // 2. Symmetry Logic
            List<GaitDataPoint> oppositeFootPoints = (point.getFootSide() == FootSide.LEFT) ? rightPoints : leftPoints;
            GaitDataPoint matchingStep = findClosestOppositeStep(point, oppositeFootPoints);

            if (matchingStep != null && point.getStancePhaseDurationMs() != null && matchingStep.getStancePhaseDurationMs() != null) {
                double diff = Math.abs(point.getStancePhaseDurationMs() - matchingStep.getStancePhaseDurationMs());
                double avg = (point.getStancePhaseDurationMs() + matchingStep.getStancePhaseDurationMs()) / 2.0;

                if (avg > 0) {
                    double symmetryIndex = (diff / avg) * 100;
                    point.setSymmetryIndex(Math.round(symmetryIndex * 100.0) / 100.0);
                    validSymmetryCount++;
                }
            } else {
                point.setSymmetryIndex(0.0);
            }
        }

        log.info("📈 [POST-PROCESS] Math Done! Detected Fatigue Points: {}/{} | Matched Symmetry Steps: {}",
                fatigueCount, allPoints.size(), validSymmetryCount);

        // ==========================================
        // PHASE 2: Forced Database State Syncing via Explicit Saves
        // ==========================================
        log.info("💾 [POST-PROCESS] Executing Managed State Entity Updates via JPA in batches...");
        List<GaitDataPoint> batchList = new ArrayList<>();
        for (int i = 0; i < allPoints.size(); i++) {
            batchList.add(allPoints.get(i));

            if (batchList.size() >= BATCH_SIZE || i == allPoints.size() - 1) {
                // saveAll + flush explicitly ensures data transitions from memory onto DB transaction segments
                dataPointRepository.saveAll(batchList);
                dataPointRepository.flush();
                batchList.clear();
            }
        }
        log.info("✅ [POST-PROCESS] Database successfully updated and flushed.");

        // ==========================================
        // PHASE 3: Milestone Checking
        // ==========================================
        log.info("🚀 [POST-PROCESS] Instantiating Snapshot Generator Service Layer...");
        snapshotService.checkAndTriggerSnapshots(session);

        // Mark session processed
        session.setIsProcessed(true);
        sessionRepository.save(session);
        log.info("🎉 [POST-PROCESS] Loop pipeline execution finished cleanly.");
    }

    private GaitDataPoint findClosestOppositeStep(GaitDataPoint currentPoint, List<GaitDataPoint> oppositePoints) {
        GaitDataPoint closest = null;
        long minDiff = Long.MAX_VALUE;

        for (GaitDataPoint opp : oppositePoints) {
            if (currentPoint.getTimestamp() == null || opp.getTimestamp() == null) continue;

            long diff = Math.abs(Duration.between(currentPoint.getTimestamp(), opp.getTimestamp()).toMillis());
            if (diff < minDiff && diff <= 1500) {
                minDiff = diff;
                closest = opp;
            }
        }
        return closest;

    }
}
