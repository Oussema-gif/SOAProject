# SOA Project
### Industrial Monitoring & Predictive Maintenance System

A service-oriented industrial monitoring platform built with **Java 21, Spring Boot, REST, SOAP, gRPC, and JavaFX**.

SOA Project demonstrates how heterogeneous services can collaborate within a distributed enterprise application to monitor industrial machines, process telemetry, detect abnormal operating conditions, manage maintenance alerts, and coordinate spare-part availability.

---

## Overview

Industrial environments require reliable monitoring and coordinated maintenance operations. This project addresses these requirements through a distributed architecture built around independent services communicating through different enterprise integration protocols.

The platform brings together three backend services and a JavaFX desktop application, demonstrating interoperability between REST, SOAP, and gRPC.

### Key Capabilities

- Industrial machine management.
- Machine telemetry ingestion.
- Abnormal-condition detection and alert processing.
- Maintenance work-order management.
- Work-order lifecycle and state transitions.
- Spare-part availability verification.
- Inventory and stock reservation operations.
- JWT-based authentication and role-based access control.
- Integration of REST, SOAP, and gRPC services.

---

## Architecture

The system follows a service-oriented architecture in which each service has a clearly defined responsibility and communication contract.

### High-Level Architecture

```text
                  JAVA FX DESKTOP CLIENT
                           |
             +-------------+-------------+
             |             |             |
            REST          SOAP          gRPC
             |             |             |
      +-------------+ +-----------+ +-------------+
      | Maintenance | | Inventory | |  Telemetry  |
      |   Service   | |  Service  | |   Service   |
      +-------------+ +-----------+ +-------------+
             |             |
             +------+------+
                    |
              +-----------+
              | PostgreSQL|
              |  Database |
              +-----------+
```

### System Components

| Component | Technology | Responsibility |
|---|---|---|
| Maintenance Service | Java, Spring Boot, REST | Machines, authentication, alerts, and work orders |
| Inventory Service | Java, SOAP | Spare parts, stock management, and reservations |
| Telemetry Service | Java, gRPC | Machine telemetry and real-time readings |
| Desktop Client | JavaFX | User interface and service integration |
| Database | PostgreSQL | Persistent data storage |

### Communication Protocols

**REST API**

Used for maintenance operations and application management.

- JWT authentication.
- Machine management.
- Alert management.
- Maintenance work orders.
- Work-order state transitions.

**SOAP Web Services**

Used for inventory-related enterprise operations.

- Spare-part information.
- Inventory availability checks.
- Stock management.
- Spare-part reservations.

**gRPC**

Used for telemetry communication.

- Structured telemetry messages.
- Machine readings transmission.
- Integration with the monitoring workflow.

---

## Technology Stack

| Category | Technologies |
|---|---|
| Programming Language | Java 21 |
| Backend Framework | Spring Boot 3 |
| Security | Spring Security, JWT |
| Communication | REST, SOAP, gRPC |
| Desktop Application | JavaFX |
| Database | PostgreSQL |
| Build Management | Maven |
| Database Migrations | SQL, configured migration tooling |
| API Documentation | Swagger / OpenAPI |
| Testing | JUnit 5, Spring Boot Test, Testcontainers |
| Infrastructure | Docker, Docker Compose |
| Version Control | Git, GitHub |

---

## Project Structure

