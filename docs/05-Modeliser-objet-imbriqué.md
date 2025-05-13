---
id: 05-modeliser-objet-imbrique
title: Modéliser un objet métier imbriqué en JSON
description: Apprendre à tester Kafka avec des objets Java complexes sérialisés manuellement en JSON via Jackson
---

# 🧩 Modéliser un objet métier imbriqué et le tester dans Kafka

---

## 🎯 Objectif de cette étape

Dans cette section, on va apprendre à :

- Créer un **objet métier Java avec une structure imbriquée**
- Le **sérialiser en JSON** via Jackson
- L’envoyer dans un **topic Kafka via un Producer personnalisé**
- Créer une liste d’objets `Person`
- Les envoyer tous dans un **seul topic Kafka**
- Les consommer **dans l’ordre**
- Vérifier le contenu de chaque message reçu

Cela nous permet de manipuler des flux **plus réalistes**, proches de ceux utilisés dans les microservices ou systèmes métier.

---

## 🧱 Contexte

Dans l’étape précédente (`04-envoi-objets-metier-JSON`), nous avons appris à :

- Créer un POJO `Person`
- L’envoyer en JSON avec Kafka
- Le consommer et le désérialiser dans un test

Mais l’objet `Person` n’avait que des champs simples (primitives : `String`, `int`).

> Dans le réel, les objets Kafka contiennent souvent **des sous-objets imbriqués** (adresse, statut, métadonnées, etc.). Nous allons apprendre à les gérer ici.

## 🧱 Pourquoi des objets imbriqués ?

Dans des systèmes réels, les objets métier contiennent souvent des sous-objets. Par exemple :
- Une commande peut contenir une liste de produits.
- Un utilisateur peut avoir une adresse.

Les objets imbriqués permettent de modéliser ces relations complexes de manière naturelle.

---

## 🧰 Étape 1 — Création de la classe `Address` et mise à jour de a classe `Person`

### 📄 `Address.java`

```java
package com.example.kafka.model;

public class Address {
    private String street;
    private String city;
    private String state;
    private String zipCode;

    public Address() {}

    public Address(String street, String city, String state, String zipCode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zipCode;
    }

    public void setZip(String zip) {
        this.zipCode = zip;
    }

    @Override
    public String toString() {
        return "Address{" +
                "street='" + street + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", zipCode='" + zipCode + '\'' +
                '}';
    }
}
```

---

### 📄 `Person.java`


```java
package com.example.kafka.model;
public class Person {
    private String name;
    private int age;
    private Address address;

    public Person() {}

    public Person(String name, int age, Address address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }

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

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", address=" + address +
                '}';
    }
}
```

## 🧰 Étape 2 — Producteur Kafka avec Jackson

Nous remplaçons le KafkaJsonSchemaSerializer (Confluent) par une sérialisation manuelle via Jackson, plus légère et suffisante dans un test local.

### 📄 KafkaProducerServicePerson.java

```java
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
}

```

## 🧰 Étape 3 — Test Kafka avec structure imbriquée

### 📄 KafkaIntegrationTestPerson.java

```java
@Test
void testKafkaProducerWithPersonAndAddress() {
    String topic = "test-person-address";
    String key = "person1";

    Address address = new Address("42 rue des Tests", "Lille", "59000");
    Person person = new Person("Léa", 29, address);

    KafkaProducerServicePerson producer = new KafkaProducerServicePerson(kafka.getBootstrapServers());
    producer.send(topic, key, person);

    // Consumer configuré pour recevoir du texte brut JSON
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "group-person-address");
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
            assertFalse(records.isEmpty(), "Aucun message reçu");

            for (ConsumerRecord<String, String> record : records) {
                ObjectMapper mapper = new ObjectMapper();
                Person received = mapper.readValue(record.value(), Person.class);

                assertEquals(person.getName(), received.getName());
                assertEquals(person.getAddress().getCity(), received.getAddress().getCity());
            }
        });

    consumer.close();
}
```

## 🧰 Étape 4 — Envoi de plusieurs objets Person (batch Kafka)

Après avoir validé l’envoi d’un seul objet `Person` avec une `Address`, nous allons tester l’envoi **de plusieurs objets JSON** dans un même topic Kafka.  
Cela simule un cas fréquent de traitement de batch dans les architectures orientées événements.

### 📄 Utilisation de la méthode `sendBatch(...)`

Dans `KafkaProducerServicePerson.java`, nous avons défini une méthode `sendBatch(...)` :

```java
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
```

Cette méthode sérialise chaque objet Person en JSON avant de l’envoyer.

### 🧪 Test d'intégration : testKafkaBatchJsonObjects()

Dans KafkaIntegrationTestPerson.java, nous ajoutons un test pour valider la réception de plusieurs objets envoyés :

```java
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
```

Commande pour relancer les tests : 
```bash
mvn clean test -Dtest=KafkaIntegrationTestPerson
``` 


✅ Ce que ce test permet de valider
✔️ Envoi multiple de messages JSON dans un topic Kafka
✔️ Réception complète des messages dans un test automatisé
✔️ Désérialisation et validation de chaque objet métier
✔️ Simulation d’un comportement réaliste en production


## ✅ Ce que tu as appris ici

✔️ Comment modéliser une structure de données imbriquée côté Java   
✔️ Comment la convertir en JSON pour Kafka (sans dépendance complexe)   
✔️ Comment l’envoyer dans un topic Kafka depuis un test 
✔️ Comment la retransformer en objet Java pour valider son contenu  

---

## 📚 À suivre


Gestion d’erreurs de désérialisation (ex : champ manquant ou type invalide)

