# SOA Project — Backend

Industrial monitoring + maintenance management. Two independent backend services
exposing three protocols, one shared PostgreSQL instance with two isolated schemas.

| Component | Port | Protocol | Ownership |
|---|---|---|---|
| Maintenance REST | 8081 | REST / JSON (JWT) | machines, users, technicians, alerts, work orders, reservation references |
| Inventory SOAP | 8082 | SOAP / XML (`X-Inventory-Api-Key`) | spare parts, stock, reservations, stock movements, purchase orders |
| PostgreSQL | 5434 (host) → 5432 (container) | JDBC | two schemas: `maintenance`, `inventory` |
| gRPC Telemetry | 9090 | (owned by Houssem's teammate module) | live telemetry — not implemented here |

## Quick start

```bash
# 1. Configure secrets
cp .env.example .env
#    edit: POSTGRES_ADMIN_PASSWORD, MAINTENANCE_DB_PASSWORD, INVENTORY_DB_PASSWORD,
#          JWT_SECRET            (openssl rand -base64 32)
#          ADMIN_PASSWORD
#          INVENTORY_API_KEY     (openssl rand -hex 32)

# 2. Start PostgreSQL
docker compose up -d
docker compose ps                     # wait for "healthy"

# 3. Build + run tests (requires Docker for Testcontainers)
mvn clean verify

# 4. Start each service (each in its own terminal)
cd ~/Desktop/Projects/soa-project-backend
set -a && . ./.env && set +a
mvn -pl maintenance-service spring-boot:run     # terminal A