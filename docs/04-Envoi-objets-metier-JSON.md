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


Kafka est conçu pour transmettre des données sous forme de **tableaux d'octets** ou de **texte brut**. Cependant, dans les applications modernes, nous travaillons souvent avec des objets complexes (comme `Person`), qui ne peuvent pas être envoyés directement.

Pour résoudre ce problème :
1. Nous utilisons **Jackson** pour convertir nos objets Java en JSON (sérialisation).
2. À la réception, nous utilisons Jackson pour reconvertir le JSON en objets Java (désérialisation).

JSON (JavaScript Object Notation) est un format léger et lisible par les humains, idéal pour transmettre des données structurées.

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
    private String name;
    private int age;

    // Obligatoire pour Jackson (constructeur vide)
    public Person() {}

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // Getters et setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
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

Pour utiliser Jackson et Kafka dans notre projet, nous devons ajouter les dépendances suivantes dans le fichier `pom.xml` :


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
> Pourquoi ces dépendances ?
Jackson Databind : Permet de convertir des objets Java en JSON et vice-versa.
Kafka JSON Schema Serializer : Fournit des sérialiseurs et désérialiseurs prêts à l'emploi pour Kafka.
---

## 🛠️ Producteur Kafka avec Jackson

📄 Fichier : 
```bash 
src/main/java/com/example/kafka/service/KafkaProducerServicePerson.java
```

```java

---

#### **4. Ajouter des explications dans le producteur Kafka :**
```java
public void send(String topic, String key, Person person) {
    // Configuration des propriétés du producteur Kafka
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());

    // Création du producteur Kafka
    try (Producer<String, Person> producer = new KafkaProducer<>(props)) {
        // Création d'un enregistrement Kafka (message)
        ProducerRecord<String, Person> record = new ProducerRecord<>(topic, key, person);

        // Envoi asynchrone du message
        producer.send(record);

        // Vidage des messages en attente
        producer.flush();
    }
}

```

> 🟨 Note : on utilise `KafkaJsonSchemaSerializer` pour la sérialisation de l'objet `Person`.

---

## Test d’intégration avec Awaitility

Voici un exemple de test d'intégration complet pour valider l'envoi et la réception d'un objet `Person` dans Kafka.


📄 Fichier : 
```bash
src/test/java/com/example/kafka/KafkaIntegrationTestPerson.java
```

```java
@Test
void testKafkaProducerWithPerson() {
    // Étape 1 : Configuration du producteur
    KafkaProducerServicePerson producer = new KafkaProducerServicePerson(kafka.getBootstrapServers());
    Person person = new Person("Alice", 30);
    producer.send("test-topic", "key1", person);

    // Étape 2 : Configuration du consommateur
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    Consumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(Collections.singletonList("test-topic"));

    // Étape 3 : Validation des messages reçus
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            assertFalse(records.isEmpty(), "Aucun message reçu");

            for (ConsumerRecord<String, String> record : records) {
                ObjectMapper mapper = new ObjectMapper();
                Person received = mapper.readValue(record.value(), Person.class);

                assertEquals(person.getName(), received.getName());
                assertEquals(person.getAge(), received.getAge());
            }
        });

    consumer.close();
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