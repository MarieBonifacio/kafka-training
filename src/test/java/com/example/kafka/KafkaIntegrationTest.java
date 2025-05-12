package com.example.kafka;

import com.example.kafka.service.KafkaProducerService;
import com.example.kafka.service.KafkaConsumerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );

    @Test
    void testKafkaProducerAndConsumerServices() {
        String topic = "test-topic";
        String key = "key";
        String value = "value";

        System.out.println("🚀 Lancement du test Kafka avec services producteurs/consommateurs...");

        KafkaProducerService producer = new KafkaProducerService(kafka.getBootstrapServers());
        KafkaConsumerService consumer = new KafkaConsumerService(kafka.getBootstrapServers(), "test-group");

        System.out.println("🛠️  Producteur et consommateur instanciés");

        producer.send(topic, key, value);
        System.out.println("📤 Message envoyé sur le topic : " + topic);

        System.out.println("⏳ Attente de réception du message...");
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<ConsumerRecord<String, String>> messages =
                            consumer.pollMessages(topic, Duration.ofMillis(500));

                    System.out.println("✅ Messages reçus : " + messages.size());
                    assertFalse(messages.isEmpty(), "Aucun message reçu !");
                    assertEquals(key, messages.get(0).key(), "Clé incorrecte");
                    assertEquals(value, messages.get(0).value(), "Valeur incorrecte");
                });

        consumer.close();
        System.out.println("🧹 Consommateur fermé proprement");
        System.out.println("🎉 Test terminé avec succès !");
    }

    @Test
    void testKafkaBatchSending() {
        String topic = "test-batch";

        KafkaProducerService producer = new KafkaProducerService(kafka.getBootstrapServers());
        KafkaConsumerService consumer = new KafkaConsumerService(kafka.getBootstrapServers(), "test-group-batch");

        List<Map.Entry<String, String>> messages = List.of(
            Map.entry("k1", "v1"),
            Map.entry("k2", "v2"),
            Map.entry("k3", "v3")
        );

        System.out.println("📤 Envoi de 3 messages dans le topic");
        producer.sendBatch(topic, messages);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<ConsumerRecord<String, String>> received =
                        consumer.pollMessages(topic, Duration.ofMillis(500));

                    assertEquals(3, received.size(), "Le nombre de messages reçus est incorrect");

                    for (int i = 0; i < messages.size(); i++) {
                        assertEquals(messages.get(i).getKey(), received.get(i).key());
                        assertEquals(messages.get(i).getValue(), received.get(i).value());
                    }
                });

        consumer.close();
    }
}
