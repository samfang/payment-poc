# payment-poc

Proof-of-concept monorepo for a payment platform, built with Java 21 and Spring Boot 3.

## Services

| Service | Description | Port |
|---|---|---|
| `payment-service` | Core payment processing REST API | 8080 |

## Standards

- [Code Principles](docs/CODE_PRINCIPLES.md) — Architecture, 12-factor, TDD, security
- [API Guidelines](docs/API_GUIDELINES.md) — REST design based on Microsoft and PayPal standards

## Prerequisites

- Java 21
- Maven 3.9+ (or use `./mvnw`)
- Docker + Docker Compose (for local run and integration tests)

## Run with Docker

```bash
cp .env.example .env        # fill in any values you want to override
docker compose up --build   # starts Postgres + payment-service
```

The service will be available at `http://localhost:8080`.
Health check: `curl http://localhost:8080/actuator/health`

## Run locally (without Docker)

```bash
# Start a local Postgres first (e.g. via Docker)
docker compose up postgres -d

# Run the service
cd services/payment-service
../../mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Build and test

```bash
./mvnw clean verify   # builds everything and runs all tests (requires Docker for Testcontainers)
```

## Repository Structure

```
payment-poc/
├── docs/                        # Architecture and API guidelines
├── libs/                        # Shared libraries (future)
├── services/
│   └── payment-service/         # Payment domain service
│       └── Dockerfile
├── .env.example                 # Required environment variable reference
├── docker-compose.yml           # Local dev stack (service + Postgres)
├── mvnw / mvnw.cmd              # Maven wrapper
├── .github/workflows/ci.yml     # CI pipeline
└── pom.xml                      # Parent POM
```
