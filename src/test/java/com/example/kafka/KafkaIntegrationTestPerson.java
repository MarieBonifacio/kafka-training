package com.example.kafka;

import com.example.kafka.model.Person;
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

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class KafkaIntegrationTestPerson {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );

    @Test
    void testKafkaProducerWithPersonObject() {
        String topic = "test-person";
        String key = "id1";
        Person person = new Person("Alice", 30);

        KafkaProducerServicePerson producer = new KafkaProducerServicePerson(kafka.getBootstrapServers());
        producer.send(topic, key, person);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-person");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    assertFalse(records.isEmpty(), "Aucun message JSON reçu");

                    for (ConsumerRecord<String, String> record : records) {
                        assertEquals(key, record.key());
                        ObjectMapper mapper = new ObjectMapper();
                        Person received = mapper.readValue(record.value(), Person.class);
                        assertEquals(person.name, received.name);
                        assertEquals(person.age, received.age);
                    }
                });

        consumer.close();
    }
}
