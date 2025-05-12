---
title: Mise en place de Kafka avec Testcontainers
---

# 🛠️ Setup Kafka local avec Testcontainers

Dans ce chapitre, nous allons créer notre premier test Kafka **sans aucun cluster réel**, uniquement grâce à **Testcontainers**.

---

## 🔍 Pourquoi Testcontainers ?

> Testcontainers permet de lancer des conteneurs Docker (comme Kafka) à la volée pendant les tests JUnit.

✔️ Tu évites les dépendances réseau  
✔️ Tu t’assures que chaque test est isolé et jetable  
✔️ Tu peux intégrer Kafka facilement dans n’importe quel pipeline CI/CD

---

## 📦 Dépendances Maven à ajouter

Dans ton `pom.xml` :

```xml
<dependencies>
  <!-- Kafka client -->
  <dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.4.0</version>
  </dependency>

  <!-- JUnit 5 -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.9.2</version>
    <scope>test</scope>
  </dependency>

  <!-- Testcontainers Kafka -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
  </dependency>

  <!-- Awaitility pour attendre proprement les messages -->
  <dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```
---

## 🐳 Lancer un conteneur Kafka

Voici comment démarrer un conteneur Kafka avec Testcontainers : 
Dans ton `kafkaIntegrationTest.java` :

```java
// Définit le package Java dans lequel se trouve la classe. Cela permet d'organiser les classes de manière logique.
package com.example.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
// Annotation @test pour indiquer que c'est un test d'intégration 
import org.junit.jupiter.api.Test;
// Classe utilitaire fournie par Testcontaiers pour lancer Kafka avec avec une conf prête à l'emploi
import org.testcontainers.containers.KafkaContainer;
//  Intégration entre Testcontainers et JUnit 5 : @Testcontainers permet de gérer automatiquement le cycle de vie des conteneurs marqués @Container.
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Permet de parser le nom d'une image docker et de déclarer une compatibilité entre images
import org.testcontainers.utility.DockerImageName;
import org.awaitility.Awaitility;

// Permets d'utiliser assertTrue pour valider qu'un test passe (vérifie ici que le conteneur kafka ets bien lancé)
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Démarre un test avec JUnit 5
// void : indique que la méthode ne retourne rien
void testKafkaContainerStarts() {
        // Démarre le conteneur Kafka
        kafka.start();
        // Vérifie que le conteneur Kafka est bien démarré
        // assertTrue : méthode de JUnit qui vérifie que la condition est vraie
        assertTrue(kafka.isRunning(), "Kafka n'est pas démarré !");
        // Affiche l'adresse du serveur Kafka dans la console 
        // System.out.println : méthode Java pour afficher un message dans la console
        System.out.println("Kafka bootstrap server: " + kafka.getBootstrapServers());
}
```

---
## 🔬 Structure de départ du test
Voici un test fonctionnel qui démarre un Kafka avec Testcontainers, envoie un message dans un topic et vérifie sa réception :

```java
@Testcontainers
public class KafkaIntegrationTest {

  @Container
  static KafkaContainer kafka = new KafkaContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                     .asCompatibleSubstituteFor("apache/kafka")
  );

  @Test
  void testKafkaProducerConsumer() {
    String topic = "test-topic";

    // Configuration Producteur
    Properties producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

    Producer<String, String> producer = new KafkaProducer<>(producerProps);
    producer.send(new ProducerRecord<>(topic, "key", "value"));
    producer.flush();
    producer.close();

    // Configuration Consommateur
    Properties consumerProps = new Properties();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    consumer.subscribe(Collections.singletonList(topic));

    Awaitility.await()
              .atMost(10, TimeUnit.SECONDS)
              .pollInterval(Duration.ofMillis(500))
              .untilAsserted(() -> {
                  ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                  assertFalse(records.isEmpty());
                  for (ConsumerRecord<String, String> record : records) {
                      assertEquals("key", record.key());
                      assertEquals("value", record.value());
                  }
              });

    consumer.close();
  }
}
```
---
## 📌 Ce que tu as appris ici

✔️ Démarrer un Kafka local à la volée   
✔️ Envoyer et recevoir un message      
✔️ Tester l'ensemble sans aucune infrastructure externe 








