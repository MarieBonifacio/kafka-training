package com.example.kafka;

import com.example.kafka.model.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class KafkaDeserializationResilienceTest {

    // Lancement d’un conteneur Kafka pour les tests
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );

    /**
     * Test original : erreur de type (age = string au lieu d’un int)
     */
    @Test
    void testJsonDeserializationError() {
        String topic = "test-json-error";
        String key = "person-error";

        // JSON invalide : champ 'age' au mauvais format
        String badJson = """
            { "name": "Hugo", "age": "trente", "address": { "street": "rue X", "city": "Lille", "zip": "59000" } }
        """;

        // === Config producteur ===
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, key, badJson));
            producer.flush();
        }

        // === Config consommateur ===
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "group-deserialization-error");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        assertFalse(records.isEmpty(), "Aucun message reçu");

                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                mapper.readValue(record.value(), Person.class);
                                fail("Le test aurait dû échouer à la désérialisation !");
                            } catch (Exception e) {
                                System.out.println("✅ Erreur de désérialisation capturée : " + e.getMessage());
                                assertTrue(e.getMessage().contains("Cannot deserialize"));
                            }
                        }
                    });
        }
    }

    /**
     * Cas 1 — champ "address" manquant
     */
    @Test
    void testDeserializationFailsOnMissingAddress() {
        String json = "{ \"name\": \"Hugo\", \"age\": 30 }"; // 'address' absent
        testInvalidDeserialization(json, "champ manquant : address");
    }

    /**
     * Cas 2 — address mal structurée (string au lieu d’un objet)
     */
    @Test
    void testDeserializationFailsOnAddressAsString() {
        String json = "{ \"name\": \"Hugo\", \"age\": 30, \"address\": \"invalide\" }";
        testInvalidDeserialization(json, "structure invalide : address=String");
    }

    /**
     * Méthode factorisée pour tester un JSON cassé
     */
    private void testInvalidDeserialization(String json, String label) {
        String topic = "test-json-" + label.replace(" ", "-");
        String key = "key-" + label.replace(" ", "-");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "group-" + label.replace(" ", "-"));
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (
            Producer<String, String> producer = new KafkaProducer<>(producerProps);
            Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)
        ) {
            producer.send(new ProducerRecord<>(topic, key, json));
            producer.flush();

            consumer.subscribe(Collections.singletonList(topic));

            Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        var records = consumer.poll(Duration.ofMillis(500));
                        assertFalse(records.isEmpty(), "Aucun message reçu dans le topic : " + topic);

                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                mapper.readValue(record.value(), Person.class);
                                fail("❌ La désérialisation aurait dû échouer pour le test : " + label);
                            } catch (Exception e) {
                                System.out.println("✅ Erreur capturée [" + label + "] : " + e.getMessage());
                            }
                        }
                    });
        }
    }
}
