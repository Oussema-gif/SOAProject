# SOA Project — Desktop Client & Telemetry Service

Industrial monitoring and predictive-maintenance desktop application.

## Architecture
- **Desktop Client**: JavaFX 21 (Concrete Archive UI)
- **Telemetry Service**: gRPC (Port 9090)
- **Maintenance Service**: REST (Port 8081) — owned by Oussema
- **Inventory Service**: SOAP (Port 8082) — owned by Oussema
- **Database**: PostgreSQL (Port 5434) via Docker

## Prerequisites
- Java 21 (JDK)
- Maven
- Docker Desktop (for the database)
- Oussema's backend repository (cloned separately)

## How to Run the System

### Step 1: Start the Database (Docker)
Navigate to Oussema's backend folder and run:
```bash
docker compose up -d