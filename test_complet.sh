#!/bin/bash

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

json_id() {
    echo "$1" | grep -o '"id":[0-9]*' | head -n 1 | grep -o '[0-9]*'
}

echo -e "${YELLOW}=== NETTOYAGE DES PORTS (Arrêt des anciens microservices) ===${NC}"
PORTS=(8888 8761 8080 8081 8082 8083)

for PORT in "${PORTS[@]}"; do
    PIDS=$(lsof -t -i:"$PORT")
    if [ -n "$PIDS" ]; then
        for PID in $PIDS; do
            echo "Arrêt du processus $PID sur le port $PORT..."
            kill -9 "$PID" 2>/dev/null
        done
    else
        echo "Port $PORT est déjà libre."
    fi
done

echo -e "${YELLOW}Les ports sont libérés. Veuillez relancer vos microservices maintenant (et attendre qu'ils soient tous UP).${NC}"
echo -e "${YELLOW}Appuyez sur ENTRÉE une fois que TOUS les services ont redémarré avec succès pour lancer les tests...${NC}"
read

readarray -t TIMES < <(python3 - <<'PY'
from datetime import datetime, timedelta

now = datetime.now().replace(microsecond=0)
values = [
    now - timedelta(seconds=30),
    now + timedelta(minutes=10),
    now + timedelta(minutes=1),
    now + timedelta(minutes=11),
    now + timedelta(days=1),
    now + timedelta(days=1, hours=2),
    now + timedelta(days=2),
    now + timedelta(days=2, hours=2),
    now - timedelta(seconds=1),
    now + timedelta(seconds=8),
]

for value in values:
    print(value.isoformat())
PY
)

CURRENT_START=${TIMES[0]}
CURRENT_END=${TIMES[1]}
OVERLAP_START=${TIMES[2]}
OVERLAP_END=${TIMES[3]}
FUTURE_START_1=${TIMES[4]}
FUTURE_END_1=${TIMES[5]}
FUTURE_START_2=${TIMES[6]}
FUTURE_END_2=${TIMES[7]}
SHORT_START=${TIMES[8]}
SHORT_END=${TIMES[9]}

UNIQ=$(date +%s)

echo -e "${GREEN}=== DÉBUT DES TESTS DU TP MICROSERVICES ===${NC}"

