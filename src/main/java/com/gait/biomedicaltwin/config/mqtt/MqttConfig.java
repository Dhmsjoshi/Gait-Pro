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

    @Autowired
    private MqttPayloadBridge mqttPayloadBridge;

    /**
     * FLOW STEP 1: MQTT Client Factory & SSL Connection Setup
     * Application start hote hi ye broker ke sath handshake aur secure connection parameters initialize karta hai.
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true); // Network disconnect hone par auto-reconnect trigger karega
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

                log.info("🎯 [MQTT-INIT] SSL TrustStore successfully loaded from classpath.");
            }
        } catch (Exception e) {
            log.error("❌ [MQTT-INIT-ERROR] Failed to load MQTT TrustStore", e);
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * FLOW STEP 2: Internal Message Pipe (Spring Integration Channel)
     * Inbound adapter se data receive hone ke baad isi channel pipe ke throug handler tak travel karta hai.
     */
    @Bean
    public MessageChannel mqttInputChannel(){
        return new DirectChannel();
    }

    /**
     * FLOW STEP 3: Inbound Message Driver (The Listener)
     * Ye active listener broker ke topic par continuously queue monitor karta hai aur messages ko channel me push karta hai.
     */
    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1); // At least once delivery compliance
        adapter.setOutputChannel(mqttInputChannel()); // Connect adapter to the message channel pipe
        adapter.setAutoStartup(true);

        return adapter;
    }

    /**
     * FLOW STEP 4: Central Entry Gate (Refactored Handler)
     * Duplicate logic aur print loop ko yahan se hata diya gaya hai.
     * Iska kaam ab sirf channel se message pakadna aur process karne ke liye PayloadBridge ke hawale karna hai.
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {
            String payload = (String) message.getPayload();
            log.info("🔥 [MQTT-RECEIVER] New Raw Packet Arrived. Size: {} chars", payload.length());

            try {
                // Gateway Router: Direct routing central router class tak bina text parse latency ke
                if (mqttPayloadBridge != null) {
                    mqttPayloadBridge.bridgeIncomingPayload(payload);
                } else {
                    log.error("❌ [HANDLER-CRITICAL] MqttPayloadBridge reference injection is NULL!");
                }
            } catch (Exception e) {
                // Network pipeline safe gate: Yahan exception handling sirf wrapper crash protection ke liye hai
                log.error("❌ [HANDLER-ERROR] Wrapper tracking failure inside channel receiver gateway", e);
            }
        };
    }

    // --- NETWORK MONITORS ---
    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.integration.mqtt.event.MqttConnectionFailedEvent> connectionFailedListener() {
        return event -> log.error("❌ [MQTT-LIVE-ERROR] CONNECTION FAILED! Cause: {}", event.getCause().getMessage());
    }

    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.integration.mqtt.event.MqttSubscribedEvent> subscribedListener() {
        return event -> log.info("🚀 [MQTT-LIVE-SUCCESS] Connected and Subscribed cleanly to the topic cluster!");
    }
}
