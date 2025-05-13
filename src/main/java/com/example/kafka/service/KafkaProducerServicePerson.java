package com.example.kafka.service;

import com.example.kafka.model.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KafkaProducerServicePerson {
    private final String bootstrapServers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaProducerServicePerson(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void send(String topic, String key, Person person) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            String json = objectMapper.writeValueAsString(person);
            producer.send(new ProducerRecord<>(topic, key, json));
            producer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBatch(String topic, List<Map.Entry<String, Person>> messages) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (Map.Entry<String, Person> entry : messages) {
                String json = objectMapper.writeValueAsString(entry.getValue());
                producer.send(new ProducerRecord<>(topic, entry.getKey(), json));
            }
            producer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