```text
soa-project-backend/
│
├── maintenance-service/
│   └── Spring Boot REST application
│
├── inventory-soap-service/
│   └── SOAP inventory application
│
├── telemetry-grpc-service/
│   └── gRPC telemetry application
│
├── desktop-client/
│   └── JavaFX desktop application
│
├── docs/
│   ├── integration-contract.md
│   ├── openapi.yaml
│   ├── soap-examples.xml
│   ├── postman-collection.json
│   └── demo-workflow.sh
│
├── docker-compose.yml
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

---

## Getting Started

### Prerequisites

Make sure the following tools are installed:

- JDK 21
- Apache Maven
- Docker
- Docker Compose
- Git

Verify your environment:

```bash
java -version
mvn -version
docker --version
docker compose version
git --version
```

### 1. Clone the Repository

```bash
git clone YOUR_REPOSITORY_URL
cd soa-project-backend
```

### 2. Configure Environment Variables

Create your local environment configuration:

```bash
cp .env.example .env
```

Configure the required database credentials, application secrets, and service configuration.

Generate secure secrets when required:

```bash
openssl rand -base64 32
openssl rand -hex 32
```

Assign the generated values to the corresponding environment variables.

**Security notice:** Never commit `.env` files, database credentials, JWT secrets, or API keys to version control.

### 3. Start PostgreSQL

Start the database using Docker Compose:

```bash
docker compose up -d
```

Check the container status:

```bash
docker compose ps
```

Wait until PostgreSQL reports a healthy status before starting the backend services.

---

## Running the Application

Each backend service runs independently.

### Maintenance Service — REST

```bash
set -a
source .env
set +a

mvn -pl maintenance-service spring-boot:run
```

Default port: `8081`

### Inventory Service — SOAP

```bash
set -a
source .env
set +a

mvn -pl inventory-soap-service spring-boot:run
```

Default port: `8082`

### Telemetry Service — gRPC

The telemetry service uses its configured Maven module and application entry point.

Default gRPC port: `9090`.

Refer to the telemetry module configuration for its exact startup command.

### JavaFX Desktop Client

```bash
set -a
source .env
set +a

mvn -f desktop-client/pom.xml clean javafx:run
```

The desktop client integrates with the backend through REST, SOAP, and gRPC.

---

## Testing

Run the complete Maven verification lifecycle from the project root:

```bash
mvn clean verify
```

The test suite covers the configured application components, including:

- REST API endpoints.
- Authentication and authorization.
- Machine management.
- Alert processing.
- Work-order management.
- SOAP service operations.
- Inventory management.
- Database integration.

Integration testing uses Testcontainers where configured.

---

## End-to-End Demonstration

The project includes a demonstration script for the maintenance integration workflow.

Run:

```bash
set -a
source .env
set +a

bash docs/demo-workflow.sh
```

### Demonstration Workflow

1. Authenticate with the Maintenance REST Service.
2. Submit or retrieve machine-related information.
3. Process maintenance alerts.
4. Check spare-part availability through the Inventory SOAP Service.
5. Execute the maintenance workflow.
6. Apply work-order state transitions.
7. Verify the resulting application state.

Expected successful execution:

```text
Workflow completed successfully
```

---

## API & Integration Documentation

The `docs/` directory contains the project's integration resources.

| File | Description |
|---|---|
| `integration-contract.md` | Frontend/backend integration contract |
| `openapi.yaml` | REST API specification |
| `soap-examples.xml` | SOAP request and response examples |
| `postman-collection.json` | Postman API collection |
| `demo-workflow.sh` | End-to-end workflow demonstration |

---

## Security

Security is integrated into the application architecture through:

- JWT-based authentication.
- Role-based access control.
- API-key protection for inventory operations.
- Environment-based secret configuration.
- Separation of service responsibilities.
- Protected backend operations.

Sensitive configuration and credentials must remain outside version control.

---

## Database Architecture

PostgreSQL provides persistent storage for the backend components.

The Maintenance and Inventory services use separate database schemas to maintain logical data separation.

Database configuration is managed through environment variables and Docker Compose.

---

## Academic Objectives

This project demonstrates practical implementation of core Service-Oriented Architecture concepts:

- Service independence.
- Loose coupling.
- Service interoperability.
- Heterogeneous communication protocols.
- Distributed application architecture.
- Enterprise application integration.
- Service orchestration.
- Database integration.
- Secure service communication.

---

## Authors

**Oussema Rahmouni**

Backend Engineering & Enterprise Services

**Houssem Mosbeh**

Desktop Application & Frontend Integration

---

## License

Developed for academic and educational purposes.