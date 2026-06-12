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
    private static final int FATIGUE_CADENCE_THRESHOLD = 70;
    private static final double TEMP_CRITICAL_THRESHOLD = 38.5;
    private static final double PITCH_DIFF_THRESHOLD = 10.0;
    private static final double RADIUS_FACTOR = 0.18;

    private final GaitDataPointRepository dataPointRepository;

    @Override
    public void performBioMechanicalAnalysis(GaitDataPoint dp) {

        // 1. Pitch Rate Gating Logic (Swing vs Stance) - FIXED METHOD NAME
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
            dp.setTrajectoryZ(0.0);
            dp.setTrajectoryY(0.0);
        }

        // 3. Core Analysis Execution
        calculateFaultyStep(dp);
        calculateFatigueStatus(dp);
        calculateStanceAndFlex(dp);
        calculateCadenceAndSymmetry(dp);
        calculateRollOverParity(dp);
    }

    private void calculateFaultyStep(GaitDataPoint dp) {
        boolean isFaulty = (dp.getImpactShockWaveZ() > MAX_IMPACT_G) ||
                (Math.abs(dp.getFootRollAngleX()) > MAX_ROLL_ANGLE);
        dp.setIsFaultyStep(isFaulty);
    }

    private void calculateFatigueStatus(GaitDataPoint dp) {
        boolean isFatigued = (dp.getTemperatureC() > TEMP_CRITICAL_THRESHOLD) &&
                (dp.getCurrentCadence() != null && dp.getCurrentCadence() < FATIGUE_CADENCE_THRESHOLD);
        dp.setIsFatigued(isFatigued);
    }

    private void calculateStanceAndFlex(GaitDataPoint dp) {
        if (dp.getStancePhaseDurationMs() != null) {
            double flex = (dp.getPitchAngleY() * dp.getStancePhaseDurationMs()) / 1000.0;
            dp.setEffectiveFlexLength(Math.round(flex * 100.0) / 100.0);
        }
    }

    private void calculateCadenceAndSymmetry(GaitDataPoint dp) {
        // Cadence Logic
        if (dp.getStepIntervalMs() != null && dp.getStepIntervalMs() > 0) {
            int calculatedCadence = (int) (60000 / dp.getStepIntervalMs());
            dp.setCurrentCadence(calculatedCadence);
        }

        // Symmetry Index Logic - FIXED METHOD NAME
        GaitDataPoint lastStep = dataPointRepository
                .findTopBySession_IdAndFootSideNotOrderByTimestampDesc(
                        dp.getSession().getId(),
                        dp.getFootSide()
                );

        if (lastStep != null && lastStep.getStancePhaseDurationMs() != null && dp.getStancePhaseDurationMs() != null) {
            double diff = Math.abs(dp.getStancePhaseDurationMs() - lastStep.getStancePhaseDurationMs());
            double avg = (dp.getStancePhaseDurationMs() + lastStep.getStancePhaseDurationMs()) / 2.0;

            double symmetryIndex = (diff / avg) * 100;
            dp.setSymmetryIndex(Math.round(symmetryIndex * 100.0) / 100.0);
        } else {
            dp.setSymmetryIndex(0.0);
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
