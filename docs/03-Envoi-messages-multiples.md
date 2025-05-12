---
id: 03-envoi-messages-multiples
title: Envoi de messages multiples 
description: Ce document explique comment envoyer plusieurs messages en utilisant Kafka.
---

## 🎯 Objectif
Envoyer plusieurs messages en une seule fois dans un topic Kafka, en utilisant une méthode de type `sendBatch(...)`.

## Ce qu’on va couvrir :    

✅ Créer une méthode `sendBatch(...)` dans le service KafkaProducerService  

✅ Écrire un test d’intégration pour vérifier l’envoi de plusieurs messages 

✅ Utiliser Awaitility pour attendre la réception des messages dans le topic Kafka  

✅ Vérifier que les messages reçus correspondent à ceux envoyés 



## 🛠️ Nouvelle méthode : `sendBatch(...)`

Voici le code ajouté dans `KafkaProducerService` :

```java
    public void sendBatch(String topic, List<Map.Entry<String, String>> messages) {
        // Méthode pour envoyer une liste de messages (clé/valeur) dans un topic Kafka donné.

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

            for (Map.Entry<String, String> entry : messages) {
                // Parcourt chaque message (clé/valeur) de la liste des messages.

                ProducerRecord<String, String> record = new ProducerRecord<>(topic, entry.getKey(), entry.getValue());
                // Crée un enregistrement Kafka (message) avec le topic, la clé et la valeur spécifiés.

                producer.send(record);
                // Envoie le message au cluster Kafka de manière asynchrone.
            }

            producer.flush();
            // Vide les messages en attente pour s'assurer que tous les messages ont été envoyés avant de fermer le producteur.
        }
        // Le producteur Kafka est automatiquement fermé à la fin du bloc try-with-resources.
    }
```

Voici le code ajouté dans `KafkaIntegrationTest` :

```java
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
    
    void testKafkaBatchSending() {
    // Méthode de test pour vérifier l'envoi et la réception de messages Kafka en lot.
    
        String topic = "test-batch";
        // Nom du topic Kafka utilisé pour le test.
    
        KafkaProducerService producer = new KafkaProducerService(kafka.getBootstrapServers());
        // Instancie un service Kafka Producer avec l'adresse des serveurs Kafka fournie par le conteneur.
    
        KafkaConsumerService consumer = new KafkaConsumerService(kafka.getBootstrapServers(), "test-group-batch");
        // Instancie un service Kafka Consumer avec l'adresse des serveurs Kafka et un groupe de consommateurs.
    
        List<Map.Entry<String, String>> messages = List.of(
            Map.entry("k1", "v1"),
            Map.entry("k2", "v2"),
            Map.entry("k3", "v3")
        );
        // Crée une liste de messages (clé/valeur) à envoyer au topic Kafka.
    
        System.out.println("📤 Envoi de 3 messages dans le topic");
        // Affiche un message indiquant que les messages sont en cours d'envoi.
    
        producer.sendBatch(topic, messages);
        // Envoie les messages en lot au topic Kafka spécifié.
    
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                // Définit une durée maximale d'attente de 10 secondes pour que la condition soit remplie.
    
                .pollInterval(Duration.ofMillis(500))
                // Définit un intervalle de 500 millisecondes entre chaque vérification de la condition.
    
                .untilAsserted(() -> {
                    // Exécute une vérification répétée jusqu'à ce que la condition définie soit remplie ou que le délai maximal soit atteint.
    
                    List<ConsumerRecord<String, String>> received =
                        consumer.pollMessages(topic, Duration.ofMillis(500));
                    // Récupère les messages du topic Kafka en utilisant le consommateur, avec un délai d'attente de 500 millisecondes.
    
                    assertEquals(3, received.size(), "Le nombre de messages reçus est incorrect");
                    // Vérifie que le nombre de messages reçus est égal à 3. Si ce n'est pas le cas, le test échoue avec un message d'erreur.
    
                    for (int i = 0; i < messages.size(); i++) {
                        // Parcourt les messages envoyés pour les comparer aux messages reçus.
    
                        assertEquals(messages.get(i).getKey(), received.get(i).key());
                        // Vérifie que la clé du message reçu correspond à la clé du message envoyé.
    
                        assertEquals(messages.get(i).getValue(), received.get(i).value());
                        // Vérifie que la valeur du message reçu correspond à la valeur du message envoyé.
                    }
                });
    
        consumer.close();
        // Ferme le consommateur Kafka pour libérer les ressources.
    }
```



## 🧠 Pourquoi c’est utile

✔️ Permet de tester des cas plus proches du réel   

✔️ Pose les bases pour tester des batchs d’événements  

✔️ Montre comment Kafka peut gérer plusieurs messages en un seul flush 

---

## Ce que tu as appris ici
✔️ Comment envoyer plusieurs messages en une seule fois dans un topic Kafka
✔️ Comment utiliser Awaitility pour attendre la réception des messages dans le topic Kafka
✔️ Comment vérifier que les messages reçus correspondent à ceux envoyés
✔️ Comment configurer un test d’intégration pour vérifier l’envoi de plusieurs messages

---

## 📚 À suivre

✅ Envoi d'objets métier en JSON
✅ Utilisation de Jackson pour sérialiser des objets métier en JSON
✅ Utilisation de KafkaJsonSchemaSerializer pour consommer des objets JSON
✅ Utilisation de KafkaJsonSchemaDeserializer pour désérialiser des objets JSON

## ✍️ À faire

Tester des messages avec la même clé    

Ajouter un timestamp personnalisé dans le ProducerRecord    

Tester la réception hors ordre (exercice avancé)    