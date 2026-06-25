package com.gait.biomedicaltwin.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.*;


import java.util.UUID;

public record RawSensorDto(
        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Session ID is required")
        UUID sessionId, // 🔥 UNCOMMENTED: Session tracking dynamic linkage ke liye mandatory hai

        @NotBlank(message = "Foot side (LEFT/RIGHT) is required")
        String footSide,

        @NotNull(message = "Impact data is required")
        @DecimalMin(value = "0.0", message = "Impact cannot be negative")
        Double impactShockwaveZ,

        @NotNull(message = "Roll angle is required")
        Double footRollAngleX,

        @NotNull(message = "Pitch angle is required")
        Double pitchAngleY,

        @NotNull(message = "Temperature is required")
        @DecimalMin(value = "-10.0") @DecimalMax(value = "60.0")
        Double temperatureC,

        @NotNull(message = "Humidity is required")
        @DecimalMin(value = "0.0") @DecimalMax(value = "100.0")
        Double humidityRh,

        // 🔥 ADDED: Sequential step tracking id from shoe sensor array
        @NotNull(message = "Step Sequence ID is required")
        Long stepId,

        @NotNull(message = "Stance duration is required")
        Long stancePhaseDurationMs,

        @NotNull(message = "Step interval is required")
        Long stepIntervalMs,

        @NotNull(message = "Hardware telemetry timestamp is required")
        Long timestampMs // Hardware invariant clock baseline
        ) {
}
