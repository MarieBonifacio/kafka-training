---
id: 02-producer-consumer-separation
title: Séparer Producteur et Consommateur Kafka 
description: Ce document explique comment séparer la logique de production et de consommation dans des classes Java dédiées.
---

# 📦 Refactor : Producteur et Consommateur Kafka réutilisables

---

## 🎯 Objectif

Dans cette étape, nous allons améliorer notre test Kafka en **séparant la logique de production et de consommation** dans des **classes Java dédiées**.

## Ce qu'on va couvrir :
- ✅ Créer un `KafkaProducerService` et un `KafkaConsumerService`
- ✅ Écrire un test d’intégration pour vérifier l’envoi et la réception de messages
- ✅ Utiliser Awaitility pour attendre la réception des messages dans le topic Kafka
- ✅ Vérifier que les messages reçus correspondent à ceux envoyés

Pourquoi ?

- ✅ Rendre le code plus lisible et maintenable
- 🔁 Réutiliser le même producteur/consommateur dans plusieurs tests
- 📐 Poser les bases pour des tests d'intégration plus réalistes

---

## 🧠 Contexte de départ

Jusqu'ici, toute la logique était dans le test `KafkaIntegrationTest`.  
Cela fonctionne… mais ce n’est pas propre :

- Les `Properties` sont dupliquées
- On ne peut pas tester séparément la logique du producteur ou du consommateur
- Difficile à maintenir dans un vrai projet

---

## 🛠️ Création du `KafkaProducerService`

```java
package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Service responsable de l'envoi de messages Kafka (key et value en String).
 */
public class KafkaProducerService {
    // Classe responsable de l'envoi de messages Kafka avec des clés et des valeurs de type String.

    private final String bootstrapServers;
    // Adresse des serveurs Kafka (bootstrap servers) utilisée pour se connecter au cluster Kafka.

    public KafkaProducerService(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        // Constructeur qui initialise l'adresse des serveurs Kafka.
    }

    public void send(String topic, String key, String value) {
        // Méthode pour envoyer un message Kafka à un topic donné avec une clé et une valeur.

        Properties props = new Properties();
        // Création d'un objet Properties pour configurer le producteur Kafka.

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Configuration de l'adresse des serveurs Kafka (bootstrap servers).

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Configuration du sérialiseur pour les clés (ici, les clés sont des chaînes de caractères).

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Configuration du sérialiseur pour les valeurs (ici, les valeurs sont des chaînes de caractères).

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            // Création d'un producteur Kafka avec les propriétés configurées.
            // Le bloc try-with-resources garantit que le producteur sera fermé automatiquement.

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            // Création d'un enregistrement Kafka (message) avec le topic, la clé et la valeur spécifiés.

            producer.send(record);
            // Envoi asynchrone du message au cluster Kafka.

            producer.flush();
            // Vidage des messages en attente pour s'assurer que le message est bien envoyé avant de fermer le producteur.
        }
    }
}
```
>💡 Cette classe ne garde pas l'instance du producer, elle crée et ferme à chaque envoi. On peut améliorer ça plus tard.

## 🛠️ Création du KafkaConsumerService

```java
package com.example.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Service responsable de la consommation de messages Kafka (key et value en String).
 */
public class KafkaConsumerService {
    // Classe responsable de la consommation de messages Kafka avec des clés et des valeurs de type String.

    private final String bootstrapServers;
    // Adresse des serveurs Kafka (bootstrap servers) utilisée pour se connecter au cluster Kafka.

    private final String groupId;
    // Identifiant du groupe de consommateurs Kafka.

    private final Consumer<String, String> consumer;
    // Instance du consommateur Kafka qui sera utilisée pour lire les messages.

    public KafkaConsumerService(String bootstrapServers, String groupId) {
        // Constructeur qui initialise les serveurs Kafka et le groupe de consommateurs.

        this.bootstrapServers = bootstrapServers;
        // Initialise l'adresse des serveurs Kafka.

        this.groupId = groupId;
        // Initialise l'identifiant du groupe de consommateurs.

        Properties props = new Properties();
        // Création d'un objet Properties pour configurer le consommateur Kafka.

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Configuration de l'adresse des serveurs Kafka (bootstrap servers).

        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Configuration de l'identifiant du groupe de consommateurs.

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // Active la validation automatique des offsets après consommation des messages.

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Configure le consommateur pour lire les messages depuis le début si aucun offset n'est trouvé.

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Configure le désérialiseur pour les clés (ici, les clés sont des chaînes de caractères).

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Configure le désérialiseur pour les valeurs (ici, les valeurs sont des chaînes de caractères).

        this.consumer = new KafkaConsumer<>(props);
        // Instancie un consommateur Kafka avec les propriétés configurées.
    }

    public List<ConsumerRecord<String, String>> pollMessages(String topic, Duration timeout) {
        // Méthode pour récupérer les messages d'un topic Kafka avec un délai d'attente spécifié.

        consumer.subscribe(Collections.singletonList(topic));
        // Abonne le consommateur au topic spécifié.

        ConsumerRecords<String, String> records = consumer.poll(timeout);
        // Récupère les messages du topic avec un délai d'attente défini.

        List<ConsumerRecord<String, String>> list = new ArrayList<>();
        // Crée une liste pour stocker les messages récupérés.

        for (ConsumerRecord<String, String> record : records.records(topic)) {
            // Parcourt les messages récupérés pour le topic spécifié.

            list.add(record);
            // Ajoute chaque message à la liste.
        }

        return list;
        // Retourne la liste des messages récupérés.
    }

    public void close() {
        // Méthode pour fermer le consommateur Kafka.

        consumer.close();
        // Ferme le consommateur pour libérer les ressources.
    }
}
```

## 🧪 Nouveau test d’intégration : KafkaIntegrationTest

