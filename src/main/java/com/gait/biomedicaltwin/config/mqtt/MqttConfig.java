package com.gait.biomedicaltwin.config.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.ingestion.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Configuration
@Slf4j
public class MqttConfig {
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.topic.sensor}")
    private String topic;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.truststore.location}")
    private String trustStoreLocation;

    @Value("${mqtt.truststore.password}")
    private String trustStorePassword;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired // ✅ FIX: Autowired active kiya taaki NullPointerException na aaye
    private MqttPayloadBridge mqttPayloadBridge;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);

        try {
            Resource resource = resourceLoader.getResource(trustStoreLocation);
            try (InputStream tsStream = resource.getInputStream()) {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(tsStream, trustStorePassword.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                options.setSocketFactory(sslContext.getSocketFactory());
                options.setHttpsHostnameVerificationEnabled(false);

                log.info("🎯 MQTT TrustStore successfully loaded from classpath secure connection ke liye!");
            }
        } catch (Exception e) {
            log.error("❌ Failed to load MQTT TrustStore from classpath", e);
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel(){
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        adapter.setAutoStartup(true);

        return adapter;
    }

    // ✅ UPDATED HANDLER: Parses Kepware's array payload structure directly and safely
    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler(IngestionService ingestionService, ObjectMapper objectMapper) {
        return message -> {
            String payload = (String) message.getPayload();
            log.info("🔥 BOOM! MQTT Se Data Aa Gaya Bhai: {}", payload);

            try {
                // Kepware ke nested root structure ko JSON tree format mein read karo
                JsonNode rootNode = objectMapper.readTree(payload);
                JsonNode valuesNode = rootNode.get("values");

                if (valuesNode != null && valuesNode.isArray()) {
                    int pitch = 0, roll = 0, impact = 0, temp = 0, humid = 0, footSide = 0;

                    // Iterate through Kepware payload array elements dynamically
                    for (JsonNode node : valuesNode) {
                        String id = node.get("id").asText();
                        int value = node.get("v").asInt();

                        if (id.contains("Pitch Angle Y")) pitch = value;
                        else if (id.contains("Foot Roll Angle X")) roll = value;
                        else if (id.contains("Impact Shockwave Z")) impact = value;
                        else if (id.contains("Temperature C")) temp = value;
                        else if (id.contains("Humidity Rh")) humid = value;
                        else if (id.contains("Foot Side")) footSide = value;
                    }

                    log.info("🎯 Parsed Kepware Metrics -> Pitch: {}, Roll: {}, Impact: {}, Temp: {}°C, Humidity: {}, FootSide: {}",
                            pitch, roll, impact, temp / 10.0, humid, (footSide == 1 ? "RIGHT" : "LEFT"));

                    // Yahan par RawSensorDto ko data feed karo aur ingestion mechanism ko pass karo
                    // RawSensorDto dto = new RawSensorDto(pitch, roll, impact, temp, humid, footSide);
                    // ingestionService.saveAndAnalyze(dto);
                }

                // Bridge layer processing triggered safely
                if (mqttPayloadBridge != null) {
                    mqttPayloadBridge.bridgeIncomingPayload(payload);
                }

            } catch (Exception e) {
                log.error("❌ Kepware IoT data structured parsing layer failed", e);
            }
        };
    }

    // --- LIVE NETWORK LOGGER BEANS FOR TROUBLESHOOTING ---
    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.integration.mqtt.event.MqttConnectionFailedEvent> connectionFailedListener() {
        return event -> {
            log.error("❌ [MQTT LIVE ERROR] CONNECTION FAILED! Wajah: {}", event.getCause().getMessage());
            if (event.getCause().getCause() != null) {
                log.error("🔍 Detailed Root Cause: {}", event.getCause().getCause().getMessage());
            }
        };
    }

    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.integration.mqtt.event.MqttSubscribedEvent> subscribedListener() {
        return event -> {
            log.info("🚀 [MQTT LIVE SUCCESS] Java successfully connected and subscribed to broker topic!");
        };
    }
}
