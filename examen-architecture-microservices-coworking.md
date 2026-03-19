# TP Architecture Logicielle — Microservices

## Gestion d'une plateforme de réservation de salles de coworking

**Niveau** : M2 Architecture et Solutions
**Durée** : 1 journée
**Technologies** : Spring Boot, Spring Cloud, Apache Kafka

---

### Contexte

Vous êtes mandaté par une startup qui propose des espaces de coworking dans plusieurs villes. La plateforme doit permettre aux utilisateurs de réserver des salles, de consulter leur disponibilité en temps réel et de gérer leurs abonnements. L'architecture doit être construite en microservices avec Spring Boot et Spring Cloud.

### Architecture cible

```
                        API Gateway
                            │
              ┌─────────────┼──────────────┐
              │             │              │
         Room Service   Member Service  Reservation Service
              │             │              │
              └─────────────┼──────────────┘
                            │
                     Apache Kafka
```

**Infrastructure commune :**

- **Config Server** : configuration centralisée
- **Discovery Server** : Eureka
- **API Gateway** : routage et point d'entrée unique

---

### Étape 1 — Créer les projets Spring Boot

Utilisez Spring Initializr pour générer les projets suivants :

- `config-server`
- `discovery-server`
- `api-gateway`
- `room-service`
- `member-service`
- `reservation-service`

Dépendances nécessaires : Spring Web, Spring Data JPA, Spring Boot Actuator, Spring Cloud Config, Eureka Server/Client, Spring for Apache Kafka, H2 ou MySQL Driver.

---

### Étape 2 — Implémenter les microservices

#### a) Room Service

Entité `Room` :

```java
@Entity
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String city;
    private Integer capacity;
    private RoomType type; // OPEN_SPACE, MEETING_ROOM, PRIVATE_OFFICE
    private BigDecimal hourlyRate;
    private boolean available;
}
```

Règles métier :

- CRUD complet sur les salles.
- Une salle ne peut accueillir qu'une seule réservation sur un créneau donné. Tant que le créneau est en cours, la salle est indisponible.
- Quand une réservation se termine (date de fin atteinte ou annulation), la salle redevient disponible.

#### b) Member Service

Entité `Member` :

```java
@Entity
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String email;
    private SubscriptionType subscriptionType; // BASIC, PRO, ENTERPRISE
    private boolean suspended;
    private Integer maxConcurrentBookings;
}
```

Règles métier :

- CRUD complet sur les membres.
- Un membre BASIC peut avoir au maximum 2 réservations actives simultanées, un PRO 5, un ENTERPRISE 10.
- Quand le nombre maximum est atteint, le champ `suspended` passe à `true`. Le membre ne peut plus réserver tant qu'une réservation n'est pas libérée.

#### c) Reservation Service

Entité `Reservation` :

```java
@Entity
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long roomId;
    private Long memberId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private ReservationStatus status; // CONFIRMED, CANCELLED, COMPLETED
}
```

Règles métier :

- Avant de créer une réservation, vérifier que la salle est disponible sur le créneau demandé (appel REST vers Room Service) et que le membre n'est pas suspendu (appel REST vers Member Service).
- Enregistrer la réservation avec le statut CONFIRMED.
- Permettre l'annulation et le marquage comme COMPLETED.

---

### Étape 3 — Intégrer Kafka

Kafka doit gérer les mécanismes asynchrones suivants :

- **Suppression d'une salle** : toutes les réservations CONFIRMED associées à cette salle doivent être automatiquement annulées (statut CANCELLED).
- **Suppression d'un membre** : toutes les réservations associées à ce membre doivent être supprimées.
- **Création d'une réservation** : si le membre atteint son quota maximum de réservations actives, un événement est publié pour mettre à jour le champ `suspended` du membre à `true`.
- **Annulation ou complétion d'une réservation** : si le membre était suspendu et repasse en dessous de son quota, un événement est publié pour remettre `suspended` à `false`.

---

### Étape 4 — Design Pattern

Mettez en place un Design Pattern adapté dans le microservice Reservation.

Vous êtes libre du choix (creational, behavioral ou structural). Justifiez votre choix dans un commentaire ou un fichier `DESIGN_PATTERN.md` à la racine du projet.

Quelques pistes de réflexion :

- Le cycle de vie d'une réservation (CONFIRMED → COMPLETED / CANCELLED) se prête-t-il à un pattern comportemental ?
- La construction d'une réservation avec ses validations multiples se prête-t-elle à un pattern créationnel ?

---

### Étape 5 — Tester l'ensemble

Testez avec Postman les scénarios suivants :

- Créer plusieurs salles dans différentes villes avec des types variés.
- Inscrire des membres avec des abonnements différents (BASIC, PRO, ENTERPRISE).
- Réserver une salle et vérifier que sa disponibilité change.
- Tenter une réservation sur une salle déjà occupée sur le même créneau (doit échouer).
- Atteindre le quota d'un membre BASIC (2 réservations) et vérifier que `suspended` passe à `true`.
- Annuler une réservation et vérifier que le membre est désuspendu.
- Supprimer une salle et vérifier la propagation Kafka sur les réservations.
- Supprimer un membre et vérifier la propagation Kafka.

---

### Étape 6 — Documentation

Documentez chaque microservice avec Swagger (springdoc-openapi).

---

### Étape 7 — Livrable

Envoyez votre dépôt git contenant :

- L'ensemble du code source nécessaire au fonctionnement de l'application
- Un `README.md` avec les instructions de lancement
- Le fichier `DESIGN_PATTERN.md` justifiant votre choix

---

### Barème

| Critère | Points |
|---------|--------|
| Infrastructure (Config Server, Eureka, Gateway) fonctionnelle | /3 |
| Room Service — CRUD + règles de disponibilité | /3 |
| Member Service — CRUD + gestion des quotas et suspension | /3 |
| Reservation Service — création avec validations cross-service | /3 |
| Kafka — propagation suppression salle et membre | /2 |
| Kafka — gestion suspension/désuspension du membre | /2 |
| Design Pattern pertinent et justifié | /2 |
| Documentation Swagger | /1 |
| Qualité du code et structure du projet | /1 |
| **Total** | **/20** |
