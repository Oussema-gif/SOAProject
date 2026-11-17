# SOA Project

### Industrial Monitoring & Predictive Maintenance System

A Service-Oriented Architecture (SOA) academic project demonstrating the
integration of REST, SOAP, and gRPC technologies into a unified
industrial monitoring and maintenance management platform.

The system monitors industrial machines, processes telemetry readings,
detects abnormal conditions, generates maintenance alerts, and
coordinates spare-part availability.

------------------------------------------------------------------------

## 1. System Architecture

The platform consists of four main components:

  -----------------------------------------------------------------------
  Component               Technology              Responsibility
  ----------------------- ----------------------- -----------------------
  Maintenance Service     Java, Spring Boot, REST Machines, alerts, work
                                                  orders, authentication

  Inventory Service       Java, SOAP              Spare parts, stock
                                                  management,
                                                  reservations

  Telemetry Service       gRPC                    Machine telemetry and
                                                  real-time readings

  Desktop Client          JavaFX                  User interface and
                                                  service orchestration
  -----------------------------------------------------------------------

### Architecture Overview

``` text
                 JAVA FX DESKTOP CLIENT
                          |
             +------------+------------+
             |            |            |
            REST         SOAP         gRPC
             |            |            |
       +-----------+ +-----------+ +-----------+
       |Maintenance| | Inventory | | Telemetry |
       |  Service  | |  Service  | |  Service  |
       +-----------+ +-----------+ +-----------+
             |            |
             +-----+------+
                   |
             +-----------+
             | PostgreSQL|
             | Database  |
             +-----------+
```

### Communication Protocols

**REST** - JWT authentication. - Machine management. - Alert
management. - Maintenance work orders. - Work-order state transitions.

**SOAP** - Spare-part information. - Inventory availability checks. -
Stock management. - Spare-part reservations.

**gRPC** - Machine telemetry communication. - Structured telemetry
messages. - Integration with the monitoring workflow.

------------------------------------------------------------------------

## 2. Technologies Used

### Backend

-   Java 21
-   Spring Boot 3
-   Spring Security
-   JWT Authentication
-   REST API
-   SOAP Web Services
-   gRPC
-   Maven

### Database

-   PostgreSQL
-   SQL
-   Database schema isolation
-   Database migrations

### Desktop Application

-   JavaFX
-   Java
-   REST Client
-   SOAP Client
-   gRPC Client

### Development & Infrastructure

-   Docker
-   Docker Compose
-   Git
-   GitHub
-   JUnit 5
-   Spring Boot Test
-   Testcontainers
-   Swagger / OpenAPI

------------------------------------------------------------------------

## 3. Project Structure

``` text
soa-project-backend/
├── maintenance-service/
├── inventory-soap-service/
├── telemetry-grpc-service/
├── desktop-client/
├── docs/
│   ├── integration-contract.md
│   ├── openapi.yaml
│   ├── soap-examples.xml
│   ├── postman-collection.json
│   └── demo-workflow.sh
├── docker-compose.yml
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

------------------------------------------------------------------------

## 4. Prerequisites

-   Java Development Kit 21
-   Apache Maven
-   Docker
-   Docker Compose
-   Git

Verify your environment:

``` bash
java -version
mvn -version
docker --version
docker compose version
git --version
```

------------------------------------------------------------------------

## 5. Installation & Configuration

### Step 1: Clone the Repository

``` bash
git clone YOUR_REPOSITORY_URL
cd soa-project-backend
```

### Step 2: Configure Environment Variables

``` bash
cp .env.example .env
```

Configure database credentials and application secrets.

Generate secure secrets:

``` bash
openssl rand -base64 32
openssl rand -hex 32
```

Use the generated values for the corresponding JWT and API-key
environment variables.

**Important:** Never commit your `.env` file or expose production
secrets.

### Step 3: Start PostgreSQL

``` bash
docker compose up -d
docker compose ps
```

Wait until the database reports a healthy status.

------------------------------------------------------------------------

## 6. Running the Backend

Run each backend service in a separate terminal.

### Maintenance REST Service

``` bash
set -a
source .env
set +a
mvn -pl maintenance-service spring-boot:run
```

Port: `8081`

### Inventory SOAP Service

``` bash
set -a
source .env
set +a
mvn -pl inventory-soap-service spring-boot:run
```

Port: `8082`

### Telemetry gRPC Service

Start the telemetry service using its configured Maven module and
application entry point.

Default configured gRPC port: `9090`.

Refer to the module configuration for the exact startup command.

------------------------------------------------------------------------

## 7. Running the JavaFX Desktop Application

``` bash
set -a
source .env
set +a
mvn -f desktop-client/pom.xml clean javafx:run
```

The desktop application communicates with the backend services through
their respective protocols.

------------------------------------------------------------------------

## 8. Testing

Run the complete Maven verification lifecycle:

``` bash
mvn clean verify
```

The test suite covers:

-   REST API endpoints.
-   Authentication and authorization.
-   Machine management.
-   Alert processing.
-   Work-order management.
-   SOAP service operations.
-   Inventory management.
-   Database integration.

Integration tests use Testcontainers where configured.

------------------------------------------------------------------------

## 9. End-to-End Demonstration

The project includes an orchestration script demonstrating the
maintenance workflow.

``` bash
set -a
source .env
set +a
bash docs/demo-workflow.sh
```

### Demonstration Workflow

1.  Authenticate with the REST service.
2.  Submit or retrieve machine-related information.
3.  Process maintenance alerts.
4.  Check spare-part availability through SOAP.
5.  Execute the maintenance workflow.
6.  Apply work-order state transitions.
7.  Verify the resulting system state.

Expected result:

``` text
Workflow completed successfully
```

------------------------------------------------------------------------

## 10. API Documentation

The `docs/` directory contains the integration documentation.

  File                      Description
  ------------------------- ---------------------------------------
  integration-contract.md   Frontend/backend integration contract
  openapi.yaml              REST API specification
  soap-examples.xml         SOAP request and response examples
  postman-collection.json   Postman API collection
  demo-workflow.sh          End-to-end workflow demonstration

------------------------------------------------------------------------

## 11. Security

The project demonstrates:

-   JWT-based authentication.
-   Role-based access control.
-   API-key protection for inventory operations.
-   Environment-based secret configuration.
-   Separation of application responsibilities.
-   Protected backend communication.

Credentials and sensitive configuration values should remain outside
version control.

------------------------------------------------------------------------

## 12. Database Architecture

PostgreSQL provides persistent storage for the backend.

The maintenance and inventory components use separate database schemas
to maintain logical data separation.

Database configuration is managed through environment variables and
Docker Compose.

------------------------------------------------------------------------

## 13. Academic Project Objectives

This project demonstrates practical implementation of:

-   Service independence.
-   Loose coupling.
-   Interoperability.
-   Heterogeneous communication protocols.
-   Service orchestration.
-   Distributed application architecture.
-   Database integration.
-   Secure service communication.
-   Enterprise application integration.

------------------------------------------------------------------------

## 14. Authors

**Oussema Rahmouni**\
Backend Engineering & Enterprise Services

**Houssem Mosbeh**\
Desktop Application & Frontend Integration

------------------------------------------------------------------------

## 15. License

This project was developed for academic and educational purposes.
