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
    private final ObjectMapper objectMapper; // JSON conversion ke liye

    @Override
    public void checkAndTriggerSnapshots(GaitSession session) {
        double currentDist = calculateProgress(session);
        int interval = (int)(currentDist / 250);

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

    @SneakyThrows // JSON serialization exceptions handle karne ke liye
    private void generateSnapshot(GaitSession session, double distance, FootSide side) {
        // 1. Unique Step IDs fetch karo
        List<UUID> stepIds = dataPointRepository.findUniqueStepIdsBySession(session.getId(), side);
        if (stepIds == null || stepIds.isEmpty()) return;

        // 2. Metrics calculate karo
        Double avgParity = dataPointRepository.calculateAvgParityByStepIds(stepIds);
        Double avgSym = dataPointRepository.calculateAvgSymmetryByStepIds(stepIds);

        // 3. Curve data (Trajectory) ko JSON mein convert karo
        // Performance ke liye yahan sirf har 5th point le rahe hain (Downsampling)
        List<GaitDataPoint> allPoints = dataPointRepository.findByStepIdIn(stepIds);
        List<Map<String, Double>> curveData = allPoints.stream()
                .filter(p -> p.getId().hashCode() % 5 == 0) // Downsampling logic
                .map(p -> {
                    Map<String, Double> coords = new HashMap<>();
                    coords.put("x", p.getTrajectoryX());
                    coords.put("y", p.getTrajectoryY());
                    coords.put("z", p.getTrajectoryZ());
                    return coords;
                }).collect(Collectors.toList());

        String jsonString = objectMapper.writeValueAsString(curveData);

        // 4. Snapshot object create karke save karo
        GaitSnapshot snapshot = new GaitSnapshot();
        snapshot.setSession(session);
        snapshot.setDistanceInterval(distance);
        snapshot.setFootSide(side);
        snapshot.setAvgRosParity(avgParity != null ? avgParity : 0.0);
        snapshot.setAvgSymmetryIndex(avgSym != null ? avgSym : 0.0);
        snapshot.setTrajectoryJson(jsonString); // JSON data save hua

        snapshotRepository.save(snapshot);
    }

}
