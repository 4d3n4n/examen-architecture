# Plan d'Action - TP Architecture Logicielle (Microservices)

## Objectif et Ligne Directrice
Ce document définit les étapes strictes à suivre pour réaliser le TP "Gestion d'une plateforme de réservation de salles de coworking".
L'objectif est de **respecter de manière parfaite et exclusive les exigences du TP**. Aucune fonctionnalité superflue ou hors-sujet ne sera ajoutée. Le code sera propre, respectera les bonnes pratiques Spring Boot / microservices, et sera facilement testable via CLI.

---

## Étape 1 : Initialisation de l'Infrastructure
- **Tâche 1.1** : Créer le projet `config-server` (Spring Cloud Config).
- **Tâche 1.2** : Créer le projet `discovery-server` (Eureka Server).
- **Tâche 1.3** : Créer le projet `api-gateway` (Spring Cloud Gateway, Eureka Client).
- **Tâche 1.4** : Créer le projet `room-service` (Spring Web, Spring Data JPA, H2/MySQL, Eureka Client, Config Client, Kafka, Actuator).
- **Tâche 1.5** : Créer le projet `member-service` (Spring Web, Spring Data JPA, H2/MySQL, Eureka Client, Config Client, Kafka, Actuator).
- **Tâche 1.6** : Créer le projet `reservation-service` (Spring Web, Spring Data JPA, H2/MySQL, Eureka Client, Config Client, Kafka, Actuator).

## Étape 2 : Implémentation des Microservices (Logique Métier & REST)

### 2.a Room Service
- **Tâche 2.a.1** : Créer l'entité `Room` et l'énumération `RoomType` (OPEN_SPACE, MEETING_ROOM, PRIVATE_OFFICE).
- **Tâche 2.a.2** : Implémenter le CRUD complet pour `Room`.
- **Tâche 2.a.3** : Gérer la disponibilité : une salle ne peut accueillir qu'une seule réservation sur un créneau donné (indisponible tant que le créneau est en cours). Redevient disponible à la fin ou annulation de la réservation. (Sera partiellement géré en collaboration avec le Reservation Service).

### 2.b Member Service
- **Tâche 2.b.1** : Créer l'entité `Member` et l'énumération `SubscriptionType` (BASIC, PRO, ENTERPRISE).
- **Tâche 2.b.2** : Implémenter le CRUD complet pour `Member`.
- **Tâche 2.b.3** : Règles de quotas : BASIC=2 max, PRO=5 max, ENTERPRISE=10 max réservations actives.
- **Tâche 2.b.4** : Gérer la suspension : si le max est atteint, `suspended` = `true`. Ne peut plus réserver.

### 2.c Reservation Service
- **Tâche 2.c.1** : Créer l'entité `Reservation` et l'énumération `ReservationStatus` (CONFIRMED, CANCELLED, COMPLETED).
- **Tâche 2.c.2** : Lors de la création, vérifier par appel REST synchrone : la salle est disponible (via Room Service) ET le membre n'est pas suspendu (via Member Service).
- **Tâche 2.c.3** : Enregistrer la réservation avec statut CONFIRMED.
- **Tâche 2.c.4** : Permettre l'annulation (statut CANCELLED) et la complétion (statut COMPLETED).

## Étape 3 : Intégration de Kafka (Événements Asynchrones)
- **Tâche 3.1** : *Suppression d'une salle* : Room Service émet un événement. Reservation Service écoute et annule (statut CANCELLED) toutes les réservations CONFIRMED associées.
- **Tâche 3.2** : *Suppression d'un membre* : Member Service émet un événement. Reservation Service écoute et supprime (delete) toutes les réservations associées.
- **Tâche 3.3** : *Création d'une réservation* : Si le membre atteint son quota max de réservations actives, publier un événement. Member Service écoute et met `suspended = true`.
- **Tâche 3.4** : *Annulation ou complétion* : Si le membre était suspendu et repasse sous son quota, publier un événement. Member Service écoute et met `suspended = false`.

## Étape 4 : Design Pattern (Reservation Service)
- **Tâche 4.1** : Choisir et implémenter un Design Pattern pertinent (ex: Builder pour la construction/validation ou State pour le cycle de vie).
- **Tâche 4.2** : Rédiger le fichier `DESIGN_PATTERN.md` à la racine pour justifier le choix.

## Étape 5 : Documentation Swagger
- **Tâche 5.1** : Documenter les endpoints avec `springdoc-openapi` dans les 3 microservices.

## Étape 6 : Préparation des Livrables et Tests
- **Tâche 6.1** : Mettre en place un `docker-compose.yml` (Kafka, Zookeeper/KRaft, bases de données si besoin).
- **Tâche 6.2** : Rédiger le `README.md` avec instructions de lancement.
- **Tâche 6.3** : Vérifier que tout le code source, README.md, et DESIGN_PATTERN.md sont présents.
- **Tâche 6.4** : Tests CLI/Postman pour valider l'intégralité du scénario demandé (Étape 5 du TP).
