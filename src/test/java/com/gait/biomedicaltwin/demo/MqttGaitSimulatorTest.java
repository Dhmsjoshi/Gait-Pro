package com.gait.biomedicaltwin.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.cleanup.SessionCleanupService;
import com.gait.biomedicaltwin.services.ingestion.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(properties = {
        // Apne local database ka naam aur credentials yahan confirm kar lena
        "SPRING_DATA_URL=jdbc:mysql://localhost:3306/humangaitdb",
        "SPRING_DATA_USERNAME=root",
        "SPRING_DATA_PASSWORD=password",
        "MQTT_PASSWORD=password123" // properties wala ${MQTT_PASSWORD}
})
@Slf4j
public class MqttGaitSimulatorTest {
    @Value("${mqtt.password}")
    private String mqttPassword;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionCleanupService sessionCleanupService;

    @Autowired
    private IngestionService ingestionService;

    private static final String BROKER_URL = "ssl://127.0.0.1:8883";
    private static final String TOPIC = "gait/sensor/data";
    private static final String USER_ID = "d3b07384-d113-4956-bc7e-3617ec23f46f";
    private static final String MQTT_USERNAME = "my_java_user";

    @BeforeEach
    void setupSslProperties() {
        System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", "true");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    //@Test
    void runOneKilometerGaitSimulation() throws Exception {
        log.info("🚀 Starting Automated 1KM Gait Simulation Process with Dynamic Metric Injectors...");

        String uniqueClientId = "gait_simulator_dev_" + UUID.randomUUID().toString().substring(0, 5);
        MqttClient client = new MqttClient(BROKER_URL, uniqueClientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(MQTT_USERNAME);
        options.setPassword(mqttPassword.toCharArray());
        options.setCleanSession(true);
        options.setMaxInflight(2000); // Increased payload limit configuration

        try {
            var trustStoreStream = getClass().getClassLoader().getResourceAsStream("truststore.jks");
            if (trustStoreStream == null) {
                throw new RuntimeException("truststore.jks missing from classpath!");
            }

            java.security.KeyStore trustStore = java.security.KeyStore.getInstance("JKS");
            trustStore.load(trustStoreStream, "password".toCharArray());

            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            javax.net.ssl.TrustManager[] standardManagers = tmf.getTrustManagers();
            javax.net.ssl.TrustManager[] customManagers = new javax.net.ssl.TrustManager[standardManagers.length];

            for (int i = 0; i < standardManagers.length; i++) {
                if (standardManagers[i] instanceof javax.net.ssl.X509TrustManager) {
                    final javax.net.ssl.X509TrustManager tm = (javax.net.ssl.X509TrustManager) standardManagers[i];

                    customManagers[i] = new javax.net.ssl.X509ExtendedTrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket) throws java.security.cert.CertificateException {
                            tm.checkClientTrusted(chain, authType);
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket) throws java.security.cert.CertificateException {
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                            tm.checkClientTrusted(chain, authType);
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                            tm.checkClientTrusted(chain, authType);
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                            tm.checkServerTrusted(chain, authType);
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return tm.getAcceptedIssuers();
                        }
                    };
                } else {
                    customManagers[i] = standardManagers[i];
                }
            }

            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, customManagers, new java.security.SecureRandom());
            options.setSocketFactory(sc.getSocketFactory());

        } catch (Exception e) {
            log.error("❌ SSL Ingestion Setup Failed: {}", e.getMessage());
            throw e;
        }

        client.connect(options);
        log.info("🔌 Connected successfully to Secured Mosquitto Broker!");

        int totalPackets = 2000;
        double pitch = 15.0;
        boolean decreasing = true;

        for (int i = 1; i <= totalPackets; i++) {
            if (decreasing) {
                pitch -= 1.5;
                if (pitch <= -15.0) decreasing = false;
            } else {
                pitch += 1.5;
                if (pitch >= 15.0) decreasing = true;
            }

            // Alternating pattern safely handled
            String footSide = (i % 2 == 0) ? "LEFT" : "RIGHT";

            // Dynamic Threshold Configurations to validate real calculations
            double dynamicTemperature;
            long dynamicStepIntervalMs;
            long dynamicStancePhaseDurationMs;

            // 🔥 FIX 1: Fatigue Index Trigger Layer (Injecting fatigue footprints into 25% of data chunks)
            if (i > 1500) {
                // High temperature (> 38.5) and slower step interval causing low cadence (< 70)
                dynamicTemperature = 39.0 + (Math.random() * 0.8); // 39.0 to 39.8
                dynamicStepIntervalMs = 950L + (long)(Math.random() * 100); // Cadence will be around ~60 to 63
            } else {
                // Normal Baseline Dataset
                dynamicTemperature = 36.5 + (Math.random() * 0.5);
                dynamicStepIntervalMs = 500L + (long)(Math.random() * 100); // Cadence around ~100 to 120
            }

            // 🔥 FIX 2: Symmetry Index Variant Engine
            // Introducing biological variance to calculate asymmetric differences between feet durations
            if ("LEFT".equals(footSide)) {
                dynamicStancePhaseDurationMs = 410L + (long)(Math.random() * 20);
            } else {
                dynamicStancePhaseDurationMs = 450L + (long)(Math.random() * 20); // Intentionally offsetted to generate real indices
            }

            RawSensorDto dto = new RawSensorDto(
                    UUID.fromString(USER_ID),
                    footSide,
                    1.2 + (Math.random() * 0.4),
                    2.0 + (Math.random() * 2.0),
                    Math.round(pitch * 100.0) / 100.0,
                    Math.round(dynamicTemperature * 100.0) / 100.0,
                    45.0 + (Math.random() * 2.0),
                    dynamicStancePhaseDurationMs,
                    dynamicStepIntervalMs
            );

            String jsonPayload = objectMapper.writeValueAsString(dto);
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);

            client.publish(TOPIC, message);

            if (i % 100 == 0) {
                log.info("📦 Progress: Sent {}/{} telemetry packets to Mosquitto...", i, totalPackets);
            }

            // Sleep timing to allow standard transactional execution window without overlap conflicts
            Thread.sleep(35);
        }

        client.disconnect();
        client.close();
        log.info("✅ Simulation complete! 2000 dynamically structured packets successfully ingested.");

        log.info("🔌 Ingestion finished. Forcing session termination to trigger post-processing & snapshots...");
        try {
            ingestionService.forceCloseSessionForTest(USER_ID);
        } catch (Exception e) {
            log.warn("⚠️ Force close call failed: {}", e.getMessage());
        }

        // Expanded sleep cycle so async pool completes state calculations safely
        log.info("🛌 Waiting 15 seconds for Async background processing thread to finalize calculations...");
        Thread.sleep(15000);
        log.info("🎯 Execution done! Check your 'gait_snapshots' table now!");
    }

    //@Test
    void testSessionCleanupAndCsvGeneration() {
        String currentSessionId = "52f48395-4bfa-46d2-b068-989439f12d6c";

        log.info("🧹 Starting Session Cleanup Process for Session: {}", currentSessionId);
        UUID sessionId = UUID.fromString(currentSessionId);

        try {
            sessionCleanupService.archiveAndCleanupSession(sessionId);
            log.info("🎉 SUCCESS: CSV generated and raw GaitDataPoints truncated!");
            log.info("📂 Check your User Home folder: /GaitDataExports/ for the file.");
        } catch (Exception e) {
            log.error("❌ Cleanup Failed: {}", e.getMessage());
            throw e;
        }
    }
}
