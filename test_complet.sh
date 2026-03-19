#!/bin/bash

# Configuration des couleurs pour un affichage plus lisible
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== NETTOYAGE DES PORTS (Arrêt des anciens microservices) ===${NC}"
# Liste des ports utilisés par nos microservices
PORTS=(8888 8761 8080 8081 8082 8083)

for PORT in "${PORTS[@]}"; do
    # Cherche TOUS les PID des processus qui écoutent sur le port (en format tableau si plusieurs)
    PIDS=$(lsof -t -i:$PORT)
    if [ ! -z "$PIDS" ]; then
        # On itère sur chaque PID trouvé pour s'assurer de bien tous les kill
        for PID in $PIDS; do
             echo -e "Arrêt du processus $PID sur le port $PORT..."
             kill -9 $PID 2>/dev/null
        done
    else
        echo -e "Port $PORT est déjà libre."
    fi
done

echo -e "${YELLOW}Les ports sont libérés. Veuillez relancer vos microservices maintenant (et attendre qu'ils soient tous UP).${NC}"
echo -e "${YELLOW}Appuyez sur ENTRÉE une fois que TOUS les services ont redémarré avec succès pour lancer les tests...${NC}"
read

echo -e "${GREEN}=== DÉBUT DES TESTS DU TP MICROSERVICES ===${NC}"

# =====================================================================
# ETAPE 1 : PREPARATION DES DONNÉES
# =====================================================================

