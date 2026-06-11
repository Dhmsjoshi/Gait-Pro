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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class IngestionServiceImpl implements IngestionService{

    private final UserRepository userRepository;
    private final GaitDataPointRepository dataPointRepository;
    private final GaitSessionRepository sessionRepository;
    private final AnalyticsService analyticsService;

    private static final long SESSION_TIMEOUT_MINUTES = 5;

    @Override
    public void saveAndAnalyze(RawSensorDto dto) {
        //Here we will ensure that user has been added in db before his analysis
        // 1. User Check
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        //check height
        if (user.getHeightCm() == null || user.getHeightCm() <= 0) {
            throw new RuntimeException("User height not configured for biometric analysis. User ID: " + user.getId());
        }

        // 2. Active Session Lookup
        GaitSession activeSession = sessionRepository.findByUserIdAndEndTimeIsNull(user.getId())
                .orElse(null);

        // 3. Logic: Last Data Point check karo (Efficiency: O(1) query)
        if (activeSession != null) {
            LocalDateTime lastDataTime = dataPointRepository.findTopBySessionIdOrderByTimestampDesc(activeSession.getId())
                    .map(GaitDataPoint::getTimestamp)
                    .orElse(activeSession.getStartTime());

            if (Duration.between(lastDataTime, LocalDateTime.now()).toMinutes() >= SESSION_TIMEOUT_MINUTES) {
                activeSession.setEndTime(LocalDateTime.now());
                sessionRepository.save(activeSession);
                activeSession = createNewSession(user);
            }
        } else {
            activeSession = createNewSession(user);
        }

        // 4. Mapping & Analytics
        GaitDataPoint dataPoint = new GaitDataPoint();
        dataPoint.setSession(activeSession);
        dataPoint.setTimestamp(LocalDateTime.now());
        dataPoint.setFootSide(FootSide.valueOf(dto.footSide()));
        dataPoint.setImpactShockWaveZ(dto.impactShockwaveZ());
        dataPoint.setFootRollAngleX(dto.footRollAngleX());
        dataPoint.setPitchAngleY(dto.pitchAngleY());
        dataPoint.setTemperatureC(dto.temperatureC());
        dataPoint.setHumidityRh(dto.humidityRh());
        dataPoint.setStancePhaseDurationMs(dto.stancePhaseDurationMs());
        dataPoint.setStepIntervalMs(dto.stepIntervalMs());

        analyticsService.performBioMechanicalAnalysis(dataPoint);

        dataPointRepository.save(dataPoint);
    }

    private GaitSession createNewSession(User user) {
        GaitSession newSession = new GaitSession();
        newSession.setUser(user);
        newSession.setStartTime(LocalDateTime.now());
        return sessionRepository.save(newSession);
    }
}
