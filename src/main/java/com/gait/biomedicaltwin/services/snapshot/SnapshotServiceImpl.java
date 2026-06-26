package com.gait.biomedicaltwin.services.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.entities.GaitSession;
import com.gait.biomedicaltwin.entities.GaitSnapshot;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import com.gait.biomedicaltwin.repositories.GaitSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotServiceImpl implements SnapshotService {
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void checkAndTriggerSnapshots(GaitSession session) {
        List<GaitDataPoint> allSessionPointsInDb = dataPointRepository.findBySessionId(session.getId());
        log.info("🔍 [SNAPSHOT-TRIGGER] DB verification scan. Found total processed row metrics: {}", allSessionPointsInDb.size());

        if (allSessionPointsInDb.isEmpty()) {
            log.warn("⚠️ [SNAPSHOT-TRIGGER] Context database contains no traces for session, cancelling builder pipeline.");
            return;
        }

        double currentDist = calculateProgressFromUniqueSteps(session);
        int maxInterval = (int) (currentDist / 250);

        log.info("📊 [SNAPSHOT-TRIGGER] Cumulative Walk Evaluation: {}m | Available Milestones Block: {}", currentDist, maxInterval);

        if (maxInterval < 1) {
            log.warn("⚠️ [SNAPSHOT-TRIGGER] Walking metric profile below milestone entry limits ({}m), skipping snapshot generation.", currentDist);
            return;
        }

        for (int interval = 1; interval <= maxInterval && interval <= 4; interval++) {
            double distance = interval * 250.0;

            if (!snapshotRepository.existsBySessionIdAndDistanceInterval(session.getId(), distance)) {
                log.info("🚀 [SNAPSHOT-TRIGGER] Barrier match hit for milestone: {}m. Spawning left/right snapshots...", distance);
                generateSnapshotForInterval(session, distance, FootSide.LEFT, allSessionPointsInDb);
                generateSnapshotForInterval(session, distance, FootSide.RIGHT, allSessionPointsInDb);
            } else {
                log.info("ℹ️ [SNAPSHOT-TRIGGER] Milestone snapshot configuration for {}m already exists inside records. Skipping.", distance);
            }
        }
    }

    private double calculateProgressFromUniqueSteps(GaitSession session) {
        long totalUniqueSteps = dataPointRepository.countUniqueStepsBySessionId(session.getId());

        double userHeightCm = (session.getUser() != null && session.getUser().getHeightCm() > 0)
                ? session.getUser().getHeightCm() : 175.0;

        double strideLengthMeters = userHeightCm * 0.00415;
        double calculatedDistance = totalUniqueSteps * strideLengthMeters;

        log.info("🎯 [BIOMECHANICAL-INTEGRITY] Total Distinct Step IDs: {} | Calibrated Stride Scale: {}m | Total Computed Metres: {}m",
                totalUniqueSteps, Math.round(strideLengthMeters * 100.0)/100.0, Math.round(calculatedDistance * 100.0)/100.0);

        return calculatedDistance;
    }

    @SneakyThrows
    private void generateSnapshotForInterval(GaitSession session, double distance, FootSide side, List<GaitDataPoint> sourcePoints) {
        // 1. Filter out points specific to this foot side (Chronologically ordered from DB)
        List<GaitDataPoint> sessionSidePoints = sourcePoints.stream()
                .filter(p -> p.getFootSide() == side)
                .collect(Collectors.toList());

        if (sessionSidePoints.isEmpty()) {
            log.warn("⚠️ [SNAPSHOT-BUILDER] Skipping segment builder. Dataset empty matching criteria for foot side: {}", side);
            return;
        }

        // 2. Average Calculations (Averages stay across all cumulative points till this interval)
        double totalSymmetry = 0.0;
        int symmetryCount = 0;
        for (GaitDataPoint p : sessionSidePoints) {
            if (p.getSymmetryIndex() != null) {
                totalSymmetry += p.getSymmetryIndex();
                symmetryCount++;
            }
        }
        double avgSym = (symmetryCount > 0) ? (totalSymmetry / symmetryCount) : 0.0;

        long totalPoints = sessionSidePoints.size();
        long fatiguePoints = sessionSidePoints.stream()
                .filter(p -> p.getIsFatigued() != null && p.getIsFatigued())
                .count();
        double fatigueScore = (totalPoints > 0) ? ((double) fatiguePoints / totalPoints) * 100.0 : 0.0;

        double totalRosParity = 0.0;
        int rosCount = 0;
        for (GaitDataPoint p : sessionSidePoints) {
            if (p.getRollOverParity() != null) {
                totalRosParity += p.getRollOverParity();
                rosCount++;
            }
        }
        double avgRosParity = (rosCount > 0) ? (totalRosParity / rosCount) : 0.0;

        // 🧠 3. BIOMEDICAL LOGIC FIX: REVERSE SCAN FOR RECENT STEP ID (Long Variant)
        // Hamein is interval ka sabse taaza (recent) complete step chahiye taaki smooth Roll-Over curve bane.
        Long recentStepId = null;
        for (int i = sessionSidePoints.size() - 1; i >= 0; i--) {
            Long stepId = sessionSidePoints.get(i).getStepId();
            if (stepId != null && stepId > 0) { // Long type validation check
                recentStepId = stepId;
                break; // Sabse fresh recent Step ID milte hi scan rok do!
            }
        }

        List<Map<String, Double>> curveData = new ArrayList<>();

        if (recentStepId != null) {
            log.info("🎯 [SNAPSHOT-HD-CURVE] Milestone: {}m | Foot: {} | Extracting continuous sequence for Recent Step ID: {}",
                    distance, side, recentStepId);

            // 🔥 EFFECTIVELY FINAL FIX: Is local variable ko lock kiya taaki Lambda ise bina error ke use kar sake
            final Long finalRecentStepId = recentStepId;

            // Sirf us single Recent Step ID ke saare sequential high-definition points ko coordinate mapping me lo
            curveData = sessionSidePoints.stream()
                    .filter(p -> finalRecentStepId.equals(p.getStepId())) // Precise Object comparison for Long
                    .map(p -> {
                        Map<String, Double> coords = new HashMap<>();
                        coords.put("x", p.getTrajectoryX() != null ? p.getTrajectoryX() : 0.0);
                        coords.put("y", p.getTrajectoryY() != null ? p.getTrajectoryY() : 0.0);
                        coords.put("z", p.getTrajectoryZ() != null ? p.getTrajectoryZ() : 0.0);
                        return coords;
                    }).collect(Collectors.toList());
        } else {
            log.warn("⚠️ [SNAPSHOT-HD-CURVE] No valid Step ID sequence discovered for {} side at {}m context.", side, distance);
        }

        // Serialization of continuous single-step trajectory curve data
        String jsonString = objectMapper.writeValueAsString(curveData);

        // 4. Populate and Save the Gait Snapshot Entity Record
        GaitSnapshot snapshot = new GaitSnapshot();
        snapshot.setSession(session);
        snapshot.setDistanceInterval(distance);
        snapshot.setFootSide(side);
        snapshot.setAvgRosParity(Math.round(avgRosParity * 100.0) / 100.0);
        snapshot.setAvgSymmetryIndex(Math.round(avgSym * 100.0) / 100.0);
        snapshot.setFatigueIndex(Math.round(fatigueScore * 100.0) / 100.0);
        snapshot.setTrajectoryJson(jsonString); // Clean, sequential single-step high definition JSON

        snapshotRepository.save(snapshot);
        log.info("✅ [SNAPSHOT-SUCCESS] Record saved for {} side at {}m milestone! Symmetry: {}%, Fatigue Index: {}%, Points in Curve: {}",
                side, distance, snapshot.getAvgSymmetryIndex(), snapshot.getFatigueIndex(), curveData.size());
    }
}
