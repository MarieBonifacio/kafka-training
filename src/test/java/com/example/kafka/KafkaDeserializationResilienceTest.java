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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class KafkaDeserializationResilienceTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(KafkaDeserializationResilienceTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );

    @Test
    void testJsonDeserializationError() throws InterruptedException {
        String label = "erreur-deserialization";
        String safeLabel = Optional.ofNullable(label).orElse("").replaceAll("[^a-zA-Z0-9._-]", "-");
        String json = """
            { "name": "Hugo", "age": "trente", "address": { "street": "rue X", "city": "Lille", "zip": "59000" } }
        """;
        String topic = "test-json-person-error" + safeLabel;
        String key = "key-person-error" + safeLabel;
        log.info("⏩ Envoi vers Kafka : topic={}, key={}, json={}", topic, key, json);

        sendInvalidMessage(topic, key, json);

        try (Consumer<String, String> consumer = createConsumer("group-deserialization-error")) {
            consumer.subscribe(Collections.singletonList(topic));

            consumer.poll(Duration.ZERO); 

            Thread.sleep(1000);
            
            Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        assertFalse(records.isEmpty(), "Aucun message reçu");

                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                objectMapper.readValue(record.value(), Person.class);
                                fail("La désérialisation aurait dû échouer !");
                            } catch (Exception e) {
                                log.warn("✅ Erreur de désérialisation capturée (key={}) : {}", record.key(), e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            fail("Erreur lors de la création du consommateur : " + e.getMessage());
        }
    }

    @Test
    void testDeserializationFailsOnMissingAddress() {
        String json = "{ \"name\": \"Hugo\", \"age\": 30 }";
        testInvalidDeserialization(json, "champ-manquant-adresse");
    }

    @Test
    void testDeserializationFailsOnAddressAsString() {
        String json = "{ \"name\": \"Hugo\", \"age\": 30, \"address\": \"invalide\" }";
        testInvalidDeserialization(json, "structure-invalide-adresse-string");
    }

    private void testInvalidDeserialization(String json, String label) {
        String topic = "test-json-" + label;
        String key = "key-json-" + label;
        String groupId = "group-" + label + "-" + UUID.randomUUID();
    
        sendInvalidMessage(topic, key, json);
    
        try (Consumer<String, String> consumer = createConsumer(groupId)) {
            consumer.subscribe(Collections.singletonList(topic));
            consumer.poll(Duration.ZERO);
            Thread.sleep(1000);
            log.info("🟡 Début de la lecture du topic {}", topic);
    
            boolean[] errorCaptured = {false};
    
            Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    log.info("Messages trouvés dans le topic {} : {}", topic, records.count());
    
                    if (!records.isEmpty()) {
                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                Person person = mapper.readValue(record.value(), Person.class);
    
                                // 🔍 Si le champ est null, on force l’erreur
                                if (person.getAddress() == null) {
                                    throw new IllegalArgumentException("Champ 'address' manquant !");
                                }
    
                                // Sinon, on force un échec : le test ne devait pas réussir
                                fail("❌ La désérialisation aurait dû échouer pour le test : " + label);
                            } catch (Exception e) {
                                log.warn("✅ Erreur capturée [{}] sur key={} : {}", label, record.key(), e.getMessage());
                                errorCaptured[0] = true;
                            }
                        }
                    }
    
                    return errorCaptured[0];
                });
    
        } catch (Exception e) {
            fail("❌ Exception inattendue dans le test : " + e.getMessage());
        }
    }
    
        
    private void sendInvalidMessage(String topic, String key, String json) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

       try (Producer<String, String> producer = new KafkaProducer<>(props)) {
        log.info("📝 Envoi d'un message invalide sur le topic : {} avec key={}", topic, key);
        producer.send(new ProducerRecord<>(topic, key, json), (metadata, exception) -> {
            if (exception != null) {
                log.error("❌ Erreur lors de l'envoi du message : {}", exception.getMessage());
            } else {
                log.info("✅ Message envoyé sur le topic={} avec offset={} - partition={}", metadata.topic(), metadata.offset(), metadata.partition());
            }
        }).get(); // rend l'appel synchrone
        producer.flush();
        Thread.sleep(1000);
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'envoi du message : {}", e.getMessage());
       }
    }

    private Consumer<String, String> createConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        return new KafkaConsumer<>(props);
    }
    
}

