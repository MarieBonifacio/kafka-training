package com.example.kafka.service;

import com.example.kafka.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.*;

import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class OrderProducerService {
    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public OrderProducerService(String bootstrapServers) {
        // Initialisation du producer kafka
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        this.producer = new KafkaProducer<>(props);
        this.objectMapper = new ObjectMapper();
    }

    public void sendOrder(String topic, Order order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, order.getId(), json);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Erreur lors de l'envoi de l'Order : {}", exception.getMessage());
                } else {
                    log.info("Order envoyé avec succès : {} à la partition {}, offset {}", 
                             order.getId(), metadata.partition(), metadata.offset());
                }
            });
            producer.flush(); // Assure que le message est envoyé immédiatement
        } catch (Exception e) {
            log.error("Erreur lors de la conversion de l'Order en JSON : {}", e.getMessage());
        }
    }

    public void close() {
        producer.close();
        log.info("Kafka producer fermé.");
    }
}