echo -e "\n${GREEN}[SCÉNARIO 1] Créer plusieurs salles dans différentes villes avec des types variés${NC}"
ROOM1_RESPONSE=$(curl -sS -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Espace Paris", "city": "Paris", "capacity": 20, "type": "OPEN_SPACE", "hourlyRate": 10.0}')
echo "$ROOM1_RESPONSE"
ROOM1_ID=$(json_id "$ROOM1_RESPONSE")

ROOM2_RESPONSE=$(curl -sS -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Salle Réunion Lyon", "city": "Lyon", "capacity": 8, "type": "MEETING_ROOM", "hourlyRate": 25.0}')
echo "$ROOM2_RESPONSE"
ROOM2_ID=$(json_id "$ROOM2_RESPONSE")

ROOM3_RESPONSE=$(curl -sS -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Bureau Privé", "city": "Marseille", "capacity": 2, "type": "PRIVATE_OFFICE", "hourlyRate": 40.0}')
echo "$ROOM3_RESPONSE"
ROOM3_ID=$(json_id "$ROOM3_RESPONSE")

echo -e "\n${GREEN}[SCÉNARIO 2] Inscrire des membres avec des abonnements différents${NC}"
MEMBER1_RESPONSE=$(curl -sS -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d "{\"fullName\": \"Alice Basic\", \"email\": \"alice.${UNIQ}@test.com\", \"subscriptionType\": \"BASIC\"}")
echo "$MEMBER1_RESPONSE"
MEMBER1_ID=$(json_id "$MEMBER1_RESPONSE")

MEMBER2_RESPONSE=$(curl -sS -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d "{\"fullName\": \"Bob Pro\", \"email\": \"bob.${UNIQ}@test.com\", \"subscriptionType\": \"PRO\"}")
echo "$MEMBER2_RESPONSE"
MEMBER2_ID=$(json_id "$MEMBER2_RESPONSE")

echo -e "\n${GREEN}[SCÉNARIO 3] Réserver une salle sur un créneau en cours et vérifier que sa disponibilité change${NC}"
RES1_RESPONSE=$(curl -sS -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$CURRENT_START\", \"endDateTime\": \"$CURRENT_END\"}")
echo "$RES1_RESPONSE"
RES1_ID=$(json_id "$RES1_RESPONSE")

echo "Vérification disponibilité Salle $ROOM1_ID (doit être available: false car créneau en cours)..."
curl -sS http://localhost:8081/api/rooms/"$ROOM1_ID"

echo -e "\n${GREEN}[SCÉNARIO 4] Tenter une réservation sur une salle déjà occupée sur le même créneau (doit échouer)${NC}"
curl -sS -w "\nHTTP Code (doit être 500): %{http_code}\n" -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"$OVERLAP_START\", \"endDateTime\": \"$OVERLAP_END\"}"

echo -e "\n${GREEN}[SCÉNARIO 5] Atteindre le quota d'un membre BASIC (2 réservations actives) et vérifier la suspension${NC}"
RES2_RESPONSE=$(curl -sS -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM2_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$FUTURE_START_1\", \"endDateTime\": \"$FUTURE_END_1\"}")
echo "$RES2_RESPONSE"
RES2_ID=$(json_id "$RES2_RESPONSE")

echo "Attente de 3 secondes (Propagation Kafka pour la suspension)..."
sleep 3
curl -sS http://localhost:8082/api/members/"$MEMBER1_ID"

echo -e "\nTentative d'une 3ème réservation par le membre BASIC (doit échouer car suspendu)..."
curl -sS -w "\nHTTP Code (doit être 500): %{http_code}\n" -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$FUTURE_START_2\", \"endDateTime\": \"$FUTURE_END_2\"}"

echo -e "\n${GREEN}[SCÉNARIO 6] Annuler une réservation et vérifier que le membre est désuspendu${NC}"
curl -sS -X PUT http://localhost:8083/api/reservations/"$RES1_ID"/cancel
sleep 3
curl -sS http://localhost:8082/api/members/"$MEMBER1_ID"
echo
curl -sS http://localhost:8081/api/rooms/"$ROOM1_ID"

echo -e "\n${GREEN}[SCÉNARIO 7] Supprimer une salle et vérifier la propagation Kafka sur les réservations${NC}"
curl -sS -X DELETE http://localhost:8081/api/rooms/"$ROOM2_ID"
sleep 3
curl -sS http://localhost:8083/api/reservations/"$RES2_ID"

echo -e "\n${GREEN}[SCÉNARIO 8] Supprimer un membre et vérifier la propagation Kafka${NC}"
RES3_RESPONSE=$(curl -sS -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"$CURRENT_START\", \"endDateTime\": \"$CURRENT_END\"}")
echo "$RES3_RESPONSE"

curl -sS -X DELETE http://localhost:8082/api/members/"$MEMBER2_ID"
sleep 3
curl -sS http://localhost:8081/api/rooms/"$ROOM3_ID"

echo -e "\n${GREEN}[SCÉNARIO 9] Laisser une réservation arriver à échéance et vérifier le passage à COMPLETED${NC}"
ROOM4_RESPONSE=$(curl -sS -X POST http://localhost:8081/api/rooms \
-H "Content-Type: application/json" \
-d '{"name": "Salle Chrono", "city": "Paris", "capacity": 4, "type": "MEETING_ROOM", "hourlyRate": 18.0}')
ROOM4_ID=$(json_id "$ROOM4_RESPONSE")
echo "$ROOM4_RESPONSE"

MEMBER3_RESPONSE=$(curl -sS -X POST http://localhost:8082/api/members \
-H "Content-Type: application/json" \
-d "{\"fullName\": \"Charlie Enterprise\", \"email\": \"charlie.${UNIQ}@test.com\", \"subscriptionType\": \"ENTERPRISE\"}")
MEMBER3_ID=$(json_id "$MEMBER3_RESPONSE")
echo "$MEMBER3_RESPONSE"

SHORT_RES_RESPONSE=$(curl -sS -X POST http://localhost:8083/api/reservations \
-H "Content-Type: application/json" \
-d "{\"roomId\": $ROOM4_ID, \"memberId\": $MEMBER3_ID, \"startDateTime\": \"$SHORT_START\", \"endDateTime\": \"$SHORT_END\"}")
SHORT_RES_ID=$(json_id "$SHORT_RES_RESPONSE")
echo "$SHORT_RES_RESPONSE"

echo "Disponibilité immédiate de la salle (doit être false tant que le créneau est en cours)..."
curl -sS http://localhost:8081/api/rooms/"$ROOM4_ID"

echo "Attente de 10 secondes pour laisser l'échéance être détectée..."
sleep 10

echo "Réservation courte (doit être COMPLETED)..."
curl -sS http://localhost:8083/api/reservations/"$SHORT_RES_ID"
echo
echo "Salle courte (doit être redevenue available: true)..."
curl -sS http://localhost:8081/api/rooms/"$ROOM4_ID"

echo -e "\n${GREEN}=== FIN DES TESTS ===${NC}"
