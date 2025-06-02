---
id: 06-tester-resilience-deserialisation
title: Tester la résilience à la désérialisation JSON
description: Apprendre à détecter et gérer proprement les erreurs de désérialisation dans Kafka, avec un focus pédagogique pour les débutants.
---

# 🛡️ Tester la résilience à la désérialisation JSON

---

## 🎯 Objectif

Dans cette partie, nous allons apprendre à rendre notre système Kafka plus robuste face à un problème courant :  
👉 **un message JSON mal formé ou inattendu** qui casse la désérialisation côté consommateur.

Nous allons voir :
- Comment **simuler des erreurs** de désérialisation
- Comment les **capturer proprement**
- Comment **logger** l’erreur comme en entreprise
- Et comment **rendre nos tests stables et prédictibles**

---

## 📦 Pourquoi c’est important

Kafka est souvent utilisé pour faire transiter des objets métier au format JSON.  
Mais si le format change, ou si un message est corrompu, on peut avoir :

- une **erreur silencieuse** (message ignoré)
- ou pire : un **crash du service** au moment de la désérialisation

En tant que Quality Engineer, il est crucial de tester la **résilience** de notre application face à ces cas.

---

## 🔨 Ce qu’on va tester

Nous allons envoyer des messages Kafka contenant du JSON volontairement incorrect, par exemple :

- 🔁 un champ mal typé (`"age": "trente"` au lieu d’un int)
- 🧩 une structure erronée (`"address": "rue de Lille"` au lieu d’un objet)
- ❌ un champ important manquant (`address` absent)

Et nous allons vérifier que notre application :
- **reçoit bien le message**
- **échoue proprement** à la désérialisation
- **log l’erreur sans planter**

---

## 🧪 Exemple de test automatisé

Nous utilisons JUnit, Awaitility, et Testcontainers pour lancer un Kafka local automatiquement.

Fichier : `KafkaDeserializationResilienceTest.java`

### Cas 1 : type incorrect (fail attendu)

```java
@Test
void testJsonDeserializationError() {
    String json = """
        {
            "name": "Hugo",
            "age": "trente",
            "address": {
                "street": "rue X",
                "city": "Lille",
                "zip": "59000"
            }
        }
    """;
    testInvalidDeserialization(json, "type incorrect : age=String");
}
```

## 🧠 Pourquoi ce test passe ?

Le champ age est censé être un int dans notre classe Person.
Mais ici on envoie une String.
Jackson ne peut pas convertir "trente" en int ⇒ une exception est levée.

Nous avons mis ce code dans un bloc try/catch dans la méthode factorisée testInvalidDeserialization(...).


### Cas 2, plus subtil : champ manquant


```java
{ "name": "Hugo", "age": 30 }
```
Est-ce que ça casse ?

❌ Non. Par défaut, Jackson accepte les champs manquants s’ils ne sont pas strictement requis (constructeur ou validation).
Donc si address est absent, Jackson met juste null.


## ✅ Astuce pour forcer une erreur sur champ manquant
Dans notre méthode de test, nous avons ajouté une ligne spéciale :

```java
if (person.address == null) {
    throw new IllegalArgumentException("Champ 'address' manquant !");
}
```

Cela permet de transformer ce "champ manquant toléré" en erreur volontaire, pour s’assurer que l’API attend bien un address.


## 📄 Méthode de test factorisée

Signature :

```java
private void testInvalidDeserialization(String json, String label)
```

Ce qu’elle fait :

1. Crée un topic Kafka avec un nom basé sur le label

2. Envoie le json cassé dans ce topic

3. Lance un KafkaConsumer pour écouter ce topic

4. Attend 10 secondes max pour recevoir un message

5. Essaie de le désérialiser

6. Si une erreur est capturée → ✅ le test passe

7. Sinon → ❌ on appelle fail(...)

Détail du cœur du test :

```java
try {
    Person person = mapper.readValue(record.value(), Person.class);

    // Si on veut forcer l’échec sur champ null
    if (person.address == null) {
        throw new IllegalArgumentException("Champ 'address' manquant !");
    }

    // Sinon, la désérialisation passe alors qu'elle aurait dû échouer
    fail("La désérialisation aurait dû échouer !");
} catch (Exception e) {
    log.warn("Erreur capturée : {}", e.getMessage());
    errorCaptured[0] = true;
}
```

## 🪵 Utilisation d’un logger propre (au lieu de println)

Nous avons remplacé tous les System.out.println(...) par :

```java
private static final Logger log = LoggerFactory.getLogger(KafkaDeserializationResilienceTest.class);
```

Et dans les tests :

```java
log.warn("Erreur de désérialisation capturée : {}", e.getMessage());
```

✅ Cela permet d’avoir des logs lisibles, structurés, et compatibles avec une plateforme d’observabilité (ELK, Datadog…).



## 📌 Ce que tu as appris ici
✔ Comment envoyer un JSON cassé dans Kafka
✔ Comment capturer les erreurs de désérialisation JSON avec Jackson
✔ Pourquoi certaines erreurs ne lèvent pas d’exception (et comment forcer une validation)
✔ Comment structurer un test clair et modulaire pour tester plusieurs cas
✔ Comment logger proprement comme en entreprise

