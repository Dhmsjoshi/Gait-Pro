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
    private final SnapshotService snapshotService; // 🔥 Injecting Snapshot Service

    private static final double FATIGUE_CADENCE_THRESHOLD = 70;
    private static final double TEMP_CRITICAL_THRESHOLD = 38.5;

    @Override
    @Async("taskExecutor")
    @Transactional
    public void processSessionMetricsAsync(UUID sessionId) {
        log.info("⏳ Starting Async Post-Processing for Session: {}", sessionId);

        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        List<GaitDataPoint> allPoints = dataPointRepository.findBySessionId(sessionId);
        if (allPoints.isEmpty()) {
            log.warn("⚠️ No data points found for session: {}, skipping.", sessionId);
            return;
        }

        Map<FootSide, List<GaitDataPoint>> groupedByFoot = allPoints.stream()
                .collect(Collectors.groupingBy(GaitDataPoint::getFootSide));

        List<GaitDataPoint> leftPoints = groupedByFoot.getOrDefault(FootSide.LEFT, List.of());
        List<GaitDataPoint> rightPoints = groupedByFoot.getOrDefault(FootSide.RIGHT, List.of());

        for (GaitDataPoint point : allPoints) {
            // Fatigue Status Calculation
            boolean isFatigued = (point.getTemperatureC() > TEMP_CRITICAL_THRESHOLD) &&
                    (point.getCurrentCadence() != null && point.getCurrentCadence() < FATIGUE_CADENCE_THRESHOLD);
            point.setIsFatigued(isFatigued);

            // Symmetry Pairing Logic
            List<GaitDataPoint> oppositeFootPoints = (point.getFootSide() == FootSide.LEFT) ? rightPoints : leftPoints;
            GaitDataPoint matchingStep = findClosestOppositeStep(point, oppositeFootPoints);

            if (matchingStep != null && point.getStancePhaseDurationMs() != null && matchingStep.getStancePhaseDurationMs() != null) {
                double diff = Math.abs(point.getStancePhaseDurationMs() - matchingStep.getStancePhaseDurationMs());
                double avg = (point.getStancePhaseDurationMs() + matchingStep.getStancePhaseDurationMs()) / 2.0;

                if (avg > 0) {
                    double symmetryIndex = (diff / avg) * 100;
                    point.setSymmetryIndex(Math.round(symmetryIndex * 100.0) / 100.0);
                }
            } else {
                point.setSymmetryIndex(0.0);
            }
        }

        // 4. Batch Save updated values with all metrics populated
        dataPointRepository.saveAll(allPoints);

        // 🔥 5. Trigger snapshots generation now that everything is fully populated
        log.info("📸 Database synchronized. Triggering Snapshots with fully processed data for Session: {}", sessionId);
        snapshotService.checkAndTriggerSnapshots(session);

        // 6. Session mark as processed successfully!
        session.setIsProcessed(true);
        sessionRepository.save(session);

        log.info("🎉 Post-Processing completed successfully for Session: {}", sessionId);
    }

    private GaitDataPoint findClosestOppositeStep(GaitDataPoint currentPoint, List<GaitDataPoint> oppositePoints) {
        GaitDataPoint closest = null;
        long minDiff = Long.MAX_VALUE;

        for (GaitDataPoint opp : oppositePoints) {
            long diff = Math.abs(java.time.Duration.between(currentPoint.getTimestamp(), opp.getTimestamp()).toMillis());
            if (diff < minDiff && diff <= 1500) {
                minDiff = diff;
                closest = opp;
            }
        }
        return closest;
    }
}
