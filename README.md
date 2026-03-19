# Coworking Microservices Platform

Une architecture en microservices (Spring Boot / Spring Cloud / Kafka) pour la gestion d'une plateforme de réservation de salles de coworking, répondant aux exigences du TP Architecture Logicielle.

## Architecture

Le projet est structuré autour des microservices suivants :
* **common-events** : module Maven partagé contenant les événements Kafka communs (`RoomDeletedEvent`, `MemberDeletedEvent`, `MemberSuspensionEvent`).
* **Config Server** (`:8888`) : Serveur de configuration centralisé (utilise un profil `native` pointant vers le dossier `classpath:/config` pour le TP).
* **Discovery Server** (`:8761`) : Annuaire Eureka pour la découverte de services.
* **API Gateway** (`:8080`) : Point d'entrée unique et routage.
* **Room Service** (`:8081`) : Gestion des salles et de leur disponibilité.
* **Member Service** (`:8082`) : Gestion des membres, de leurs abonnements (quotas) et suspensions.
* **Reservation Service** (`:8083`) : Gestion des réservations (avec State Pattern et validation distribuée).

**Infrastructure asynchrone :**
* **Apache Kafka & Zookeeper** (via Docker Compose) pour la communication asynchrone (suppressions en cascade, gestion dynamique des suspensions basées sur les quotas).

---

## Prérequis

- **Java 17**
- **Maven** (3.8+)
- **Docker & Docker Compose** (pour Kafka)

---

## Instructions de lancement

### 1. Démarrer Kafka
À la racine du projet, lancez l'infrastructure Kafka :
```bash
docker-compose up -d
```
*Attendez quelques secondes que Kafka soit pleinement opérationnel.*

### 2. Compiler le projet

Depuis la racine :
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests
```

Si votre machine utilise déjà Java 17 par défaut, les exports ne sont pas nécessaires. En revanche, avec un JDK plus récent, le build peut devenir instable.

### 3. Démarrer les Microservices
Vous pouvez lancer chaque service individuellement via votre IDE, ou utiliser Maven dans des terminaux séparés, **strictement dans l'ordre suivant** :

1. **Config Server**
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd config-server && mvn spring-boot:run
   ```
2. **Discovery Server** (attendre qu'il soit démarré sur `localhost:8761`)
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd discovery-server && mvn spring-boot:run
   ```
3. **API Gateway**
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd api-gateway && mvn spring-boot:run
   ```
4. **Room Service**, **Member Service**, **Reservation Service** (l'ordre importe peu ici)
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd room-service && mvn spring-boot:run
   # Dans un autre terminal :
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd member-service && mvn spring-boot:run
   # Dans un autre terminal :
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   cd reservation-service && mvn spring-boot:run
   ```

*Vérifiez sur [http://localhost:8761](http://localhost:8761) que tous les services (GATEWAY, ROOM-SERVICE, MEMBER-SERVICE, RESERVATION-SERVICE) sont bien enregistrés (UP).*

---

## Swagger / OpenAPI

Les documentations de chaque API sont disponibles :
- **Room Service** : http://localhost:8081/swagger-ui/index.html
- **Member Service** : http://localhost:8082/swagger-ui/index.html
- **Reservation Service** : http://localhost:8083/swagger-ui/index.html

Vous pouvez également accéder aux services via la **Gateway** :
- Ex: `http://localhost:8080/room-service/api/rooms`

---

## Exemples d'utilisation (Scénarios de test CLI / cURL)

Voici les commandes pour tester le workflow complet du TP.

### 1. Créer une salle
```bash
curl -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Salle Alpha", "city": "Paris", "capacity": 10, "type": "MEETING_ROOM", "hourlyRate": 25.0}'
```
*Notez l'ID retourné (ex: 1).*

### 2. Inscrire un membre (Abonnement BASIC = 2 résas max)
```bash
curl -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d '{"fullName": "John Doe", "email": "john@doe.com", "subscriptionType": "BASIC"}'
```
*Notez l'ID retourné (ex: 1).*

### 3. Réserver une salle (Succès)

Important : les services manipulent des `LocalDateTime` sans fuseau horaire. Utilisez donc des dates/heures locales cohérentes avec votre machine, ou lancez directement `./test_complet.sh` qui les génère automatiquement.

```bash
curl -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d '{"roomId": 1, "memberId": 1, "startDateTime": "2030-05-10T10:00:00", "endDateTime": "2030-05-10T12:00:00"}'
```
*Vérifiez sur le Room Service que la salle n'est plus disponible :*
```bash
curl -X GET http://localhost:8081/api/rooms/1
```

### 4. Tenter une réservation sur la même salle (Échec attendu)
```bash
curl -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d '{"roomId": 1, "memberId": 1, "startDateTime": "2030-05-10T11:00:00", "endDateTime": "2030-05-10T13:00:00"}'
```
*(Doit retourner une erreur 500 : "Room is not available...")*

### 5. Créer une 2ème salle et réserver (Atteinte du quota BASIC)
```bash
# Créer salle 2
curl -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Salle Beta", "city": "Lyon", "capacity": 5, "type": "PRIVATE_OFFICE", "hourlyRate": 15.0}'

# Réserver salle 2
curl -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d '{"roomId": 2, "memberId": 1, "startDateTime": "2030-05-11T10:00:00", "endDateTime": "2030-05-11T12:00:00"}'
```
*À ce stade, le membre 1 a atteint son quota (2 réservations). Vérifions qu'il est suspendu (via l'event Kafka) :*
```bash
curl -X GET http://localhost:8082/api/members/1
# "suspended" doit être true.
```

### 6. Annuler une réservation (Désuspension)
```bash
# Annuler la réservation 1 (sur la salle 1)
curl -X PUT http://localhost:8083/api/reservations/1/cancel
```
*La salle 1 redevient disponible et le membre 1 redescend à 1 réservation active, il est désuspendu via Kafka :*
```bash
curl -X GET http://localhost:8082/api/members/1
# "suspended" doit être redevenu false.
```

### 7. Supprimer une salle (Annulation en cascade via Kafka)
```bash
curl -X DELETE http://localhost:8081/api/rooms/2
```
*La réservation 2 était sur la salle 2. Vérifions qu'elle a été annulée :*
```bash
curl -X GET http://localhost:8083/api/reservations/2
# "status" doit être "CANCELLED"
```

### Script de vérification

Le script [`test_complet.sh`](/Users/adenankhachnane/Downloads/examen_adenan_khachnane_m1/test_complet.sh) couvre les scénarios du sujet avec des dates locales générées dynamiquement, y compris le passage automatique d'une réservation à `COMPLETED`.

Attention : ce script libère d'abord les ports `8888`, `8761`, `8080`, `8081`, `8082`, `8083`, puis te demande de relancer les services avant de poursuivre.

## Design Pattern

Le choix du pattern utilisé dans `reservation-service` est documenté dans [`DESIGN_PATTERN.md`](/Users/adenankhachnane/Downloads/examen_adenan_khachnane_m1/DESIGN_PATTERN.md).
