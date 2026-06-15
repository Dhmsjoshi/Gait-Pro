package com.gait.biomedicaltwin.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.cleanup.SessionCleanupService;
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

    private static final String BROKER_URL = "ssl://127.0.0.1:8883";
    private static final String TOPIC = "gait/sensor/data";
    private static final String USER_ID = "d3b07384-d113-4956-bc7e-3617ec23f46f";
    private static final String MQTT_USERNAME = "my_java_user";

    // 🔥 JVM LEVEL PAR ENDPOINT/HOSTNAME CHECK KO DISABLE KARNA
    @BeforeEach
    void setupSslProperties() {
        // Yeh property Java ko bolti hai ki IP/Hostname match karne ka nakhra band kare
        System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", "true");
        // Standard JSSE (Java Secure Socket Extension) ke endpoint check ko khali (empty) karo
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

   // @Test
    void runOneKilometerGaitSimulation() throws Exception {
        log.info("🚀 Starting Automated 1KM Gait Simulation Process...");

        String uniqueClientId = "gait_simulator_dev_" + UUID.randomUUID().toString().substring(0, 5);
        MqttClient client = new MqttClient(BROKER_URL, uniqueClientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(MQTT_USERNAME);

        String mqttPass = System.getProperty("MQTT_PASSWORD", "YOUR_MOSQUITTO_PASSWORD_HERE");
        options.setPassword(mqttPassword.toCharArray());
        options.setCleanSession(true);

        // ====================================================================
        // 🔥 EXTENDED TRUST MANAGER TO BYPASS HOSTNAME / IP SAN IDENTITY CHECK
        // ====================================================================
        try {
            var trustStoreStream = getClass().getClassLoader().getResourceAsStream("truststore.jks");
            if (trustStoreStream == null) {
                throw new RuntimeException("truststore.jks missing from classpath!");
            }

            java.security.KeyStore trustStore = java.security.KeyStore.getInstance("JKS");
            trustStore.load(trustStoreStream, "password".toCharArray());

            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Standard managers ko pakdo
            javax.net.ssl.TrustManager[] standardManagers = tmf.getTrustManagers();
            javax.net.ssl.TrustManager[] customManagers = new javax.net.ssl.TrustManager[standardManagers.length];

            // Unhe Extended Trust Manager mein wrap karo jo Identity check (SAN check) ko bypass karega
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
                            // 🔥 Khali chhod diya taaki Socket Identification check bypass ho jaye
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                            tm.checkClientTrusted(chain, authType);
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                            // 🔥 Khali chhod diya taaki Engine Identification check bypass ho jaye
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
        // ====================================================================

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

            String footSide = (i % 2 == 0) ? "LEFT" : "RIGHT";

            RawSensorDto dto = new RawSensorDto(
                    UUID.fromString(USER_ID),
                    footSide,
                    1.2 + (Math.random() * 0.4),
                    2.0 + (Math.random() * 2.0),
                    Math.round(pitch * 100.0) / 100.0,
                    36.5 + (Math.random() * 0.5),
                    45.0 + (Math.random() * 2.0),
                    420L,
                    850L
            );

            String jsonPayload = objectMapper.writeValueAsString(dto);
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);

            client.publish(TOPIC, message);

            if (i % 100 == 0) {
                log.info("📦 Progress: Sent {}/{} telemetry packets to Mosquitto...", i, totalPackets);
            }

            Thread.sleep(50);
        }

        client.disconnect();
        client.close();
        log.info("✅ Simulation complete! 2000 packets successfully ingested.");
    }


    //@Test
    void testSessionCleanupAndCsvGeneration() {
        log.info("🧹 Starting Session Cleanup Process for Session: {}", "98f77260-9068-4a20-8758-abb08e720938");

        UUID sessionId = UUID.fromString("98f77260-9068-4a20-8758-abb08e720938");

        try {
            // Service method ko call kiya
            sessionCleanupService.archiveAndCleanupSession(sessionId);

            log.info("🎉 SUCCESS: CSV generated and raw GaitDataPoints truncated!");
            log.info("📂 Check your User Home folder: /GaitDataExports/ for the file.");
        } catch (Exception e) {
            log.error("❌ Cleanup Failed: {}", e.getMessage());
            throw e;
        }
    }
}
