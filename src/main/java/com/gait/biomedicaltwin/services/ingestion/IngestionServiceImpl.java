package com.gait.biomedicaltwin.services.ingestion;

import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.entities.GaitSession;
import com.gait.biomedicaltwin.entities.User;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import com.gait.biomedicaltwin.repositories.GaitSessionRepository;
import com.gait.biomedicaltwin.repositories.UserRepository;
import com.gait.biomedicaltwin.services.analytics.AnalyticsService;
import com.gait.biomedicaltwin.services.postprocess.GaitPostProcessingService;
import com.gait.biomedicaltwin.services.snapshot.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class IngestionServiceImpl implements IngestionService{
    private final UserRepository userRepository;
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;
    private final GaitPostProcessingService postProcessingService;

    private static final long SESSION_TIMEOUT_MINUTES = 5;

    @Override
    public void saveAndAnalyze(RawSensorDto dto) {
        // 1. User Check
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getHeightCm() == null || user.getHeightCm() <= 0) {
            throw new RuntimeException("User height not configured for biometric analysis. User ID: " + user.getId());
        }

        // 2. Active Session Lookup (Production Safety Fix)
        GaitSession activeSession = sessionRepository.findFirstByUser_IdAndEndTimeIsNullOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        // 3. Session Timeout & Creation Logic
        if (activeSession != null) {
            // 🔥 Burst protection link: finding timeout using latest DB data entries
            LocalDateTime lastDataTime = dataPointRepository.findTopBySession_IdOrderByTimestampDesc(activeSession.getId())
                    .map(GaitDataPoint::getTimestamp)
                    .orElse(activeSession.getStartTime());

            if (Duration.between(lastDataTime, LocalDateTime.now()).toMinutes() >= SESSION_TIMEOUT_MINUTES) {
                activeSession.setEndTime(LocalDateTime.now());
                sessionRepository.save(activeSession);

                // Trigger background calculations for the closed session
                log.info("📢 Session timeout detected. Triggering background calculations for Session: {}", activeSession.getId());
                postProcessingService.processSessionMetricsAsync(activeSession.getId());

                activeSession = createNewSession(user);
            }
        } else {
            activeSession = createNewSession(user);
        }

        // 4. Mapping MQTT Dto Record directly to Database Entity
        GaitDataPoint dataPoint = new GaitDataPoint();
        dataPoint.setSession(activeSession);

        // System baseline logs time tracking
        dataPoint.setTimestamp(LocalDateTime.now());

        // 🔥 INDUSTRIAL SCENARIO FIX 1: Mapping Invariant Hardware Clock Metrics
        dataPoint.setHardwareTimestampMs(dto.timestampMs());

        // 🔥 INDUSTRIAL SCENARIO FIX 2: Mapping direct Sensor Step Tracking Context (Bypassing local UUID generator)
        dataPoint.setStepId(dto.stepId());

        dataPoint.setFootSide(FootSide.valueOf(dto.footSide().toUpperCase()));
        dataPoint.setImpactShockWaveZ(dto.impactShockwaveZ());
        dataPoint.setFootRollAngleX(dto.footRollAngleX());
        dataPoint.setPitchAngleY(dto.pitchAngleY());
        dataPoint.setTemperatureC(dto.temperatureC());
        dataPoint.setHumidityRh(dto.humidityRh());
        dataPoint.setStancePhaseDurationMs(dto.stancePhaseDurationMs());
        dataPoint.setStepIntervalMs(dto.stepIntervalMs());

        // 5. Real-Time Analysis (Trajectory, Burst Protection & Faults Engine Activation)
        analyticsService.performBioMechanicalAnalysis(dataPoint);

        // Raw points production storage storage commit
        dataPointRepository.save(dataPoint);

        log.debug("📦 [INGESTION-FLOW] Point processed successfully. Step ID: {} | Hardware Tick Time: {}",
                dataPoint.getStepId(), dataPoint.getHardwareTimestampMs());
    }

    private GaitSession createNewSession(User user) {
        GaitSession newSession = new GaitSession();
        newSession.setUser(user);
        newSession.setStartTime(LocalDateTime.now());
        newSession.setIsProcessed(false);
        return sessionRepository.save(newSession);
    }

    // 🔥 FORCE CLOSE FOR SIMULATOR TESTING
    @Override
    public void forceCloseSessionForTest(String userId) {
        GaitSession activeSession = sessionRepository.findFirstByUser_IdAndEndTimeIsNullOrderByCreatedAtDesc(UUID.fromString(userId))
                .orElse(null);

        if (activeSession != null) {
            activeSession.setEndTime(LocalDateTime.now());
            sessionRepository.save(activeSession);

            log.info("🔌 Test complete! Forcing close and async processing for Session: {}", activeSession.getId());

            // Is line se aapka Async config active hoga aur step-count blueprints trigger ho jayenge!
            postProcessingService.processSessionMetricsAsync(activeSession.getId());
        } else {
            log.warn("⚠️ No active session found to force close for user: {}", userId);
        }
    }
}
