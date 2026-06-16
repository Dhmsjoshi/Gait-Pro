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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        double currentDist = calculateProgress(session);
        int maxInterval = (int) (currentDist / 250);

        log.info("📊 [SNAPSHOT-TRIGGER] Calculated Total Distance: {}m | Target Max Milestone Interval: {}", currentDist, maxInterval);

        if (maxInterval < 1) {
            log.warn("⚠️ [SNAPSHOT-TRIGGER] Distance is less than 250m ({}), skipping snapshot evaluation.", currentDist);
            return;
        }

        // Active transaction context se saare points ek baar mein read karna
        List<GaitDataPoint> allSessionPointsInDb = dataPointRepository.findBySessionId(session.getId());
        log.info("🔍 [SNAPSHOT-TRIGGER] DB Query verification read returned {} rows for analysis pipeline.", allSessionPointsInDb.size());

        for (int interval = 1; interval <= maxInterval && interval <= 4; interval++) {
            double distance = interval * 250.0;

            if (!snapshotRepository.existsBySessionIdAndDistanceInterval(session.getId(), distance)) {
                log.info("🚀 [SNAPSHOT-TRIGGER] Milestone checkpoint reached: {}m. Initiating builder block...", distance);
                generateSnapshotForInterval(session, distance, FootSide.LEFT, allSessionPointsInDb);
                generateSnapshotForInterval(session, distance, FootSide.RIGHT, allSessionPointsInDb);
            } else {
                log.info("ℹ️ [SNAPSHOT-TRIGGER] Snapshot for {}m already exists. Skipping duplicate execution.", distance);
            }
        }
    }

    private double calculateProgress(GaitSession session) {
        Long totalStance = dataPointRepository.sumStanceDurationBySessionId(session.getId());
        return (totalStance == null) ? 0.0 : (totalStance / 1000.0) * 1.2;
    }

    @SneakyThrows
    private void generateSnapshotForInterval(GaitSession session, double distance, FootSide side, List<GaitDataPoint> sourcePoints) {

        // Mapped list se specific foot side ke data points filter karna
        List<GaitDataPoint> sessionSidePoints = sourcePoints.stream()
                .filter(p -> p.getFootSide() == side)
                .collect(Collectors.toList());

        if (sessionSidePoints.isEmpty()) {
            log.warn("⚠️ [SNAPSHOT-BUILDER] No points matching FootSide: {} found. Generation skipped.", side);
            return;
        }

        // 1. Average Symmetry Index Calculation
        double totalSymmetry = 0.0;
        int symmetryCount = 0;
        for (GaitDataPoint p : sessionSidePoints) {
            if (p.getSymmetryIndex() != null) {
                totalSymmetry += p.getSymmetryIndex();
                symmetryCount++;
            }
        }
        double avgSym = (symmetryCount > 0) ? (totalSymmetry / symmetryCount) : 0.0;

        // 2. Fatigue Index Percentage Calculation
        long totalPoints = sessionSidePoints.size();
        long fatiguePoints = sessionSidePoints.stream()
                .filter(p -> p.getIsFatigued() != null && p.getIsFatigued())
                .count();
        double fatigueScore = (totalPoints > 0) ? ((double) fatiguePoints / totalPoints) * 100.0 : 0.0;

        // 🔥 REAL FIX: 3. Average Roll Over Parity Calculation (Bypassing hardcoded 0.0)
        double totalRosParity = 0.0;
        int rosCount = 0;
        for (GaitDataPoint p : sessionSidePoints) {
            if (p.getRollOverParity() != null) {
                totalRosParity += p.getRollOverParity();
                rosCount++;
            }
        }
        double avgRosParity = (rosCount > 0) ? (totalRosParity / rosCount) : 0.0;

        log.info("📢 [SNAPSHOT-BUILDER-DEBUG] Side: {} | Total Points: {} | Avg Symmetry: {} | Fatigue%: {} | Avg ROS Parity: {}",
                side, totalPoints, avgSym, fatigueScore, avgRosParity);

        // 4. Downsampling logic for SCADA rendering (Every 5th point hash code selection)
        List<Map<String, Double>> curveData = sessionSidePoints.stream()
                .filter(p -> p.getId() != null && p.getId().hashCode() % 5 == 0)
                .map(p -> {
                    Map<String, Double> coords = new HashMap<>();
                    coords.put("x", p.getTrajectoryX() != null ? p.getTrajectoryX() : 0.0);
                    coords.put("y", p.getTrajectoryY() != null ? p.getTrajectoryY() : 0.0);
                    coords.put("z", p.getTrajectoryZ() != null ? p.getTrajectoryZ() : 0.0);
                    return coords;
                }).collect(Collectors.toList());

        String jsonString = objectMapper.writeValueAsString(curveData);

        // 5. Entity State Generation and Persistence
        GaitSnapshot snapshot = new GaitSnapshot();
        snapshot.setSession(session);
        snapshot.setDistanceInterval(distance);
        snapshot.setFootSide(side);

        // Dynamic mathematical properties setting
        snapshot.setAvgRosParity(Math.round(avgRosParity * 100.0) / 100.0);
        snapshot.setAvgSymmetryIndex(Math.round(avgSym * 100.0) / 100.0);
        snapshot.setFatigueIndex(Math.round(fatigueScore * 100.0) / 100.0);
        snapshot.setTrajectoryJson(jsonString);

        snapshotRepository.save(snapshot);
        log.info("✅ [SNAPSHOT-SUCCESS] Saved Snapshot for {} foot at {}m! Symmetry: {}, Fatigue: {}, ROS Parity: {}",
                side, distance, snapshot.getAvgSymmetryIndex(), snapshot.getFatigueIndex(), snapshot.getAvgRosParity());
    }

}
