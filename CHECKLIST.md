# Checklist de Suivi de Projet - TP Architecture Microservices

## Étape 1 : Infrastructure Spring Boot
- [x] Générer `config-server` avec Spring Cloud Config
- [x] Générer `discovery-server` avec Eureka Server
- [x] Générer `api-gateway` avec Gateway et Eureka Client
- [x] Générer `room-service` avec Web, JPA, H2, Eureka, Config, Kafka, Actuator
- [x] Générer `member-service` avec Web, JPA, H2, Eureka, Config, Kafka, Actuator
- [x] Générer `reservation-service` avec Web, JPA, H2, Eureka, Config, Kafka, Actuator
- [x] Mettre en place le `docker-compose.yml` pour Kafka

## Étape 2 : Microservices & Logique Métier

### Room Service
- [x] Entité `Room` et enum `RoomType`
- [x] Repository et API CRUD de base
- [x] Endpoint de consultation de la disponibilité pour un créneau (pour le Reservation Service)

### Member Service
- [x] Entité `Member` et enum `SubscriptionType`
- [x] Repository et API CRUD de base
- [x] Endpoint de consultation des détails du membre (statut suspendu, etc.)

### Reservation Service
- [x] Entité `Reservation` et enum `ReservationStatus`
- [x] Repository et API
- [x] Client REST (Feign ou RestTemplate) vers Room Service
- [x] Client REST (Feign ou RestTemplate) vers Member Service
- [x] Logique de création de réservation (vérification synchrone disponibilité + membre non suspendu)
- [x] Logique d'annulation (CANCELLED)
- [x] Logique de complétion (COMPLETED)

## Étape 3 : Kafka (Asynchrone)
- [x] **Suppression Salle** : Émission depuis `room-service`
- [x] **Suppression Salle** : Écoute et Annulation (CANCELLED) dans `reservation-service`
- [x] **Suppression Membre** : Émission depuis `member-service`
- [x] **Suppression Membre** : Écoute et Suppression (DELETE) dans `reservation-service`
- [x] **Création Réservation** : Calcul des quotas dans `reservation-service` et émission d'événement de suspension si max atteint
- [x] **Création Réservation** : Écoute dans `member-service` et mise à jour `suspended = true`
- [x] **Annulation/Complétion** : Calcul des quotas dans `reservation-service` et émission d'événement de désuspension si repasse sous max
- [x] **Annulation/Complétion** : Écoute dans `member-service` et mise à jour `suspended = false`

## Étape 4 : Design Pattern
- [x] Implémentation du Design Pattern dans `reservation-service`
- [x] Rédaction du `DESIGN_PATTERN.md` à la racine

## Étape 5 : Swagger
- [x] Dépendance `springdoc-openapi` dans `room-service`
- [x] Dépendance `springdoc-openapi` dans `member-service`
- [x] Dépendance `springdoc-openapi` dans `reservation-service`

## Étape 6 : Livrables & Validation Finale
- [x] Tests de création/CRUD (Salles, Membres)
- [x] Test réservation succès
- [x] Test réservation échec (salle occupée, même créneau)
- [x] Test atteindre le quota (BASIC=2) -> vérification `suspended = true`
- [x] Test annulation -> vérification `suspended = false`
- [x] Test suppression salle -> vérification réservations annulées
- [x] Test suppression membre -> vérification réservations supprimées
- [x] Rédaction complète du `README.md` (instructions de lancement et exemples)
- [x] Nettoyage du code et préparation pour l'envoi
