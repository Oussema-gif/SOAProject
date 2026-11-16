# Integration Contract

Full DTOs: `openapi.yaml`. SOAP examples: `soap-examples.xml`. Postman: `postman-collection.json`.

## Endpoints
- REST: `http://localhost:8081/api/v1` — header `Authorization: Bearer <JWT>`
- SOAP: `http://localhost:8082/services/inventory` — header `X-Inventory-Api-Key: <key>`
- WSDL: `http://localhost:8082/services/inventory.wsdl` (public GET)

## Auth
POST /auth/login {username, password} → {accessToken, tokenType, expiresInSeconds, username, role}
Use `Authorization: Bearer <accessToken>` on every request. 401 → re-login. 403 → wrong role.

## Errors
All 4xx/5xx: {timestamp, status, error, message, path, details}
Codes: VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, INVALID_STATE_TRANSITION, INTERNAL_ERROR

## Alert deduplication
POST /alerts → 201 new OR 200 existing (same sourceEventId, OR same machineId+metricType while open). Safe to retry.

## Work-order states
OPEN → CHECKING_INVENTORY → READY_FOR_MAINTENANCE | WAITING_FOR_PARTS | PENDING_RESERVATION
READY_FOR_MAINTENANCE → IN_PROGRESS → COMPLETED
Any state → CANCELLED
IN_PROGRESS requires an assigned technician. Invalid transition → 409 INVALID_STATE_TRANSITION.

## Reservation
PATCH /work-orders/{id}/reservation {reservationId, partId, quantity, status?}
- ACTIVE (default): from CHECKING_INVENTORY or PENDING_RESERVATION → WO moves to READY_FOR_MAINTENANCE atomically. Idempotent.
- RELEASED: idempotent, works in any non-terminal state.
If SOAP succeeded but this PATCH fails → retry the PATCH; never re-reserve.

## SOAP behaviour
- checkStock insufficient → available=false (normal answer, NOT a fault)
- reservePart insufficient → SOAP fault INSUFFICIENT_STOCK
- reservePart idempotent by requestId (same ID + same payload = same reservation; different payload = IDEMPOTENCY_CONFLICT)
- releaseReservation idempotent (no double stock increase)

## End-to-end workflow
1. POST /alerts
2. POST /work-orders {alertId}
3. PATCH status CHECKING_INVENTORY
4. SOAP checkStock → if available=false, PATCH WAITING_FOR_PARTS
5. SOAP reservePart → on success, PATCH /reservation (WO auto-moves to READY_FOR_MAINTENANCE)
6. PATCH /assign, then PATCH status IN_PROGRESS, then PATCH status COMPLETED

## Seed data
Machines: MACHINE-001..MACHINE-004
Parts: PART-001 (50), PART-002 (20), PART-003 (30), PART-004 (15), PART-005 (25)
