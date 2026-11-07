# Code Principles

This document defines the engineering standards for all services in the `payment-poc` monorepo. All contributors and AI agents must follow these principles.

---

## 1. Architecture: Clean Layered Design

Each service is organised into strict layers. Dependencies flow inward only — outer layers depend on inner ones, never the reverse.

```
Controller  →  Service  →  Domain / Repository
     ↓              ↓               ↓
   DTOs          Business       JPA Entity
                 Logic
```

### Layers

| Layer | Package | Responsibility |
|---|---|---|
| **Domain** | `domain/<context>/` | JPA entities, value objects, enums |
| **Repository** | `domain/<context>/` | Spring Data JPA interfaces only |
| **Service** | `service/<context>/` | Business logic; interface + implementation |
| **DTO** | `dto/<context>/` | Request / response objects; never expose entities directly |
| **Controller** | `controller/` | HTTP mapping, input validation, delegates to service |
| **Config** | `config/` | Spring beans, security, OpenAPI, etc. |
| **Exception** | `exception/` | Custom exceptions, global handler |

### Rules

- Entities **never** leave the service layer. Controllers always receive and return DTOs.
- Services receive and return DTOs or domain primitives — never `HttpServletRequest` or Spring types.
- Repository interfaces contain only Spring Data-derived query methods and `@Query` annotations. No business logic.
- One domain context per vertical slice (e.g. `payment`, `account`). Do not mix contexts.

---

## 2. Domain Separation

Each business capability is a self-contained vertical slice:

```
com.example.paymentpoc/
├── payment/
│   ├── Payment.java          # entity
│   ├── PaymentStatus.java    # enum
│   ├── PaymentRepository.java
│   ├── PaymentService.java   # interface
│   ├── PaymentServiceImpl.java
│   ├── dto/
│   │   ├── CreatePaymentRequest.java
│   │   └── PaymentResponse.java
│   └── PaymentController.java
└── ...
```

Adding a new domain must not require changes to existing domain packages.

---

## 3. The Twelve-Factor App

All services **must** comply with the [12-Factor App](https://12factor.net) methodology:

| Factor | Requirement |
|---|---|
| **I. Codebase** | One repo per service (or one monorepo with one deployable artifact per service). No shared mutable state between services. |
| **II. Dependencies** | All dependencies declared in `pom.xml`. No system-level package assumptions. |
| **III. Config** | All environment-specific values come from environment variables or externally injected config (never hard-coded). Use `application.yml` for defaults; override via env vars. |
| **IV. Backing services** | Databases, caches, queues are attached resources referenced by URL/credentials from config — not wired in code. |
| **V. Build, release, run** | The build stage produces an artifact (JAR/image). The release stage combines artifact + config. The run stage executes it. CI/CD enforces these stages. |
| **VI. Processes** | Services are stateless. No in-process session state. Shared state lives in the database or cache. |
| **VII. Port binding** | Services are self-contained and bind their own port via embedded Tomcat. No external container dependency. |
| **VIII. Concurrency** | Scale by running more process instances, not by threading tricks inside the process. |
| **IX. Disposability** | Fast startup (< 30 s). Graceful shutdown: drain in-flight requests, then stop. |
| **X. Dev/prod parity** | Use the same Docker image for local dev, staging, and production. Keep the gap small. |
| **XI. Logs** | Write logs to `stdout`/`stderr` as a structured event stream. Never write to files or manage log rotation. Use JSON structured logging in deployed environments. |
| **XII. Admin processes** | One-off tasks (migrations, seed data) run as separate short-lived processes — not as startup code inside the main application. |

---

## 4. Test-Driven Development

Write the test first. All features begin with a failing test.

### Test pyramid

| Level | Framework | Scope |
|---|---|---|
| **Unit** | JUnit 5 + Mockito | Service, domain logic; no Spring context |
| **Integration** | `@SpringBootTest` + Testcontainers | Repository, full service wiring |
| **API / slice** | `@WebMvcTest` + MockMvc | Controller input/output, HTTP contract |
| **Contract** | (future) Spring Cloud Contract | Service-to-service compatibility |

### Rules

- Minimum 80% line coverage on `service` and `domain` packages.
- Use `@ExtendWith(MockitoExtension.class)` for unit tests — no Spring context overhead.
- Test class name mirrors implementation: `PaymentServiceImplTest`, `PaymentControllerTest`.
- One `@Test` per behaviour, not per method. Name tests in the form `should_<outcome>_when_<condition>`.
- Avoid `@Autowired` in unit tests. Inject via constructor.
- Use Testcontainers (PostgreSQL image) for all repository and integration tests.

---

## 5. Data Access

- Use **Spring Data JPA** (`JpaRepository`) for all CRUD and standard queries.
- Complex queries use `@Query` with JPQL (prefer) or native SQL when JPQL cannot express the query.
- **Never** use raw `EntityManager` in business code. Wrap it in a repository method.
- Apply pagination via `Pageable` / `Page<T>` for all list endpoints; never return an unbounded list.
- Apply `@Transactional` at the service layer, not the repository layer.
- Keep entity field names in `snake_case` via `@Column(name = "...")` mapping; Java field names stay `camelCase`.

---

## 6. Data Access Control

- All endpoints are authenticated by default (`anyRequest().authenticated()`). Explicitly permit public paths.
- Use JWT bearer tokens (OAuth 2.0 Resource Server). Validate `iss`, `aud`, `exp`, and required scope claims.
- **Owner-scoped queries:** A user may only read or mutate resources they own. Apply ownership checks in the service layer: `payment.ownerId().equals(currentUserId())`.
- Use Spring Security's `@PreAuthorize` for scope-based access, e.g. `@PreAuthorize("hasAuthority('SCOPE_payments:write')")`.
- Never pass raw user IDs from the request body — resolve them from the validated JWT principal.

---

## 7. Dependency Injection and Object Construction

- Prefer **constructor injection** in all Spring beans. Avoid `@Autowired` on fields.
- Mark service classes `final` and inject via a single all-args constructor.
- Use `@RequiredArgsConstructor` (Lombok) to reduce boilerplate.

---

## 8. Error Handling

- Throw typed domain exceptions (`PaymentNotFoundException`, `InsufficientFundsException`).
- A single `@RestControllerAdvice` class maps exceptions to HTTP responses.
- Do not catch-and-swallow exceptions in service or repository code.
- Log exceptions at `WARN` or `ERROR` level with enough context to diagnose — include IDs, not PII.

---

## 9. Logging and Observability

- Use SLF4J with Logback. Never use `System.out.println`.
- Log at `INFO` for significant state transitions (payment created, status changed).
- Log at `DEBUG` for internal decision points (only enabled in dev).
- Include a `traceId` in all log lines (via MDC). Spring Cloud Sleuth / Micrometer Tracing is the preferred mechanism.
- Expose a `/actuator/health` endpoint for liveness/readiness probes.

---

## 10. Code Style

- Format: Google Java Style (enforced by `google-java-format` in CI).
- Max line length: 120 characters.
- No wildcard imports.
- Unused code is deleted, not commented out.
- All public classes, interfaces, and methods have a one-line Javadoc summary (exception: trivial getters/setters).
