#!/usr/bin/env bash
set -euo pipefail

: "${ADMIN_USERNAME:?source .env first}"
: "${ADMIN_PASSWORD:?source .env first}"
: "${INVENTORY_API_KEY:?source .env first}"

REST=http://localhost:8081/api/v1
SOAP=http://localhost:8082/services/inventory
SUFFIX=$(date +%s | tail -c 6)
MACHINE_ID="MACHINE-DEMO-${SUFFIX}"
TECH_EMAIL="demo-${SUFFIX}@example.com"

j() { python3 -c 'import sys,json;print(json.load(sys.stdin).get("'"$1"'",""))'; }
step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$1"; }

echo "Machine:    $MACHINE_ID"
echo "Technician: $TECH_EMAIL"

step "Health checks"
curl -s -o /dev/null -w "  rest: %{http_code}\n" http://localhost:8081/actuator/health
curl -s -o /dev/null -w "  soap: %{http_code}\n" http://localhost:8082/services/inventory.wsdl

step "1. Login as admin"
TOKEN=$(curl -s -X POST "$REST/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
AUTH="Authorization: Bearer $TOKEN"
echo "  token: ${TOKEN:0:20}..."

step "2. Create machine $MACHINE_ID"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X POST "$REST/machines" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"machineId\":\"$MACHINE_ID\",\"name\":\"Demo Machine\",\"location\":\"Lab\",\"model\":\"D-1\",\"status\":\"RUNNING\"}"

step "3. Create technician"
TECH_ID=$(curl -s -X POST "$REST/technicians" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Demo Tech\",\"email\":\"$TECH_EMAIL\",\"active\":true}" | j id)
echo "  technicianId: $TECH_ID"

step "4. Create alert"
ALERT_ID=$(curl -s -X POST "$REST/alerts" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"machineId\":\"$MACHINE_ID\",\"metricType\":\"TEMPERATURE\",\"severity\":\"CRITICAL\",\"observedValue\":91.4,\"thresholdValue\":85.0,\"message\":\"Temperature exceeded threshold\",\"occurredAt\":\"$(date -u +%FT%TZ)\",\"sourceEventId\":\"EV-DEMO-$SUFFIX\"}" \
  | j id)
echo "  alertId: $ALERT_ID"

step "5. Create work order"
WO_ID=$(curl -s -X POST "$REST/work-orders" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"machineId\":\"$MACHINE_ID\",\"alertId\":\"$ALERT_ID\",\"title\":\"Inspect cooling system\",\"description\":\"Investigate high temp\",\"priority\":\"HIGH\"}" \
  | j id)
echo "  workOrderId: $WO_ID"

step "6. OPEN → CHECKING_INVENTORY"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X PATCH "$REST/work-orders/$WO_ID/status" \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"CHECKING_INVENTORY"}'

step "7. SOAP checkStock(PART-001, 2)"
CHECK=$(curl -s -X POST "$SOAP" -H 'Content-Type: text/xml' -H "X-Inventory-Api-Key: $INVENTORY_API_KEY" \
  --data-binary "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:inv=\"http://soaproject.com/inventory\"><soapenv:Body><inv:checkStockRequest><inv:partId>PART-001</inv:partId><inv:quantity>2</inv:quantity></inv:checkStockRequest></soapenv:Body></soapenv:Envelope>")
AVAILABLE=$(echo "$CHECK" | python3 -c 'import sys,re;print(re.search(r"available>([^<]+)", sys.stdin.read()).group(1))')
echo "  available=$AVAILABLE"

step "8. SOAP reservePart"
RESERVE=$(curl -s -X POST "$SOAP" -H 'Content-Type: text/xml' -H "X-Inventory-Api-Key: $INVENTORY_API_KEY" \
  --data-binary "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:inv=\"http://soaproject.com/inventory\"><soapenv:Body><inv:reservePartRequest><inv:requestId>REQ-DEMO-$SUFFIX</inv:requestId><inv:partId>PART-001</inv:partId><inv:quantity>2</inv:quantity><inv:workOrderId>$WO_ID</inv:workOrderId></inv:reservePartRequest></soapenv:Body></soapenv:Envelope>")
RESERVATION_ID=$(echo "$RESERVE" | python3 -c 'import sys,re;print(re.search(r"reservationId>([^<]+)", sys.stdin.read()).group(1))')
echo "  reservationId: $RESERVATION_ID"

step "9. PATCH /reservation → READY_FOR_MAINTENANCE"
curl -s -X PATCH "$REST/work-orders/$WO_ID/reservation" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"reservationId\":\"$RESERVATION_ID\",\"partId\":\"PART-001\",\"quantity\":2,\"status\":\"ACTIVE\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  status:",d["status"])'

step "10. Assign technician"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X PATCH "$REST/work-orders/$WO_ID/assign" -H "$AUTH" \
  -H 'Content-Type: application/json' -d "{\"technicianId\":\"$TECH_ID\"}"

step "11. → IN_PROGRESS"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X PATCH "$REST/work-orders/$WO_ID/status" \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'

step "12. → COMPLETED"
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X PATCH "$REST/work-orders/$WO_ID/status" \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"COMPLETED"}'

step "Final state"
curl -s "$REST/work-orders/$WO_ID" -H "$AUTH" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print("  status:",d["status"]);print("  completedAt:",d["completedAt"])'

printf '\n\033[1;32m✔ Workflow completed successfully\033[0m\n'
