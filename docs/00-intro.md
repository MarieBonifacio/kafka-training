---
id: 00-intro
title: Introduction
---

# 🎓 Formation Kafka + Testcontainers (Java + Maven)

Bienvenue dans cette formation interne, progressive et orientée pratique, pour apprendre à écrire des **tests automatisés Kafka** avec **JUnit 5**, **Testcontainers**, et **Maven**.

---

## 🧠 Objectifs

- Maîtriser les bases de Kafka en local sans cluster complexe
- Utiliser Testcontainers pour tester Kafka sans dépendance externe
- Écrire des tests fiables et lisibles avec Awaitility
- Manipuler des messages complexes (ex : JSON) dans Kafka
- Créer une structure de projet claire et réutilisable
- Documenter une formation utile pour tout développeur ou QE débutant

---

## 🧰 Prérequis techniques

- **Java 17+**
- **Docker Desktop** installé et lancé (Testcontainers l’utilise)
- **Maven** (et non Gradle !)
- **IDE : VSCode** (ou autre)
- Extension **Java Test Runner** recommandée dans VSCode

---

## 📁 Structure pédagogique

La formation est découpée en chapitres progressifs. Chacun contient :
- Des explications pédagogiques
- Des extraits de code Maven/Java
- Des commentaires utiles pour un débutant
- Des exercices facultatifs

---

## 🚀 Projet fil rouge

Nous allons tester un système Kafka simple, composé :
- D’un producteur Java qui envoie un message dans un topic
- D’un consommateur Java qui lit ce message
- D’un test d’intégration utilisant un conteneur Kafka temporaire

Le tout sera documenté et versionné pour être réutilisable facilement.
