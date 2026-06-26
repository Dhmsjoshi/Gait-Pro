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

    // Session automatic timeout criteria config (5 Minutes)
    private static final long SESSION_TIMEOUT_MINUTES = 5;

    @Override
    public void saveAndAnalyze(RawSensorDto dto) {

        // 1. USER VALIDATION CHECK
        // Database se check karo ki user valid hai ya nahi
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new RuntimeException("❌ User not found with ID: " + dto.userId()));

        // Height verification check: Metrics calculation ke liye height zaruri hai
        if (user.getHeightCm() == null || user.getHeightCm() <= 0) {
            throw new RuntimeException("❌ User height not configured for biometric analysis. User ID: " + user.getId());
        }

        // 2. ACTIVE SESSION LOOKUP
        // User ki aisi session dhundo jiska endTime abhi tak NULL hai (Yaani running session)
        GaitSession activeSession = sessionRepository.findFirstByUser_IdAndEndTimeIsNullOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        // 3. SESSION TIMEOUT & CREATION LOGIC
        if (activeSession != null) {
            // Agar running session mili, toh check karo ki aakhri datapoint kab aaya tha
            LocalDateTime lastDataTime = dataPointRepository.findTopBySession_IdOrderByTimestampDesc(activeSession.getId())
                    .map(GaitDataPoint::getTimestamp)
                    .orElse(activeSession.getStartTime());

            // Check: Kya aakhri packet aaye huye 5 minute se zyada ho chuke hain?
            if (Duration.between(lastDataTime, LocalDateTime.now()).toMinutes() >= SESSION_TIMEOUT_MINUTES) {

                log.info("📢 [TIMEOUT DETECTED] Session inactive for {} mins. Closing Session: {}",
                        SESSION_TIMEOUT_MINUTES, activeSession.getId());

                // Old session ko timestamp dekar lock karo
                activeSession.setEndTime(LocalDateTime.now());
                sessionRepository.save(activeSession);

                // Background Thread trigger for multi-variable batch calculations
                postProcessingService.processSessionMetricsAsync(activeSession.getId());

                // Naye packets ke liye bilkul fresh session initialize karo
                activeSession = createNewSession(user);

            } else {
                // 🏃🧠 AAPKA LOGIC FLOW HERE:
                // User abhi chal raha hai (time delta < 5 mins). Nayi session nahi banegi,
                // purani activeSession hi as-is aage forward ho jayegi!
                log.debug("🏃 [SESSION-REUSED] User walking continuously. Reusing active Session ID: {}", activeSession.getId());
            }
        } else {
            // Agar koi active session database me mili hi nahi (Fresh Walk Scenario)
            log.info("🆕 [FRESH-START] No active session found for user. Creating a new entry sequence.");
            activeSession = createNewSession(user);
        }

        // 4. MAPPING DTO RECORD PROPERTIES DIRECTLY TO DATABASE ENTITY
        GaitDataPoint dataPoint = new GaitDataPoint();
        dataPoint.setSession(activeSession); // Link data point to our active/reused session
        dataPoint.setTimestamp(LocalDateTime.now()); // Server entry system time

        // 🔥 INDUSTRIAL IMPLEMENTATION FIELD CHANGES:
        dataPoint.setHardwareTimestampMs(dto.timestampMs()); // Firmware native internal clock timing
        dataPoint.setStepId(dto.stepId());                   // Chronological footprint tracking sequence ID

        // Standard metrics mapping conversions
        dataPoint.setFootSide(FootSide.valueOf(dto.footSide().toUpperCase()));
        dataPoint.setImpactShockWaveZ(dto.impactShockwaveZ());
        dataPoint.setFootRollAngleX(dto.footRollAngleX());
        dataPoint.setPitchAngleY(dto.pitchAngleY());
        dataPoint.setTemperatureC(dto.temperatureC());
        dataPoint.setHumidityRh(dto.humidityRh());
        dataPoint.setStancePhaseDurationMs(dto.stancePhaseDurationMs());
        dataPoint.setStepIntervalMs(dto.stepIntervalMs());

        // 5. REAL-TIME ENGINE EVALUATION
        // Data point save karne se pehle instantaneous gait logic evaluate karo
        analyticsService.performBioMechanicalAnalysis(dataPoint);

        // Raw matrix transmission write to DB
        dataPointRepository.save(dataPoint);

        log.debug("📦 [INGESTION-SUCCESS] Packet saved. Step ID: {} | Hardware Tick: {}",
                dataPoint.getStepId(), dataPoint.getHardwareTimestampMs());
    }

    /**
     * Helper Method: Creates a clean new session object state in database
     */
    private GaitSession createNewSession(User user) {
        GaitSession newSession = new GaitSession();
        newSession.setUser(user);
        newSession.setStartTime(LocalDateTime.now());
        newSession.setIsProcessed(false); // Post-processing is pending until session completes
        return sessionRepository.save(newSession);
    }

    /**
     * Test Interface Trigger: Forces active session boundary close immediately
     * without waiting for the 5-minute natural idle timeout rule.
     */
    @Override
    public void forceCloseSessionForTest(String userId) {
        GaitSession activeSession = sessionRepository.findFirstByUser_IdAndEndTimeIsNullOrderByCreatedAtDesc(UUID.fromString(userId))
                .orElse(null);

        if (activeSession != null) {
            activeSession.setEndTime(LocalDateTime.now());
            sessionRepository.save(activeSession);

            log.info("🔌 [TEST-FORCE-CLOSE] Session manually terminated for evaluation. Session ID: {}", activeSession.getId());

            // Fire and forget: Triggers immediate multi-step aggregation computations
            postProcessingService.processSessionMetricsAsync(activeSession.getId());
        } else {
            log.warn("⚠️ [TEST-FORCE-WARN] Manual close requested but no running session active for User ID: {}", userId);
        }
    }
}
