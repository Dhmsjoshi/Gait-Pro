package com.gait.biomedicaltwin.repositories;

import com.gait.biomedicaltwin.entities.GaitSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GaitSessionRepository extends JpaRepository<GaitSession, UUID> {
    Optional<GaitSession> findByUserIdAndEndTimeIsNull(UUID userId);


    List<GaitSession> findAllByEndTimeIsNull();
}
