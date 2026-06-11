package com.gait.biomedicaltwin.repositories;

import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.entities.GaitSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GaitDataPointRepository extends JpaRepository<GaitDataPoint, Long> {
    // --- EXISTING METHODS ---
    GaitDataPoint findTopBySessionIdAndFootSideNotOrderByTimestampDesc(UUID sessionId, FootSide footSide);
    List<GaitDataPoint> findTop20BySessionUserIdOrderByTimestampDesc(UUID userId);
    Optional<GaitDataPoint> findTopBySessionIdOrderByTimestampDesc(UUID sessionId);
    List<GaitDataPoint> findBySessionIdAndFootSide(UUID sessionId, FootSide side);
    Optional<GaitDataPoint> findTopBySessionIdAndFootSideOrderByTimestampDesc(UUID sessionId, FootSide side);

    // --- CLEANUP & ARCHIVAL METHODS ---
    // Session ke saare points fetch karne ke liye (CSV export ke liye)
    @Query("SELECT d FROM GaitDataPoint d WHERE d.session.id = :sessionId")
    List<GaitDataPoint> findBySessionId(@Param("sessionId") UUID sessionId);

    // Session ke saare points delete karne ke liye (Cleanup service ke liye)
    @Modifying
    @Transactional
    @Query("DELETE FROM GaitDataPoint d WHERE d.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);

    // --- SNAPSHOT & ANALYTICS METHODS ---
    @Query("SELECT SUM(d.stancePhaseDurationMs) FROM GaitDataPoint d WHERE d.session.id = :sessionId")
    Long sumStanceDurationBySessionId(@Param("sessionId") UUID sessionId);

    @Query("SELECT DISTINCT d.stepId FROM GaitDataPoint d WHERE d.session.id = :sessionId AND d.footSide = :side AND d.stepId IS NOT NULL")
    List<UUID> findUniqueStepIdsBySession(@Param("sessionId") UUID sessionId, @Param("side") FootSide side);

    @Query("SELECT AVG(d.rollOverParity) FROM GaitDataPoint d WHERE d.stepId IN :stepIds")
    Double calculateAvgParityByStepIds(@Param("stepIds") List<UUID> stepIds);

    @Query("SELECT AVG(d.symmetryIndex) FROM GaitDataPoint d WHERE d.stepId IN :stepIds")
    Double calculateAvgSymmetryByStepIds(@Param("stepIds") List<UUID> stepIds);

    // Step IDs ke basis par points nikalne ke liye (Curve JSON generate karne ke liye)
    List<GaitDataPoint> findByStepIdIn(List<UUID> stepIds);
}
