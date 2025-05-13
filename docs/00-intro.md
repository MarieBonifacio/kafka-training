---
id: 00-intro
title: Introduction
---

# 🎓 Formation Kafka + Testcontainers (Java + Maven)

Bienvenue dans cette formation pratique pour apprendre à automatiser des tests Kafka avec Java, Maven, et Testcontainers.

---

# 🎓 Formation Kafka + Testcontainers (Java + Maven)

Bienvenue dans cette formation pratique pour apprendre à automatiser des tests Kafka avec Java, Maven, et Testcontainers.

---

## 🧠 Pourquoi cette formation ?

Kafka est un outil clé dans les architectures modernes pour gérer des flux de données en temps réel. Cette formation vous permettra de :
- Comprendre les bases de Kafka.
- Automatiser des tests Kafka sans dépendre d'un cluster réel.
- Manipuler des données complexes (JSON) dans Kafka.
- Créer des tests fiables et réutilisables.

---

## 🗺️ Aperçu du projet

Voici un aperçu du flux que nous allons tester :

```plaintext
+-------------+       +----------------+       +----------------+
| KafkaProducer| ---> | Kafka (Topic)  | ---> | KafkaConsumer  |
+-------------+       +----------------+       +----------------+

```
- **KafkaProducer** : Envoie des messages à un topic Kafka.
- **Kafka (Topic)** : Stocke les messages.
- **KafkaConsumer** : Lit les messages du topic.

---

## 📁 Structure pédagogique

Chaque chapitre contient :

- Des explications claires et progressives.
- Des extraits de code commentés.
- Des exercices pratiques pour renforcer vos compétences.