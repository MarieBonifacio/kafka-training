---
title: Tester la résilience à la désérialisation JJSON
description: Vérifier la robustesse de votre architecture Kafka face à des erreurs de parsing JSON

---


## 🧭 Objectif pédagogique

L’objectif ici est de valider que notre application **ne plante pas** lorsqu’elle reçoit un message Kafka au **format JSON invalide** (erreur de type, champ manquant, etc.).  
Nous allons simuler un cas de désérialisation cassée, et mettre en place une stratégie simple de **gestion d’erreur robuste**.

---

## 🧪 Simulation d’une erreur de désérialisation

### ❌ Exemple de message JSON invalide

```json
{
  "name": "Hugo",
  "age": "trente", // ⚠️ ce champ devrait être un entier
  "address": {
    "street": "rue X",
    "city": "Lille",
    "zip": "59000"
  }
}
```
Le champ age est ici une chaîne de caractères, alors que l’objet Java Person attend un int.


## ⚙️ Configuration Kafka minimale (sans serializer custom)

Nous utilisons un producteur/consommateur simple avec les classes standard :

- StringSerializer pour le producer

- StringDeserializer côté consumer

- Désérialisation manuelle avec Jackson dans un bloc try/catch


# 📄 Exemple de test complet
Fichier : KafkaDeserializationResilienceTest.java

```java
@Test
void testJsonDeserializationError() {
    String topic = "test-json-error";
    String badJson = """
        { "name": "Hugo", "age": "trente", "address": { "street": "rue X", "city": "Lille", "zip": "59000" } }
    """;

    Producer<String, String> producer = new KafkaProducer<>(...);
    producer.send(new ProducerRecord<>(topic, "key1", badJson));
    producer.flush();

    Consumer<String, String> consumer = new KafkaConsumer<>(...);
    consumer.subscribe(List.of(topic));

    Awaitility.await().untilAsserted(() -> {
        var records = consumer.poll(Duration.ofMillis(500));
        assertFalse(records.isEmpty());

        for (var record : records) {
            try {
                var mapper = new ObjectMapper();
                var person = mapper.readValue(record.value(), Person.class);
                fail("Le test aurait dû échouer !");
            } catch (Exception e) {
                System.out.println("✅ Erreur de désérialisation capturée : " + e.getMessage());
            }
        }
    });
}
```

## ✅ Ce que ce test vérifie

✔️ Le message est bien reçu par Kafka   
✔️ La désérialisation échoue dans le try/catch  
✔️ L’erreur est capturée, sans provoquer de crash du test ni de NullPointerException    

---

## 🧠 Bonnes pratiques à appliquer


| Bonne pratique                                         | Pourquoi ?                                              |
| ------------------------------------------------------ | ------------------------------------------------------- |
| 🔁 Utiliser `try/catch` lors de la désérialisation     | Pour isoler les erreurs et empêcher les crashs          |
| 🪵 Logger une erreur claire avec contexte              | Facilite l’analyse en prod                              |
| 🧪 Écrire des tests dédiés à la robustesse             | Permet de détecter les cas limites dès le CI            |
| 🧮 Éviter les `readValue(...)` directs sans validation | Pour ne pas faire confiance aveuglément au JSON entrant |


## 📊 Stratégies de journalisation et monitoring

## 🔎 Ce qu’on peut surveiller :

Nombre d’erreurs de parsing par topic

Clés associées aux messages cassés

Horodatage + trace technique

💡 Exemple de logique pro :

```java
try {
    Person p = mapper.readValue(record.value(), Person.class);
} catch (JsonProcessingException e) {
    logger.warn("Erreur de parsing JSON dans Kafka : " + e.getMessage());
    metrics.increment("kafka.deserialization.error");
}
```

🧪 Commande pour relancer le test


```bash
mvn test -Dtest=KafkaDeserializationResilienceTest
```

Tu dois voir :

```bash
✅ Erreur de désérialisation capturée : Cannot deserialize value of type `int` from String "trente"
 ```
---

📌 Conclusion

Tu viens d’apprendre à :

Simuler des erreurs réalistes dans un flux Kafka

Capturer ces erreurs de manière propre

Mettre en place une base de monitoring résilient

Appliquer des bonnes pratiques compatibles production

---

### 🧪 Approfondissements possibles avant l'étape métier


## 1. 🧱 Tester plusieurs erreurs différentes
Exemples :

| Cas                     | Exemple                                 |
| ----------------------- | --------------------------------------- |
| 🧩 Type incorrect       | `"age": "trente"` au lieu d’un int      |
| ❌ Champ manquant        | Pas de `address` du tout                |
| 🧨 Structure incorrecte | `"address": "rue x"` au lieu d’un objet |


🎯 But : valider que chaque cas est capturé proprement et que tu peux les différencier dans les logs.

# 📄 Test dédié

On ajoute une méthode par erreur dans KafkaDeserializationResilienceTest.java :

```java
@Test
void testDeserializationFailsOnMissingField() {
    String badJson = """{ "name": "Hugo", "age": 30 }"""; // pas de 'address'

    testInvalidDeserialization(badJson, "champ manquant");
}

@Test
void testDeserializationFailsOnInvalidAddressStructure() {
    String badJson = """{ "name": "Hugo", "age": 30, "address": "invalid" }"""; // address en string

    testInvalidDeserialization(badJson, "structure invalide");
}


private void testInvalidDeserialization(String json, String label) {
    String topic = "test-json-error-" + label.replace(" ", "-");
    Producer<String, String> producer = new KafkaProducer<>(...);
    producer.send(new ProducerRecord<>(topic, "key", json));
    producer.flush();

    Consumer<String, String> consumer = new KafkaConsumer<>(...);
    consumer.subscribe(List.of(topic));

    Awaitility.await().untilAsserted(() -> {
        var records = consumer.poll(Duration.ofMillis(500));
        assertFalse(records.isEmpty());

        for (var record : records) {
            try {
                var mapper = new ObjectMapper();
                mapper.readValue(record.value(), Person.class);
                fail("La désérialisation aurait dû échouer pour : " + label);
            } catch (Exception e) {
                System.out.println("✅ Erreur attendue (" + label + ") : " + e.getMessage());
            }
        }
    });
}
```



---


## 2. 🪵 Logger de manière structurée
Utiliser SLF4J + Logback au lieu de System.out

Exemple :

```java
private static final Logger log = LoggerFactory.getLogger(getClass());
log.warn("Erreur JSON pour key {}: {}", record.key(), e.getMessage());
```
🎯 But : pratique attendue en entreprise pour tracer les erreurs Kafka dans un ELK / Datadog / Sentry.

---


## 3. 📊 Compter les erreurs avec une métrique

Simuler un compteur (int parseFailures = 0)

Ou utiliser Micrometer si tu veux l’intégrer plus tard à Prometheus

🎯 But : préparer un hook vers du monitoring réel

---

## 4. 🧪 Extraire la désérialisation dans une méthode dédiée

```java	
public Optional<Person> safeParse(String json) {
  try {
    return Optional.of(mapper.readValue(json, Person.class));
  } catch (...) {
    log.warn("Erreur de parsing", e);
    return Optional.empty();
  }
}
```

🎯 But : réutilisabilité + testabilité unitaire

---

## 5. 🔁 Remonter une version plus robuste
Utiliser un schema registry avec Avro ou JSON Schema

Ou valider manuellement ton JSON avec une lib comme Everit ou Justify

🎯 But : poser la base pour ton rôle de référente qualité Kafka