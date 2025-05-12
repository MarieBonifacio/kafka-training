---
id: 04-envoi-objets-metier-JSON
title: Envoi d'objets métier en JSON
description: Ce document explique comment envoyer des objets métier en JSON en utilisant Kafka.
---

## 🎯 Objectif
Passer de simples chaînes de caractères (String) à des objets Java (POJO) envoyés et reçus via Kafka, en les sérialisant/désérialisant automatiquement en JSON.

## Ce qu’on va couvrir :

✅ Créer un objet Java (`Person`)

✅ L’envoyer via Kafka après sérialisation JSON

✅ Le recevoir et le désérialiser automatiquement grâce à Jackson


---


# 🔄 Sérialiser des objets Java avec Jackson dans Kafka

---

## 🧠 Pourquoi Jackson ?

**Jackson** est une bibliothèque Java très populaire pour transformer des objets Java en JSON (sérialisation), et inversement (désérialisation).

Kafka ne comprend pas les objets Java — il envoie des **tableaux d'octets** ou du texte.  
Pour transmettre un objet métier comme `Person`, nous devons donc le **convertir en JSON**, puis le retransformer à la réception.

---

## 🧪 Exemple concret : l'objet `Person`

Voici une classe simple représentant un objet métier :

📄 Fichier:

```bash
src/main/java/com/example/kafka/model/Person.java
```

```java
package com.example.kafka;

public class Person {
    public String name;
    public int age;

    // Obligatoire pour Jackson (constructeur vide)
    public Person() {}

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + "}";
    }
}
```

> 📌 Jackson a besoin d’un constructeur vide pour pouvoir instancier les objets lors de la désérialisation.




### Résumé des fichiers à ce stade :
```bash
src/
└── main/
    └── java/
        └── com/
            └── example/
                └── kafka/
                    ├── service/
                    │   ├── KafkaProducerService.java
                    │   └── KafkaConsumerService.java
                    ├── model/
                    │   └── Person.java
                    └── KafkaIntegrationTest.java  ← (ou dans test/)
```

---

 ## 📦 Ajouter Jackson et les sérializers Kafka

 Dans ton `pom.xml`, ajoute :

```xml
 <!-- Jackson core -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.15.2</version>
</dependency>

<!-- Jackson Kafka (serializer + deserializer déjà prêts) -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-json-schema-serializer</artifactId>
    <version>7.5.0</version>
</dependency>
```
> 🟨 Note : si jamais tu ne veux pas utiliser la lib de Confluent, on peut aussi faire nos propres JsonSerializer et JsonDeserializer manuellement. Mais Confluent fonctionne bien pour un test simple.

---

## 🛠️ Producteur Kafka avec Jackson

📄 Fichier : 
```bash 
src/main/java/com/example/kafka/service/KafkaProducerServicePerson.java
```

```java
package com.example.kafka.service;

import com.example.kafka.model.Person;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;

import java.util.Properties;

/**
 * Producteur Kafka configuré pour envoyer des objets Person en JSON.
 */
public class KafkaProducerServicePerson {
    // Classe responsable de l'envoi de messages Kafka contenant des objets de type Person.

    private final String bootstrapServers;
    // Adresse des serveurs Kafka (bootstrap servers) utilisée pour se connecter au cluster Kafka.

    public KafkaProducerServicePerson(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        // Constructeur qui initialise l'adresse des serveurs Kafka.
    }

    public void send(String topic, String key, Person person) {
        // Méthode pour envoyer un objet Person dans un topic Kafka donné avec une clé spécifiée.

        Properties props = new Properties();
        // Création d'un objet Properties pour configurer le producteur Kafka.

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Configuration de l'adresse des serveurs Kafka (bootstrap servers).

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Configuration du sérialiseur pour les clés (ici, les clés sont des chaînes de caractères).

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
        // Configuration du sérialiseur pour les valeurs (ici, les valeurs sont des objets Person sérialisés en JSON).

        try (Producer<String, Person> producer = new KafkaProducer<>(props)) {
            // Création d'un producteur Kafka avec les propriétés configurées.
            // Le bloc try-with-resources garantit que le producteur sera fermé automatiquement.

            ProducerRecord<String, Person> record = new ProducerRecord<>(topic, key, person);
            // Création d'un enregistrement Kafka (message) avec le topic, la clé et l'objet Person spécifiés.

            producer.send(record);
            // Envoi asynchrone du message au cluster Kafka.

            producer.flush();
            // Vidage des messages en attente pour s'assurer que le message est bien envoyé avant de fermer le producteur.
        }
        // Le producteur Kafka est automatiquement fermé à la fin du bloc try-with-resources.
    }
}
```

> 🟨 Note : on utilise `KafkaJsonSchemaSerializer` pour la sérialisation de l'objet `Person`.

---

## Test d’intégration avec Awaitility

📄 Fichier : 
```bash
src/test/java/com/example/kafka/KafkaIntegrationTestPerson.java
```

```java
package com.example.kafka.service;

import com.example.kafka.model.Person;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;

import java.util.Properties;

/**
 * Producteur Kafka configuré pour envoyer des objets Person en JSON.
 */
public class KafkaProducerServicePerson {
    // Classe responsable de l'envoi de messages Kafka contenant des objets de type Person.

    private final String bootstrapServers;
    // Adresse des serveurs Kafka (bootstrap servers) utilisée pour se connecter au cluster Kafka.

    public KafkaProducerServicePerson(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        // Constructeur qui initialise l'adresse des serveurs Kafka.
    }

    public void send(String topic, String key, Person person) {
        // Méthode pour envoyer un objet Person dans un topic Kafka donné avec une clé spécifiée.

        Properties props = new Properties();
        // Création d'un objet Properties pour configurer le producteur Kafka.

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Configuration de l'adresse des serveurs Kafka (bootstrap servers).

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Configuration du sérialiseur pour les clés (ici, les clés sont des chaînes de caractères).

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
        // Configuration du sérialiseur pour les valeurs (ici, les valeurs sont des objets Person sérialisés en JSON).

        try (Producer<String, Person> producer = new KafkaProducer<>(props)) {
            // Création d'un producteur Kafka avec les propriétés configurées.
            // Le bloc try-with-resources garantit que le producteur sera fermé automatiquement.

            ProducerRecord<String, Person> record = new ProducerRecord<>(topic, key, person);
            // Création d'un enregistrement Kafka (message) avec le topic, la clé et l'objet Person spécifiés.

            producer.send(record);
            // Envoi asynchrone du message au cluster Kafka.

            producer.flush();
            // Vidage des messages en attente pour s'assurer que le message est bien envoyé avant de fermer le producteur.
        }
        // Le producteur Kafka est automatiquement fermé à la fin du bloc try-with-resources.
    }
}
```

---

## ✅ Ce que tu as appris ici

✔️ Comment utiliser Jackson pour transformer un objet Java en JSON dans Kafka

✔️ Comment configurer Kafka pour consommer ce JSON automatiquement

✔️ Comment réaliser un test d’intégration de bout en bout avec des objets métier

---

## 📚 À suivre

Tester plusieurs objets JSON

Ajouter des champs imbriqués

Gérer les erreurs de désérialisation