```java
package com.example.kafka;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
// Annotation indiquant que cette classe utilise Testcontainers pour gérer des conteneurs Docker dans les tests.

public class KafkaIntegrationTest {
// Déclaration de la classe de test pour tester l'intégration avec Kafka.

    @Container
    // Annotation indiquant que ce conteneur Kafka sera géré automatiquement par Testcontainers.

    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.2.1")
                       .asCompatibleSubstituteFor("apache/kafka")
    );
    // Déclaration d'un conteneur Kafka basé sur l'image Docker "confluentinc/cp-kafka:7.2.1".
    // Cette image est utilisée comme substitut compatible pour Apache Kafka.

    @Test
    // Annotation indiquant qu'il s'agit d'un test unitaire.

    void testKafkaProducerAndConsumerServices() {
    // Méthode de test pour vérifier le fonctionnement des services Kafka Producer et Consumer.

        String topic = "test-topic";
        // Nom du topic Kafka utilisé pour le test.

        String key = "key";
        // Clé du message Kafka.

        String value = "value";
        // Valeur du message Kafka.

        System.out.println("🚀 Lancement du test Kafka avec services producteurs/consommateurs...");
        // Affiche un message indiquant le début du test.

        KafkaProducerService producer = new KafkaProducerService(kafka.getBootstrapServers());
        // Instancie un service Kafka Producer avec l'adresse des serveurs Kafka fournie par le conteneur.

        KafkaConsumerService consumer = new KafkaConsumerService(kafka.getBootstrapServers(), "test-group");
        // Instancie un service Kafka Consumer avec l'adresse des serveurs Kafka et un groupe de consommateurs.

        System.out.println("🛠️  Producteur et consommateur instanciés");
        // Affiche un message indiquant que le producteur et le consommateur ont été créés.

        producer.send(topic, key, value);
        // Envoie un message au topic Kafka avec la clé et la valeur spécifiées.

        System.out.println("📤 Message envoyé sur le topic : " + topic);
        // Affiche un message indiquant que le message a été envoyé au topic.

        System.out.println("⏳ Attente de réception du message...");
        // Affiche un message indiquant que le test attend la réception du message.

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                // Définit une durée maximale d'attente de 10 secondes pour que la condition soit remplie.

                .pollInterval(Duration.ofMillis(500))
                // Définit un intervalle de 500 millisecondes entre chaque vérification de la condition.

                .untilAsserted(() -> {
                    // Exécute une vérification répétée jusqu'à ce que la condition définie soit remplie ou que le délai maximal soit atteint.

                    List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> messages =
                        consumer.pollMessages(topic, Duration.ofMillis(500));
                    // Récupère les messages du topic Kafka en utilisant le consommateur, avec un délai d'attente de 500 millisecondes.

                    System.out.println("✅ Messages reçus : " + messages.size());
                    // Affiche dans la console le nombre de messages reçus.

                    assertFalse(messages.isEmpty(), "Aucun message reçu !");
                    // Vérifie que la liste des messages n'est pas vide. Si elle est vide, le test échoue avec le message "Aucun message reçu !".

                    assertEquals(key, messages.get(0).key(), "Clé incorrecte");
                    // Vérifie que la clé du premier message reçu correspond à la clé attendue. Si ce n'est pas le cas, le test échoue avec le message "Clé incorrecte".

                    assertEquals(value, messages.get(0).value(), "Valeur incorrecte");
                    // Vérifie que la valeur du premier message reçu correspond à la valeur attendue. Si ce n'est pas le cas, le test échoue avec le message "Valeur incorrecte".
                });

        consumer.close();
        // Ferme le consommateur Kafka pour libérer les ressources.

        System.out.println("🧹 Consommateur fermé proprement");
        // Affiche dans la console que le consommateur a été fermé correctement.

        System.out.println("🎉 Test terminé avec succès !");
        // Affiche dans la console que le test s'est terminé avec succès.
    }
}
```
>💡 On utilise `pollMessages` pour récupérer les messages. On peut améliorer la gestion des offsets et des erreurs plus tard.   
>💡 On peut aussi ajouter des logs pour suivre l'envoi et la réception des messages.

### Étapes du test :
1. Lancement d’un conteneur Kafka via Testcontainers
2. Création du producteur et du consommateur
3. Envoi d’un message `"key":"value"` dans le topic
4. Attente (avec Awaitility) que le consommateur le lise
5. Validation que la clé et la valeur sont correctes
6. Fermeture du consommateur

### 🔍 Résultat console

Lors de l'exécution, tu devrais voir dans la **Debug Console** de VSCode :

```plaintext
🚀 Lancement du test Kafka avec services producteurs/consommateurs...
🛠️  Producteur et consommateur instanciés
📤 Message envoyé sur le topic : test-topic
⏳ Attente de réception du message...
✅ Messages reçus : 1
🧹 Consommateur fermé proprement
🎉 Test terminé avec succès !
```
>💡 Si tu vois "Aucun message reçu !" ou "Clé incorrecte", c'est que le test a échoué. Vérifie que le conteneur Kafka est bien lancé et accessible.
>💡 Pense à ajouter des logs pour suivre l'envoi et la réception des messages..

## ✅ Ce que tu as appris ici
✔️ Comment structurer le code Kafka de manière modulaire    
✔️ Comment écrire des services simples mais testables   
✔️ Comment rendre ton test plus propre, clair, et évolutif  

---

## 📚 À suivre
✔️ L'envoi de messages multiples dans un topic Kafka

## ✍️ À faire toi-même (exercice)
Modifier KafkaProducerService pour permettre l’envoi de plusieurs messages

Ajouter un paramètre pour customiser les sérializers

Écrire un test qui envoie 3 messages et vérifie leur ordre de réception