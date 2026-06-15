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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl implements SnapshotService {
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void checkAndTriggerSnapshots(GaitSession session) {
        double currentDist = calculateProgress(session);
        int interval = (int) (currentDist / 250);

        // Check window constraint bounds (250m to 1000m)
        if (interval >= 1 && interval <= 4) {
            double distance = interval * 250.0;
            if (!snapshotRepository.existsBySessionIdAndDistanceInterval(session.getId(), distance)) {
                generateSnapshot(session, distance, FootSide.LEFT);
                generateSnapshot(session, distance, FootSide.RIGHT);
            }
        }
    }

    private double calculateProgress(GaitSession session) {
        Long totalStance = dataPointRepository.sumStanceDurationBySessionId(session.getId());
        return (totalStance == null) ? 0.0 : (totalStance / 1000.0) * 1.2;
    }

    @SneakyThrows
    private void generateSnapshot(GaitSession session, double distance, FootSide side) {
        // 1. Fetch unique step identifiers for grouping constraints
        List<UUID> stepIds = dataPointRepository.findUniqueStepIdsBySession(session.getId(), side);
        if (stepIds == null || stepIds.isEmpty()) return;

        // 2. Heavy aggregates mathematical calculations
        Double avgParity = dataPointRepository.calculateAvgParityByStepIds(stepIds);
        Double avgSym = dataPointRepository.calculateAvgSymmetryByStepIds(stepIds);

        // 3. 🔥 FATIGUE INDEX SCORING MECHANISM
        // Counts how many points crossed fatigue parameters in this current dataset segment
        Long totalPoints = dataPointRepository.countByStepIdIn(stepIds);
        Long fatiguePoints = dataPointRepository.countByStepIdInAndIsFatiguedTrue(stepIds);

        Double fatigueScore = 0.0;
        if (totalPoints != null && totalPoints > 0 && fatiguePoints != null) {
            fatigueScore = ((double) fatiguePoints / totalPoints) * 100.0;
        }

        // 4. Downsampling logic for SCADA rendering (Takes every 5th item boundary)
        List<GaitDataPoint> allPoints = dataPointRepository.findByStepIdIn(stepIds);
        List<Map<String, Double>> curveData = allPoints.stream()
                .filter(p -> p.getId().hashCode() % 5 == 0)
                .map(p -> {
                    Map<String, Double> coords = new HashMap<>();
                    coords.put("x", p.getTrajectoryX());
                    coords.put("y", p.getTrajectoryY());
                    coords.put("z", p.getTrajectoryZ());
                    return coords;
                }).collect(Collectors.toList());

        String jsonString = objectMapper.writeValueAsString(curveData);

        // 5. Build and persist consolidated model state
        GaitSnapshot snapshot = new GaitSnapshot();
        snapshot.setSession(session);
        snapshot.setDistanceInterval(distance);
        snapshot.setFootSide(side);
        snapshot.setAvgRosParity(avgParity != null ? avgParity : 0.0);
        snapshot.setAvgSymmetryIndex(avgSym != null ? avgSym : 0.0);

        // Persisting computed fatigue index values inside snapshot rows
        snapshot.setFatigueIndex(Math.round(fatigueScore * 100.0) / 100.0);
        snapshot.setTrajectoryJson(jsonString);

        snapshotRepository.save(snapshot);
    }

}
