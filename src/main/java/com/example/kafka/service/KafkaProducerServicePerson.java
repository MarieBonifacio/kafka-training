package com.example.kafka.service;

import com.example.kafka.model.Person;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaProducerServicePerson {
    private final String bootstrapServers;

    public KafkaProducerServicePerson(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void send(String topic, String key, Person person) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());

        try (Producer<String, Person> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, person));
            producer.flush();
        }
    }
}
