#!/bin/bash

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

json_id() {
    echo "$1" | grep -o '"id":[0-9]*' | head -n 1 | grep -o '[0-9]*'
}

LAST_RESPONSE_BODY=""
LAST_RESPONSE_CODE=""

perform_json_request() {
    local method=$1
    local url=$2
    local data=${3:-}
    local tmp_file

    tmp_file=$(mktemp)

    if [ -n "$data" ]; then
        LAST_RESPONSE_CODE=$(curl -sS -o "$tmp_file" -w "%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null || echo "000")
    else
        LAST_RESPONSE_CODE=$(curl -sS -o "$tmp_file" -w "%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" 2>/dev/null || echo "000")
    fi

    LAST_RESPONSE_BODY=$(cat "$tmp_file" 2>/dev/null || true)
    rm -f "$tmp_file"
}

perform_json_request_with_retry() {
    local method=$1
    local url=$2
    local data=${3:-}
    local timeout=${4:-20}
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        perform_json_request "$method" "$url" "$data"

        if [ "$LAST_RESPONSE_CODE" != "500" ] && [ "$LAST_RESPONSE_CODE" != "503" ] && [ "$LAST_RESPONSE_CODE" != "000" ]; then
            return 0
        fi

        sleep 1
        elapsed=$((elapsed + 1))
    done

    return 1
}

wait_for_service() {
    local url=$1
    local timeout=${2:-30}
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        if curl -fsS "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    return 1
}

wait_for_body_contains() {
    local url=$1
    local expected=$2
    local timeout=${3:-30}
    local elapsed=0
    local body=""

    while [ "$elapsed" -lt "$timeout" ]; do
        body=$(curl -sS "$url" 2>/dev/null || true)
        if [[ "$body" == *"$expected"* ]]; then
            echo "$body"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    echo "$body"
    return 1
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

echo "Vérification de la disponibilité des services métier..."
wait_for_service http://localhost:8081/actuator/health 30 || echo "Room Service ne répond pas encore."
wait_for_service http://localhost:8082/actuator/health 30 || echo "Member Service ne répond pas encore."
wait_for_service http://localhost:8083/actuator/health 30 || echo "Reservation Service ne répond pas encore."

eval "$(python3 - <<'PY'
from datetime import datetime, timedelta

now = datetime.now().replace(microsecond=0)
values = {
    "CURRENT_START": now - timedelta(seconds=30),
    "CURRENT_END": now + timedelta(minutes=10),
    "OVERLAP_START": now + timedelta(minutes=1),
    "OVERLAP_END": now + timedelta(minutes=11),
    "FUTURE_START_1": now + timedelta(days=1),
    "FUTURE_END_1": now + timedelta(days=1, hours=2),
    "FUTURE_START_2": now + timedelta(days=2),
    "FUTURE_END_2": now + timedelta(days=2, hours=2),
}

for key, value in values.items():
    print(f'{key}="{value.isoformat()}"')
PY
)"

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
perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$CURRENT_START\", \"endDateTime\": \"$CURRENT_END\"}" 30
RES1_RESPONSE=$LAST_RESPONSE_BODY
echo "$RES1_RESPONSE"
RES1_ID=$(json_id "$RES1_RESPONSE")

echo "Vérification disponibilité Salle $ROOM1_ID (doit être available: false car créneau en cours)..."
wait_for_body_contains http://localhost:8081/api/rooms/"$ROOM1_ID" '"available":false' 10

echo -e "\n${GREEN}[SCÉNARIO 4] Tenter une réservation sur une salle déjà occupée sur le même créneau (doit échouer)${NC}"
perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM1_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"$OVERLAP_START\", \"endDateTime\": \"$OVERLAP_END\"}" 15
echo "$LAST_RESPONSE_BODY"
echo "HTTP Code (doit être 409): $LAST_RESPONSE_CODE"

echo -e "\n${GREEN}[SCÉNARIO 5] Atteindre le quota d'un membre BASIC (2 réservations actives) et vérifier la suspension${NC}"
perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM2_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$FUTURE_START_1\", \"endDateTime\": \"$FUTURE_END_1\"}" 15
RES2_RESPONSE=$LAST_RESPONSE_BODY
echo "$RES2_RESPONSE"
RES2_ID=$(json_id "$RES2_RESPONSE")

echo "Attente de la suspension du membre BASIC..."
wait_for_body_contains http://localhost:8082/api/members/"$MEMBER1_ID" '"suspended":true' 30

echo -e "\nTentative d'une 3ème réservation par le membre BASIC (doit échouer car suspendu)..."
perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER1_ID, \"startDateTime\": \"$FUTURE_START_2\", \"endDateTime\": \"$FUTURE_END_2\"}" 15
echo "$LAST_RESPONSE_BODY"
echo "HTTP Code (doit être 409): $LAST_RESPONSE_CODE"

echo -e "\n${GREEN}[SCÉNARIO 6] Annuler une réservation et vérifier que le membre est désuspendu${NC}"
curl -sS -X PUT http://localhost:8083/api/reservations/"$RES1_ID"/cancel
wait_for_body_contains http://localhost:8082/api/members/"$MEMBER1_ID" '"suspended":false' 30
echo
wait_for_body_contains http://localhost:8081/api/rooms/"$ROOM1_ID" '"available":true' 10

echo -e "\n${GREEN}[SCÉNARIO 7] Supprimer une salle et vérifier la propagation Kafka sur les réservations${NC}"
curl -sS -X DELETE http://localhost:8081/api/rooms/"$ROOM2_ID"
wait_for_body_contains http://localhost:8083/api/reservations/"$RES2_ID" '"status":"CANCELLED"' 30

echo -e "\n${GREEN}[SCÉNARIO 8] Supprimer un membre et vérifier la propagation Kafka${NC}"
perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM3_ID, \"memberId\": $MEMBER2_ID, \"startDateTime\": \"$CURRENT_START\", \"endDateTime\": \"$CURRENT_END\"}" 15
RES3_RESPONSE=$LAST_RESPONSE_BODY
echo "$RES3_RESPONSE"

curl -sS -X DELETE http://localhost:8082/api/members/"$MEMBER2_ID"
wait_for_body_contains http://localhost:8081/api/rooms/"$ROOM3_ID" '"available":true' 30

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

eval "$(python3 - <<'PY'
from datetime import datetime, timedelta

now = datetime.now().replace(microsecond=0)
print(f'SHORT_START="{(now - timedelta(seconds=1)).isoformat()}"')
print(f'SHORT_END="{(now + timedelta(seconds=8)).isoformat()}"')
PY
)"

perform_json_request_with_retry POST http://localhost:8083/api/reservations \
"{\"roomId\": $ROOM4_ID, \"memberId\": $MEMBER3_ID, \"startDateTime\": \"$SHORT_START\", \"endDateTime\": \"$SHORT_END\"}" 15
SHORT_RES_RESPONSE=$LAST_RESPONSE_BODY
SHORT_RES_ID=$(json_id "$SHORT_RES_RESPONSE")
echo "$SHORT_RES_RESPONSE"

echo "Disponibilité immédiate de la salle (doit être false tant que le créneau est en cours)..."
wait_for_body_contains http://localhost:8081/api/rooms/"$ROOM4_ID" '"available":false' 10

echo "Attente de 10 secondes pour laisser l'échéance être détectée..."
sleep 10

echo "Réservation courte (doit être COMPLETED)..."
wait_for_body_contains http://localhost:8083/api/reservations/"$SHORT_RES_ID" '"status":"COMPLETED"' 10
echo
echo "Salle courte (doit être redevenue available: true)..."
wait_for_body_contains http://localhost:8081/api/rooms/"$ROOM4_ID" '"available":true' 10

echo -e "\n${GREEN}=== FIN DES TESTS ===${NC}"
