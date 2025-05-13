package com.example.kafka;

import com.example.kafka.model.Person;
import com.example.kafka.model.Address;
import com.example.kafka.service.KafkaProducerServicePerson;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.awaitility.Awaitility;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class KafkaIntegrationPersonTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );

    @Test
    void testKafkaProducerWithPersonAndAddress() {
        System.out.println("🚀 Démarrage du test Kafka avec plusieurs personnes imbriquées...");

        String topic = "test-btch-JSON";
        System.out.println("🛠️ Création du producteur Kafka...");
        KafkaProducerServicePerson producer = new KafkaProducerServicePerson(kafka.getBootstrapServers());

        System.out.println("📋 Préparation des données à envoyer...");
        List<Map.Entry<String, Person>> persons = List.of(
                Map.entry("key1", new Person("John Doe", 30, new Address("123 Main St", "Springfield", "IL", "62701"))),
                Map.entry("key2", new Person("Jane Smith", 25, new Address("456 Elm St", "Springfield", "IL", "62702"))),
                Map.entry("key3", new Person("Alice Johnson", 28, new Address("789 Oak St", "Springfield", "IL", "62703")))
        );

        System.out.println("📤 Envoi des messages au topic Kafka : " + topic);
        producer.sendBatch(topic, persons);

        System.out.println("⚙️ Configuration du consommateur Kafka...");
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        System.out.println("📡 Consommateur Kafka abonné au topic : " + topic);

        System.out.println("⏳ Attente des messages dans le topic...");
        Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            assertEquals(3, records.count(), "❌ Nombre de messages incorrect !");
            System.out.println("✅ Messages reçus : " + records.count());

            ObjectMapper mapper = new ObjectMapper();
            int index = 0;
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("📥 Traitement du message reçu avec clé : " + record.key());
                Person expected = persons.get(index).getValue();
                Person received = mapper.readValue(record.value(), Person.class);

                System.out.println("🔍 Validation des données du message...");
                assertEquals(expected.getName(), received.getName(), "❌ Le nom ne correspond pas !");
                assertEquals(expected.getAddress().getCity(), received.getAddress().getCity(), "❌ La ville ne correspond pas !");
                assertEquals(expected.getAddress().getStreet(), received.getAddress().getStreet(), "❌ La rue ne correspond pas !");
                assertEquals(expected.getAddress().getState(), received.getAddress().getState(), "❌ L'état ne correspond pas !");
                assertEquals(expected.getAddress().getZip(), received.getAddress().getZip(), "❌ Le code postal ne correspond pas !");
                System.out.println("🎉 Validation réussie pour le message avec clé : " + record.key());
                index++;
            }
        });

        consumer.close();
        System.out.println("🏁 Fin du test Kafka !");
    }
}