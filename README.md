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
- Maven 3.9+
- Docker (for Testcontainers integration tests)

## Quick Start

```bash
# Build everything
mvn clean verify

# Run payment-service locally
cd services/payment-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Repository Structure

```
payment-poc/
├── docs/                        # Architecture and API guidelines
├── services/
│   └── payment-service/         # Payment domain service
├── .github/workflows/ci.yml     # CI pipeline
└── pom.xml                      # Parent POM
```
