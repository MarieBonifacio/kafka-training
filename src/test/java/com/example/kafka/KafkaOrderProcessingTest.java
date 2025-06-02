package com.example.kafka;

import com.example.kafka.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaOrderProcessingTest {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderProcessingTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private KafkaContainer kafka;
    private String bootstrapServers;

    @BeforeEach
    void startKafka() {
        kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                           .asCompatibleSubstituteFor("apache/kafka")
        );
        kafka.start();
        bootstrapServers = kafka.getBootstrapServers();
    }

    @AfterEach
    void stopKafka() {
        kafka.stop();
    }

    @Test
    void testOrderProcessingFlow() throws Exception {
        String inputTopic = "order-input";
        String outputTopic = "order-processed";

        // Étape 1 : envoi d’un Order
        Order order = new Order("ORDER-001", 150.0, "NEW");
        String orderJson = objectMapper.writeValueAsString(order);
        log.info("🔄 Envoi initial de l'Order : {}", orderJson);

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(prodProps)) {
            producer.send(new ProducerRecord<>(inputTopic, order.id, orderJson)).get();
            log.info("✅ Order envoyé dans le topic '{}'", inputTopic);
        }

        // Étape 2 : Consommation + traitement + republication
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processing-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (
            Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
            Producer<String, String> outputProducer = new KafkaProducer<>(prodProps)
        ) {
            consumer.subscribe(List.of(inputTopic));

            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertFalse(records.isEmpty(), "Aucun message reçu dans le topic d'entrée");

                for (ConsumerRecord<String, String> record : records) {
                    Order received = objectMapper.readValue(record.value(), Order.class);
                    log.info("📥 Message reçu : {}", received);

                    received.status = received.amount > 100 ? "ALERT" : "OK";

                    String modifiedOrderJson = objectMapper.writeValueAsString(received);
                    outputProducer.send(new ProducerRecord<>(outputTopic, received.id, modifiedOrderJson)).get();
                    log.info("✅ Order modifié envoyé dans le topic '{}'", outputTopic);
                }
            });
        }

        // Étape 3 : Vérification finale
        try (Consumer<String, String> finalConsumer = new KafkaConsumer<>(consumerProps)) {
            finalConsumer.subscribe(List.of(outputTopic));

            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = finalConsumer.poll(Duration.ofMillis(500));
                assertFalse(records.isEmpty(), "Aucun message reçu dans le topic de sortie");

                for (ConsumerRecord<String, String> record : records) {
                    Order received = objectMapper.readValue(record.value(), Order.class);
                    log.info("🔎 Message final reçu : {}", received);
                    assertTrue(
                        received.status.equals("ALERT") || received.status.equals("OK"),
                        "Le statut de l'Order doit être ALERT ou OK"
                    );
                }
            });
        }
    }
}
