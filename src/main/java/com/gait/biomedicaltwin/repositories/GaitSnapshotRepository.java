package com.gait.biomedicaltwin.repositories;

import com.gait.biomedicaltwin.entities.GaitSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GaitSnapshotRepository extends JpaRepository<GaitSnapshot, UUID> {

    // 1. Check karne ke liye ki kya is session/distance ka snapshot exist karta hai
    boolean existsBySessionIdAndDistanceInterval(UUID sessionId, double distance);

    // 2. Deletion se pehle verify karne ke liye ki snapshots generate ho chuke hain
    long countBySessionId(UUID sessionId);


}
