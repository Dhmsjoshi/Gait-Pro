package com.gait.biomedicaltwin.repositories;

import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.entities.GaitSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GaitDataPointRepository extends JpaRepository<GaitDataPoint, Long> {

    // Analytics ke liye: Opposite foot ka last step dhundne ke liye
    GaitDataPoint findTopBySessionIdAndFootSideNotOrderByTimestampDesc(UUID sessionId, FootSide footSide);

    // Dashboard/SCADA ke liye: Latest stream
    List<GaitDataPoint> findTop20BySessionUserIdOrderByTimestampDesc(UUID userId);

    Optional<GaitDataPoint> findTopBySessionIdOrderByTimestampDesc(UUID sessionId);
    @Override
    GaitDataPoint  save(GaitDataPoint entity);
}
