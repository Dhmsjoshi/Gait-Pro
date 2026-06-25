package com.gait.biomedicaltwin.services.scheduled;


import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.entities.GaitSession;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import com.gait.biomedicaltwin.repositories.GaitSessionRepository;
import com.gait.biomedicaltwin.services.postprocess.GaitPostProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionTimeoutScheduler {
    private final GaitSessionRepository sessionRepository;
    private final GaitDataPointRepository dataPointRepository;
    private final GaitPostProcessingService postProcessingService;

    private static final long SESSION_TIMEOUT_MINUTES = 5;

    // 🔥 Har 1 minute mein background mein automatic chalega
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void checkAndCloseTimedOutSessions() {
        log.info("🔍 [SCHEDULER] Checking for inactive gait sessions...");

        // Saare active sessions dhoondo jinka endTime null hai
        List<GaitSession> activeSessions = sessionRepository.findByEndTimeIsNull();

        for (GaitSession session : activeSessions) {
            // Us session ka aakhri data point ka time nikalo
            LocalDateTime lastDataTime = dataPointRepository.findTopBySession_IdOrderByTimestampDesc(session.getId())
                    .map(GaitDataPoint::getTimestamp)
                    .orElse(session.getStartTime());

            // Agar aakhri message se lekar abhi tak 5 minute ya usse zyada ka gap ho chuka hai
            if (Duration.between(lastDataTime, LocalDateTime.now()).toMinutes() >= SESSION_TIMEOUT_MINUTES) {
                log.info("📢 [SCHEDULER] Inactivity detected for Session: {}. Closing automatically.", session.getId());

                session.setEndTime(LocalDateTime.now());
                sessionRepository.save(session);

                // 🔥 Trigger background calculations and snapshot builder!
                postProcessingService.processSessionMetricsAsync(session.getId());
            }
        }
    }
}
