package com.gait.biomedicaltwin.config.mqtt;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttPayloadBridge {
    private final ObjectMapper objectMapper;
    private final IngestionService ingestionService;

    // 🔥 Dynamic counters for simulator thread alignment and snapshot calculations consistency
    private static final AtomicLong INDUSTRIAL_STEP_COUNTER = new AtomicLong(1000L);
    private static final AtomicLong LAST_HARDWARE_TICK = new AtomicLong(System.currentTimeMillis());

    /**
     * FLOW STEP 5: Format Check & Router
     * MqttConfig se payload seedha yahan aata hai. Content parsing ke pehle routing criteria evaluation hoti hai.
     */
    public void bridgeIncomingPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("⚠️ [BRIDGE-WARN] Empty or blank payload dropped at gateway line.");
            return;
        }

        // Check format types using keyword signatures
        if (rawPayload.contains("\"values\"") || rawPayload.contains("'values'")) {
            log.debug("🏭 [BRIDGE-ROUTE] Routing to Case 2: Industrial KEPServerEX Format.");
            parseAndProcessKepwareFormat(rawPayload);
        } else {
            log.debug("🏃 [BRIDGE-ROUTE] Routing to Case 1: Wearable Direct Device Format.");
            parseAndProcessWearableFormat(rawPayload);
        }
    }

    /**
     * FLOW STEP 6 (Case 1): Wearable Device JSON Extractor
     * Standard device JSON parsing zone wrapped with local try-catch.
     */
    private void parseAndProcessWearableFormat(String rawPayload) {
        try {
            // Direct DTO mapping step
            RawSensorDto dto = objectMapper.readValue(rawPayload, RawSensorDto.class);

            // Handover to business layers
            ingestionService.saveAndAnalyze(dto);
        } catch (Exception e) {
            // Localized Fault Tolerance: Packet discard warning, application keeps running
            log.error("❌ [CASE-1-PARSE-ERROR] Failed to map direct wearable JSON payload to DTO structure -> {}", e.getMessage());
        }
    }

    /**
     * FLOW STEP 6 (Case 2): Industrial Complex Array Extractor (KEPServerEX)
     * Parses custom array structures safely, converts telemetry precision scales, and injects simulated step tracking variables.
     */
    private void parseAndProcessKepwareFormat(String rawPayload) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawPayload);
            JsonNode valuesArray = rootNode.get("values");

            if (valuesArray == null || !valuesArray.isArray()) {
                log.warn("⚠️ [CASE-2-WARN] Payload marked as Kepware but 'values' block is invalid or missing.");
                return;
            }

            // Industrial Baseline configuration metadata overrides
            UUID industrialUserId = UUID.fromString("d3b07384-d113-4956-bc7e-3617ec23f46f");
            UUID placeholderSessionId = UUID.randomUUID();

            // Temporary extraction variables container setup
            String footSide = "LEFT";
            Double impactShockwaveZ = 0.0;
            Double footRollAngleX = 0.0;
            Double pitchAngleY = 0.0;
            Double temperatureC = 0.0;
            Double humidityRh = 0.0;

            // Biomechanical timeline calibration constants
            Long stancePhaseDurationMs = 410L + (long)(Math.random() * 30);
            Long stepIntervalMs = 500L;

            // Dynamic payload elements collection loop
            for (JsonNode node : valuesArray) {
                String id = node.get("id").asText();
                double value = node.get("v").asDouble();

                if (id.endsWith("Pitch Angle Y")) {
                    pitchAngleY = value;
                } else if (id.endsWith("Foot Roll Angle X")) {
                    footRollAngleX = value;
                } else if (id.endsWith("Impact Shockwave Z") || id.endsWith("Impact ShockWave Z")) {
                    impactShockwaveZ = value / 10.0; // Scaled mathematical translation step (Python compatibility adjustment)
                } else if (id.endsWith("Temperature C")) {
                    temperatureC = value / 10.0;     // Scaling standard floating precision point values
                } else if (id.endsWith("Humidity Rh")) {
                    humidityRh = value;
                } else if (id.endsWith("FootSide") || id.endsWith("Foot Side")) {
                    footSide = (value == 1.0) ? "RIGHT" : "LEFT";
                }
            }

            // Biomechanical asymmetry balancer adjustment block (Triggers real runtime symmetry index evaluation)
            if ("RIGHT".equals(footSide)) {
                stancePhaseDurationMs += 35L;
            }

            // Invariant simulated hardware ticks continuity sequence setup
            long simulatedHardwareTimestamp = LAST_HARDWARE_TICK.addAndGet(stepIntervalMs);

            if (Math.random() > 0.85) {
                INDUSTRIAL_STEP_COUNTER.incrementAndGet();
            }
            long currentStepId = INDUSTRIAL_STEP_COUNTER.get();

            // Constructing new structural instance honoring exact DTO parameter order constraints
            RawSensorDto dto = new RawSensorDto(
                    industrialUserId,          // 1. UUID: userId
                    placeholderSessionId,      // 2. UUID: sessionId
                    footSide,                  // 3. String: footSide
                    impactShockwaveZ,          // 4. Double: impactShockwaveZ
                    footRollAngleX,            // 5. Double: footRollAngleX
                    pitchAngleY,               // 6. Double: pitchAngleY
                    temperatureC,              // 7. Double: temperatureC
                    humidityRh,                // 8. Double: humidityRh
                    simulatedHardwareTimestamp,// 9. Long: timestampMs
                    currentStepId,             // 10. Long: stepId
                    stancePhaseDurationMs,     // 11. Long: stancePhaseDurationMs
                    stepIntervalMs             // 12. Long: stepIntervalMs
            );

            // Handover parsed entity to core business service
            ingestionService.saveAndAnalyze(dto);

        } catch (Exception e) {
            // Localized Fault Tolerance: Dynamic block mapping safe drop strategy
            log.error("❌ [CASE-2-PARSE-ERROR] Execution aborted. Industrial Kepware array translation process failed -> {}", e.getMessage());
        }
    }
}
