package com.gait.biomedicaltwin.config.mqtt;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttPayloadBridge {
    private final ObjectMapper objectMapper;
    private final IngestionService ingestionService; // Tumhari core analysis service

    public void bridgeIncomingPayload(String rawPayload) {
        try {
            // DETECTION LAYER: Check karo ki Kepware ka "values" array hai ya nahi
            if (rawPayload.contains("\"values\"") || rawPayload.contains("'values'")) {
                log.info("🏭 [Case 2] Industrial Data Detected from KEPServerEX. Normalizing format...");
                parseAndProcessKepwareFormat(rawPayload);
            } else {
                log.info("🏃 [Case 1] Wearable Data Detected from Direct Hardware. Parsing directly...");
                parseAndProcessWearableFormat(rawPayload);
            }
        } catch (Exception e) {
            log.error("❌ Critical Ingestion Error: Failed to route payload -> {}", e.getMessage());
        }
    }

    private void parseAndProcessWearableFormat(String rawPayload) throws Exception {
        RawSensorDto dto = objectMapper.readValue(rawPayload, RawSensorDto.class);
        // Tumhara exact architecture method call
        ingestionService.saveAndAnalyze(dto);
    }

    private void parseAndProcessKepwareFormat(String rawPayload) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawPayload);
        JsonNode valuesArray = rootNode.get("values");

        if (valuesArray != null && valuesArray.isArray()) {
            // Hum default values initialize kar rahe hain jo Modbus simulator mein nahi hain
            UUID industrialUserId = UUID.fromString("d3b07384-d113-4956-bc7e-3617ec23f46f");
            String footSide = "LEFT";
            Double impactShockwaveZ = 0.0;
            Double footRollAngleX = 0.0;
            Double pitchAngleY = 0.0;
            Double temperatureC = 0.0;
            Double humidityRh = 0.0;
            Long stancePhaseDurationMs = 420L; // Fallback simulation values
            Long stepIntervalMs = 500L;

            // Array loop processing
            for (JsonNode node : valuesArray) {
                String id = node.get("id").asText();
                double value = node.get("v").asDouble();

                if (id.endsWith("Pitch Angle Y")) {
                    pitchAngleY = Math.round(value * 100.0) / 100.0;
                } else if (id.endsWith("Foot Roll Angle X")) {
                    footRollAngleX = value;
                } else if (id.endsWith("Impact Shockwave Z")) {
                    impactShockwaveZ = value;
                } else if (id.endsWith("Temperature C")) {
                    temperatureC = value;
                } else if (id.endsWith("Humidity Rh")) {
                    humidityRh = value;
                } else if (id.endsWith("Foot Side")) {
                    footSide = (value == 1.0) ? "RIGHT" : "LEFT";
                }
            }

            // Record object creation matching your RawSensorDto exactly
            RawSensorDto dto = new RawSensorDto(
                    industrialUserId,
                    footSide,
                    impactShockwaveZ,
                    footRollAngleX,
                    pitchAngleY,
                    temperatureC,
                    humidityRh,
                    stancePhaseDurationMs,
                    stepIntervalMs
            );

            // Passing normalized data to your exact core engine method
            ingestionService.saveAndAnalyze(dto);
        }
    }
}
