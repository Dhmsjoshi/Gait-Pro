package com.gait.biomedicaltwin.services.analytics;

import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService{
    private static final double MAX_IMPACT_G = 2.5;
    private static final double MAX_ROLL_ANGLE = 8.0;
    private static final double RADIUS_FACTOR = 0.18;

    private final GaitDataPointRepository dataPointRepository;

    @Override
    public void performBioMechanicalAnalysis(GaitDataPoint dp) {
        // Get last data point context for sequential tracking differential parameters
        GaitDataPoint lastDp = dataPointRepository
                .findTopBySession_IdAndFootSideOrderByHardwareTimestampMsDesc(dp.getSession().getId(), dp.getFootSide())
                .orElse(null);

        boolean isSwing = false;

        // 🔥 BURST PROTECTION MATRIX: Using native sensor timestamps invariant
        if (lastDp != null && lastDp.getHardwareTimestampMs() != null && dp.getHardwareTimestampMs() != null) {
            long timeDiffMs = dp.getHardwareTimestampMs() - lastDp.getHardwareTimestampMs();

            if (timeDiffMs > 0) {
                // Correctly spaced telemetric packet calculation flow
                double pitchRate = Math.abs(dp.getPitchAngleY() - lastDp.getPitchAngleY()) / timeDiffMs;
                isSwing = pitchRate > 0.05;
            } else {
                // 💡 Network congestion burst mode: Math falls back to boundary angle rules safely
                isSwing = Math.abs(dp.getPitchAngleY()) > 5.0;
            }
        } else {
            // Safe initial fallback processing logic
            isSwing = Math.abs(dp.getPitchAngleY()) > 5.0;
        }

        dp.setIsSwingPhase(isSwing);

        // Vector Trajectory spatial coordinate mapping
        if (!isSwing) {
            double height = (dp.getSession().getUser() != null && dp.getSession().getUser().getHeightCm() > 0)
                    ? dp.getSession().getUser().getHeightCm() : 175.0;
            double R = height * RADIUS_FACTOR;
            calculateVectorTrajectory(dp, R);
        } else {
            dp.setTrajectoryX(0.0);
            dp.setTrajectoryY(0.0);
            dp.setTrajectoryZ(0.0);
        }

        // Invoke supplementary analytics filters
        calculateFaultyStep(dp);
        calculateStanceAndFlex(dp);
        calculateCadence(dp);
        calculateRollOverParity(dp);
    }

    private void calculateFaultyStep(GaitDataPoint dp) {
        boolean isFaulty = (dp.getImpactShockWaveZ() > MAX_IMPACT_G) ||
                (Math.abs(dp.getFootRollAngleX()) > MAX_ROLL_ANGLE);
        dp.setIsFaultyStep(isFaulty);
    }

    private void calculateStanceAndFlex(GaitDataPoint dp) {
        if (dp.getStancePhaseDurationMs() != null) {
            double flex = (dp.getPitchAngleY() * dp.getStancePhaseDurationMs()) / 1000.0;
            dp.setEffectiveFlexLength(Math.round(flex * 100.0) / 100.0);
        }
    }

    private void calculateCadence(GaitDataPoint dp) {
        if (dp.getStepIntervalMs() != null && dp.getStepIntervalMs() > 0) {
            int calculatedCadence = (int) (60000 / dp.getStepIntervalMs());
            dp.setCurrentCadence(calculatedCadence);
        }
    }

    private void calculateRollOverParity(GaitDataPoint dp) {
        double parity = Math.pow(dp.getFootRollAngleX(), 2) + Math.pow(dp.getPitchAngleY(), 2);
        dp.setRollOverParity(Math.round(parity * 100.0) / 100.0);
    }

    private void calculateVectorTrajectory(GaitDataPoint dp, double R) {
        double pitchRad = Math.toRadians(dp.getPitchAngleY());
        double rollRad = Math.toRadians(dp.getFootRollAngleX());

        dp.setTrajectoryX(Math.round((R * Math.sin(pitchRad)) * 10000.0) / 10000.0);
        dp.setTrajectoryY(Math.round((R * Math.cos(pitchRad) * Math.sin(rollRad)) * 10000.0) / 10000.0);
        dp.setTrajectoryZ(Math.round((R * (1 - (Math.cos(pitchRad) * Math.cos(rollRad)))) * 10000.0) / 10000.0);
    }
}
