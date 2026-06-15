package com.gait.biomedicaltwin.services.analytics;

import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService{
    // --- Industrial Standards & Thresholds ---
    private static final double MAX_IMPACT_G = 2.5;
    private static final double MAX_ROLL_ANGLE = 8.0;
    private static final double RADIUS_FACTOR = 0.18;

    private final GaitDataPointRepository dataPointRepository;

    @Override
    public void performBioMechanicalAnalysis(GaitDataPoint dp) {

        // 1. Pitch Rate Gating Logic (Swing vs Stance)
        GaitDataPoint lastDp = dataPointRepository
                .findTopBySession_IdOrderByTimestampDesc(dp.getSession().getId())
                .orElse(null);

        boolean isSwing = false;
        if (lastDp != null) {
            long timeDiff = Duration.between(lastDp.getTimestamp(), dp.getTimestamp()).toMillis();
            if (timeDiff > 0) {
                // Pitch Rate logic: Degrees per millisecond
                double pitchRate = Math.abs(dp.getPitchAngleY() - lastDp.getPitchAngleY()) / timeDiff;
                isSwing = pitchRate > 0.05; // 0.05 deg/ms threshold
            }
        }
        dp.setIsSwingPhase(isSwing);

        // 2. Trajectory Calculation (Only for Stance Phase)
        if (!isSwing) {
            double height = dp.getSession().getUser().getHeightCm();
            double R = height * RADIUS_FACTOR;
            calculateVectorTrajectory(dp, R);
        } else {
            // Explicitly set to zero to avoid NullPointer/Garbage data in SCADA
            dp.setTrajectoryX(0.0);
            dp.setTrajectoryY(0.0);
            dp.setTrajectoryZ(0.0);
        }

        // 3. Core Instantaneous Analysis Execution
        calculateFaultyStep(dp);
        calculateStanceAndFlex(dp);
        calculateCadence(dp);
        calculateRollOverParity(dp);

        // Note: Symmetry and Fatigue statuses are handled at the session boundary level
        // inside GaitPostProcessingServiceImpl asynchronously to maintain MQTT execution throughput.
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

        // Strategy B: Coupled 3D Rigid Body Equations
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double cosRoll = Math.cos(rollRad);
        double sinRoll = Math.sin(rollRad);

        // X = R * sin(pitch)
        dp.setTrajectoryX(Math.round((R * sinPitch) * 10000.0) / 10000.0);

        // Y = R * cos(pitch) * sin(roll)
        dp.setTrajectoryY(Math.round((R * cosPitch * sinRoll) * 10000.0) / 10000.0);

        // Z = R * (1 - cos(pitch) * cos(roll))
        dp.setTrajectoryZ(Math.round((R * (1 - (cosPitch * cosRoll))) * 10000.0) / 10000.0);
    }
}
