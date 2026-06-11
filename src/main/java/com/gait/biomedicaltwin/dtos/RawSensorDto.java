package com.gait.biomedicaltwin.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.*;


import java.util.UUID;

public record RawSensorDto(
        @NotNull(message = "User ID is required")
        UUID userId, // Yahan se milega user ka unique ID

//        @NotNull(message = "Session ID is required")
//        UUID sessionId,

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

        @NotNull
        Long stancePhaseDurationMs,
        @NotNull
        Long stepIntervalMs

        ) {
}
