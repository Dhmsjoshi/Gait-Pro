package com.gait.biomedicaltwin.services.scheduled;

import com.gait.biomedicaltwin.entities.GaitSession;
import com.gait.biomedicaltwin.repositories.GaitSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionCleanupService {
    private final GaitSessionRepository sessionRepository;

    @Scheduled(fixedRate = 60000) // Har 1 minute mein
    @Transactional
    public void closeIdleSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        // Un sabhi sessions ko nikalo jinka endTime null hai aur
        // jinmein 5 minute se koi activity nahi hui
        List<GaitSession> idleSessions = sessionRepository.findAllByEndTimeIsNull();

        for (GaitSession session : idleSessions) {
            // Agar last activity threshold se purani hai
            if (session.getUpdatedAt().isBefore(threshold)) {
                session.setEndTime(LocalDateTime.now());
                sessionRepository.save(session);
            }
        }
    }
}