echo -e "\n${GREEN}[SCÉNARIO 1] Créer plusieurs salles dans différentes villes avec des types variés${NC}"
echo "Création Salle 1 (OPEN_SPACE à Paris)..."
ROOM1_RESPONSE=$(curl -s -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Espace Paris", "city": "Paris", "capacity": 20, "type": "OPEN_SPACE", "hourlyRate": 10.0}')
echo $ROOM1_RESPONSE
ROOM1_ID=$(echo $ROOM1_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

echo -e "\nCréation Salle 2 (MEETING_ROOM à Lyon)..."
ROOM2_RESPONSE=$(curl -s -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Salle Réunion Lyon", "city": "Lyon", "capacity": 8, "type": "MEETING_ROOM", "hourlyRate": 25.0}')
echo $ROOM2_RESPONSE
ROOM2_ID=$(echo $ROOM2_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

echo -e "\nCréation Salle 3 (PRIVATE_OFFICE à Marseille)..."
ROOM3_RESPONSE=$(curl -s -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Bureau Privé", "city": "Marseille", "capacity": 2, "type": "PRIVATE_OFFICE", "hourlyRate": 40.0}')
echo $ROOM3_RESPONSE
ROOM3_ID=$(echo $ROOM3_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

echo -e "\n${GREEN}[SCÉNARIO 2] Inscrire des membres avec des abonnements différents${NC}"
echo "Création Membre 1 (BASIC - Max 2 résa)..."
MEMBER1_RESPONSE=$(curl -s -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d '{"fullName": "Alice Basic", "email": "alice@test.com", "subscriptionType": "BASIC"}')
echo $MEMBER1_RESPONSE
MEMBER1_ID=$(echo $MEMBER1_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

echo -e "\nCréation Membre 2 (PRO - Max 5 résa)..."
MEMBER2_RESPONSE=$(curl -s -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d '{"fullName": "Bob Pro", "email": "bob@test.com", "subscriptionType": "PRO"}')
echo $MEMBER2_RESPONSE
MEMBER2_ID=$(echo $MEMBER2_RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

# =====================================================================
# ETAPE 2 : RESERVATIONS ET DISPONIBILITES
# =====================================================================

echo -e "\n${GREEN}[SCÉNARIO 3] Réserver une salle et vérifier que sa disponibilité change${NC}"
echo "Le Membre $MEMBER1_ID réserve la Salle $ROOM1_ID..."
RES1_RESPONSE=$(curl -s -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"2024-12-01T10:00:00\", \"endDateTime\": \"2024-12-01T12:00:00\"}")
echo $RES1_RESPONSE
RES1_ID=$(echo $RES1_RESPONSE | grep -o '"id":[0-9]*' | head -n 1 | grep -o '[0-9]*')

echo "Vérification disponibilité Salle $ROOM1_ID (doit être available: false)..."
curl -s -X GET http://localhost:8081/api/rooms/$ROOM1_ID | grep -o '"available":(true|false)' || curl -s -X GET http://localhost:8081/api/rooms/$ROOM1_ID

echo -e "\n${GREEN}[SCÉNARIO 4] Tenter une réservation sur une salle déjà occupée sur le même créneau (doit échouer)${NC}"
echo "Le Membre $MEMBER2_ID tente de réserver la Salle $ROOM1_ID sur le même créneau..."
curl -s -w "\nHTTP Code (doit être 500): %{http_code}\n" -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"2024-12-01T11:00:00\", \"endDateTime\": \"2024-12-01T13:00:00\"}"

# =====================================================================
# ETAPE 3 : GESTION DES QUOTAS ET SUSPENSION (KAFKA)
# =====================================================================

echo -e "\n${GREEN}[SCÉNARIO 5] Atteindre le quota d'un membre BASIC (2 réservations) et vérifier la suspension${NC}"
echo "Le Membre $MEMBER1_ID (BASIC) réserve sa 2ème salle (Salle $ROOM2_ID)..."
RES2_RESPONSE=$(curl -s -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM2_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"2024-12-02T14:00:00\", \"endDateTime\": \"2024-12-02T16:00:00\"}")
echo $RES2_RESPONSE
RES2_ID=$(echo $RES2_RESPONSE | grep -o '"id":[0-9]*' | head -n 1 | grep -o '[0-9]*')

echo "Attente de 10 secondes (Propagation Kafka pour la suspension)..."
sleep 10

echo "Vérification du Membre $MEMBER1_ID (doit être suspended: true)..."
curl -s -X GET http://localhost:8082/api/members/$MEMBER1_ID

echo -e "\nTentative d'une 3ème réservation par le Membre $MEMBER1_ID (doit échouer car suspendu)..."
curl -s -w "\nHTTP Code (doit être 500): %{http_code}\n" -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"2024-12-03T10:00:00\", \"endDateTime\": \"2024-12-03T12:00:00\"}"

echo -e "\n${GREEN}[SCÉNARIO 6] Annuler une réservation et vérifier que le membre est désuspendu${NC}"
echo "Annulation de la 1ère réservation (ID: $RES1_ID) du Membre $MEMBER1_ID..."
curl -s -X PUT http://localhost:8083/api/reservations/$RES1_ID/cancel

echo -e "\nAttente de 10 secondes (Propagation Kafka pour la désuspension)..."
sleep 10

echo "Vérification du Membre $MEMBER1_ID (doit être redevenu suspended: false)..."
curl -s -X GET http://localhost:8082/api/members/$MEMBER1_ID

echo -e "\nVérification de la Salle $ROOM1_ID (doit être redevenue available: true suite à l'annulation)..."
curl -s -X GET http://localhost:8081/api/rooms/$ROOM1_ID

# =====================================================================
# ETAPE 4 : SUPPRESSIONS EN CASCADE (KAFKA)
# =====================================================================

echo -e "\n${GREEN}[SCÉNARIO 7] Supprimer une salle et vérifier la propagation Kafka sur les réservations${NC}"
echo "La Salle $ROOM2_ID est actuellement réservée par la Réservation $RES2_ID."
echo "Suppression de la Salle $ROOM2_ID..."
curl -s -X DELETE http://localhost:8081/api/rooms/$ROOM2_ID

echo "Attente de 10 secondes (Propagation Kafka pour annuler la réservation associée)..."
sleep 10

echo "Vérification de la Réservation $RES2_ID (doit être CANCELLED)..."
curl -s -X GET http://localhost:8083/api/reservations/$RES2_ID

echo -e "\n${GREEN}[SCÉNARIO 8] Supprimer un membre et vérifier la propagation Kafka${NC}"
echo "Le Membre $MEMBER2_ID fait une réservation sur la Salle $ROOM3_ID..."
RES3_RESPONSE=$(curl -s -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"2024-12-10T09:00:00\", \"endDateTime\": \"2024-12-10T18:00:00\"}")
echo $RES3_RESPONSE

echo "Suppression du Membre $MEMBER2_ID..."
curl -s -X DELETE http://localhost:8082/api/members/$MEMBER2_ID

echo "Attente de 10 secondes (Propagation Kafka pour supprimer la réservation et libérer la salle)..."
sleep 10

echo "Vérification de la Salle $ROOM3_ID (doit être redevenue available: true)..."
curl -s -X GET http://localhost:8081/api/rooms/$ROOM3_ID

echo -e "\n${GREEN}=== FIN DES TESTS ===${NC}"