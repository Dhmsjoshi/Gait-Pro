package com.gait.biomedicaltwin.services.cleanup;

import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import com.gait.biomedicaltwin.repositories.GaitSessionRepository;
import com.gait.biomedicaltwin.repositories.GaitSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionCleanupService {
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSnapshotRepository snapshotRepository;
    private final GaitSessionRepository sessionRepository;

    // CSV kahan save hogi uska path (Windows/Linux ke liye flexible)
    private final String EXPORT_PATH = System.getProperty("user.home") + "/GaitDataExports/";

    @Transactional
    public void archiveAndCleanupSession(UUID sessionId) {
        // 1. Check karo snapshots ban gaye hain
        if (snapshotRepository.countBySessionId(sessionId) == 0) {
            throw new RuntimeException("Snapshots not generated yet, aborting deletion!");
        }

        List<GaitDataPoint> points = dataPointRepository.findBySessionId(sessionId);

        // 2. CSV Generate karo
        if (generateCsvFile(sessionId, points)) {
            // 3. Success hone par hi delete karo
            dataPointRepository.deleteBySessionId(sessionId);

            // 4. Archive mark karo
            var session = sessionRepository.findById(sessionId).orElseThrow();
            session.setIsArchived(true);
            sessionRepository.save(session);
        }
    }

    private boolean generateCsvFile(UUID sessionId, List<GaitDataPoint> points) {
        try {
            File dir = new File(EXPORT_PATH);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "Session_" + sessionId + ".csv");
            try (FileWriter writer = new FileWriter(file)) {
                // Header ko entity fields ke sequence mein rakha hai
                writer.append("Timestamp,TrajectoryX,TrajectoryY,TrajectoryZ,RollOverParity,PitchAngleY,FootRollAngleX,IsSwingPhase\n");

                for (GaitDataPoint p : points) {
                    writer.append(p.toCsvLine());
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
