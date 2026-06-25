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

    // 🔥 Dynamic counters for Kepware data mapping
    private static final AtomicLong INDUSTRIAL_STEP_COUNTER = new AtomicLong(1000L);
    private static final AtomicLong LAST_HARDWARE_TICK = new AtomicLong(System.currentTimeMillis());

    public void bridgeIncomingPayload(String rawPayload) {
        try {
            if (rawPayload.contains("\"values\"") || rawPayload.contains("'values'")) {
                log.debug("🏭 [Case 2] Industrial Data Detected from KEPServerEX.");
                parseAndProcessKepwareFormat(rawPayload);
            } else {
                log.debug("🏃 [Case 1] Wearable Data Detected from Direct Hardware.");
                parseAndProcessWearableFormat(rawPayload);
            }
        } catch (Exception e) {
            log.error("❌ Critical Ingestion Error: Failed to route payload -> {}", e.getMessage());
        }
    }

    private void parseAndProcessWearableFormat(String rawPayload) throws Exception {
        RawSensorDto dto = objectMapper.readValue(rawPayload, RawSensorDto.class);
        ingestionService.saveAndAnalyze(dto);
    }

    private void parseAndProcessKepwareFormat(String rawPayload) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawPayload);
        JsonNode valuesArray = rootNode.get("values");

        if (valuesArray != null && valuesArray.isArray()) {
            // Compiler orders: 1st & 2nd arguments are java.util.UUID
            UUID industrialUserId = UUID.fromString("d3b07384-d113-4956-bc7e-3617ec23f46f");
            UUID placeholderSessionId = UUID.randomUUID(); // Fallback dynamic session boundary reference

            // Default Initializations
            String footSide = "LEFT";
            Double impactShockwaveZ = 0.0;
            Double footRollAngleX = 0.0;
            Double pitchAngleY = 0.0;
            Double temperatureC = 0.0;
            Double humidityRh = 0.0;

            // 🧠 DYNAMIC INJECTION FOR SNAPSHOT CALCULATIONS:
            Long stancePhaseDurationMs = 410L + (long)(Math.random() * 30);
            Long stepIntervalMs = 500L;

            for (JsonNode node : valuesArray) {
                String id = node.get("id").asText();
                double value = node.get("v").asDouble();

                if (id.endsWith("Pitch Angle Y")) {
                    pitchAngleY = value;
                } else if (id.endsWith("Foot Roll Angle X")) {
                    footRollAngleX = value;
                } else if (id.endsWith("Impact Shockwave Z") || id.endsWith("Impact ShockWave Z")) {
                    impactShockwaveZ = value / 10.0;
                } else if (id.endsWith("Temperature C")) {
                    temperatureC = value / 10.0;
                } else if (id.endsWith("Humidity Rh")) {
                    humidityRh = value;
                } else if (id.endsWith("FootSide") || id.endsWith("Foot Side")) {
                    footSide = (value == 1.0) ? "RIGHT" : "LEFT";
                }
            }

            if ("RIGHT".equals(footSide)) {
                stancePhaseDurationMs += 35L;
            }

            // Continuous hardware ticks mapping
            long simulatedHardwareTimestamp = LAST_HARDWARE_TICK.addAndGet(stepIntervalMs);

            if (Math.random() > 0.85) {
                INDUSTRIAL_STEP_COUNTER.incrementAndGet();
            }
            long currentStepId = INDUSTRIAL_STEP_COUNTER.get();

            // 🔥 EXACT ALIGNMENT MATCHING THE DTO RECORD PARAMETER SIGNATURE:
            // Required positional order:
            // 2 x UUID, 1 x String, 5 x Double, 4 x Long
            RawSensorDto dto = new RawSensorDto(
                    industrialUserId,          // 1. UUID: userId
                    placeholderSessionId,      // 2. UUID: sessionId (or metadata ID)
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

            ingestionService.saveAndAnalyze(dto);
        }
    }
}
