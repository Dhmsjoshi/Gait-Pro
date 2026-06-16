package com.gait.biomedicaltwin.config.mqtt;

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

    @Autowired // <-- Ab Constructor ki zaroorat nahi, Spring ise direct handle karega
    private ResourceLoader resourceLoader;

    //MQTT Client factory to setup SSL and Credentials configuration

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30); // ⏳ Timeout badha diya taaki hang na ho
        options.setKeepAliveInterval(60);
        //options.setMaxInflight(100);

        try {
            Resource resource = resourceLoader.getResource(trustStoreLocation);
            try (InputStream tsStream = resource.getInputStream()) {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(tsStream, trustStorePassword.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                // 🌟 TLSv1.2 se badal kar generic "TLS" kiya taaki standard handshake fail na ho
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                options.setSocketFactory(sslContext.getSocketFactory());

                // 🚨 LOCALHOST HOSTNAME VERIFICATION BYPASS (Most Important for local testing)
                options.setHttpsHostnameVerificationEnabled(false);

                log.info("🎯 MQTT TrustStore successfully loaded from classpath secure connection ke liye!");
            }
        } catch (Exception e) {
            log.error("❌ Failed to load MQTT TrustStore from classpath", e);
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    // For Incoming message -> Spring integration channel
    @Bean
    MessageChannel mqttInputChannel(){
        return new DirectChannel();
    }

    // Inbound Adapter: Listens Mosquitto topics and send data to channel
    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());

        // Yeh property background socket thread khud open karegi
        adapter.setAutoStartup(true);

        return adapter;
    }

//    @Bean
//    public org.springframework.boot.CommandLineRunner forceMqttConnection(MessageProducer inbound) {
//        return args -> {
//            if (inbound instanceof org.springframework.context.SmartLifecycle) {
//                log.info("🔌 Forcing MQTT Inbound Adapter to connect physically...");
//                ((org.springframework.context.SmartLifecycle) inbound).start();
//            }
//        };
//    }

    // Service Activator: Takes messages from channel and passes to Ingestion service
    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler(IngestionService ingestionService, ObjectMapper objectMapper) {
        return message -> {
            String payload = (String) message.getPayload();
            log.info("Received MQTT Raw Payload: {}", payload);
            try {
                // JSON string ko aapke RawSensorDto mein convert karna
                RawSensorDto dto = objectMapper.readValue(payload, RawSensorDto.class);

                // Aapka core architecture method trigger karna
                ingestionService.saveAndAnalyze(dto);
                log.info("MQTT Payload successfully processed and analyzed by IngestionService.");
            } catch (Exception e) {
                log.error("Error parsing or processing MQTT payload", e);
            }
        };
    }
//    @Bean
//    @ServiceActivator(inputChannel = "mqttInputChannel")
//    public MessageHandler handler(IngestionService ingestionService, ObjectMapper objectMapper) {
//        return message -> {
//            String payload = (String) message.getPayload();
//
//            // 🔥 YEH LOG HAR HAAL MEIN CHAMKNA CHAHIYE
//            log.info("🔥 BOOM! MQTT Se Data Aa Gaya Bhai: {}", payload);
//
//            try {
//                RawSensorDto dto = objectMapper.readValue(payload, RawSensorDto.class);
//                log.info("Parsed DTO Successfully: FootSide = {}, Temp = {}", dto.footSide(), dto.temperatureC());
//
//                // 🚫 Database abhi empty hai, toh isko 2 minute ke liye comment kar dete hain:
//                // ingestionService.saveAndAnalyze(dto);
//
//                log.info("✅ Core service ko bypass karke demo successfully chal gaya!");
//            } catch (Exception e) {
//                log.error("❌ JSON parsing mein dikkat aayi", e);
//            }
//        };
//    }


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
