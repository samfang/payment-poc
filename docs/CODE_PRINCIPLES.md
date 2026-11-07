# Code Principles

This document defines the engineering standards for all services in the `payment-poc` monorepo. All contributors and AI agents must follow these principles.

---

## 1. Architecture: Feature-First with Layered Sub-packages

Each business capability is a self-contained vertical slice. Layers live *inside* the feature package, not at the top level. Cross-cutting infrastructure (security config, error handling) lives in `shared/`.

```
com.example.paymentpoc/
├── payment/
│   ├── api/              ← controllers, request/response DTOs, OpenAPI annotations
│   ├── application/      ← service interfaces and implementations, use-case orchestration
│   ├── domain/           ← model, value objects, repository *interface* (no Spring/JPA imports)
│   └── infrastructure/   ← JPA entities, Spring Data JPA repositories, external adapters
└── shared/
    ├── config/           ← Spring configuration beans (security, OpenAPI, etc.)
    └── exception/        ← ApiError, typed exceptions, GlobalExceptionHandler
```

### Layer responsibilities

| Layer | Allowed dependencies | Must not import |
|---|---|---|
| `domain` | Java stdlib, your own value types | Spring, JPA, Hibernate, Jackson |
| `application` | `domain`, `shared/exception` | `api`, `infrastructure`, Spring MVC |
| `infrastructure` | `domain`, JPA/Spring Data | `api`, `application` |
| `api` | `application` (via interface), `shared/exception`, Jackson | `domain` directly, `infrastructure` |
| `shared` | Spring, any framework | Feature packages |

### Rules

- Each layer has a `package-info.java` with a one-sentence description of its role.
- Entities and Spring Data repository implementations belong in `infrastructure`; the domain holds only a repository interface (`PaymentRepository`) with no Spring or JPA annotations.
- Controllers (`api`) receive and return DTOs only — never expose domain model objects in responses.
- Services (`application`) receive and return DTOs or domain value types — never `HttpServletRequest` or Spring MVC types.
- One feature package per domain context (e.g. `payment`, `account`). Do not mix concerns across feature packages.

---

## 2. The Twelve-Factor App

All services **must** comply with the [12-Factor App](https://12factor.net) methodology:

| Factor | Requirement |
|---|---|
| **I. Codebase** | One monorepo; one deployable JAR per service. No shared mutable state between services. |
| **II. Dependencies** | All dependencies declared in `pom.xml`. No system-level package assumptions. |
| **III. Config** | All environment-specific values come from environment variables. Use `application.yml` only for defaults and structural config; override at runtime via env vars. Never commit secrets. `.env.example` documents every required variable. |
| **IV. Backing services** | Databases, caches, queues are attached resources referenced by URL/credentials from config — not wired in code. |
| **V. Build, release, run** | The build stage produces a JAR/image. The release stage combines artifact + config. The run stage executes it. CI/CD enforces these stages. |
| **VI. Processes** | Services are stateless. No in-process session state. Shared state lives in the database or cache. |
| **VII. Port binding** | Services are self-contained and bind their own port via embedded Tomcat. No external container dependency. |
| **VIII. Concurrency** | Scale by running more process instances, not by threading tricks inside the process. |
| **IX. Disposability** | Fast startup (< 30 s). Graceful shutdown: drain in-flight requests, then stop. |
| **X. Dev/prod parity** | Use the same Docker image for local dev, staging, and production. Keep the gap small. |
| **XI. Logs** | Write logs to `stdout`/`stderr` as a structured JSON event stream. Never write to files or manage log rotation. The `local` Spring profile may use plain text for readability. |
| **XII. Admin processes** | One-off tasks (data migrations, seed data) should run as separate short-lived processes. **POC exception:** Flyway migrations run on application startup (controlled by `SPRING_FLYWAY_ENABLED`), which is acceptable for this POC but should be replaced with a separate migration job before production. |

---

## 3. Test-Driven Development

Write the test first. All features begin with a failing test.

### Test pyramid

| Level | Framework | Scope |
|---|---|---|
| **Unit** | JUnit 5 + Mockito | Service and domain logic; no Spring context |
| **Slice** | `@WebMvcTest` + MockMvc | Controller input/output, HTTP contract |
| **Integration** | `@SpringBootTest` + Testcontainers | Repository queries, full service wiring |

### Rules

- Minimum 80% line coverage on `application` and `domain` packages.
- Use `@ExtendWith(MockitoExtension.class)` for unit tests — no Spring context overhead.
- Test class name mirrors implementation: `PaymentServiceImplTest`, `PaymentControllerTest`.
- One `@Test` per behaviour. Name tests `should_<outcome>_when_<condition>`.
- Avoid `@Autowired` in unit tests. Inject via constructor.
- Use Testcontainers (PostgreSQL image) for all repository and integration tests.

---

## 4. Data Access

- Use **Spring Data JPA** (`JpaRepository`) for all CRUD and standard queries.
- Complex queries use `@Query` with JPQL (preferred) or native SQL when JPQL is insufficient.
- Apply pagination via `Pageable` / `Page<T>` for all list endpoints; never return an unbounded list.
- Apply `@Transactional` at the `application` (service) layer, not the `infrastructure` layer.
- Keep database column names in `snake_case` via `@Column(name = "...")` mapping; Java field names stay `camelCase`.

---

## 5. Data Access Control

- All endpoints are authenticated by default (`anyRequest().authenticated()`). Explicitly permit public paths.
- Use JWT bearer tokens (OAuth 2.0 Resource Server). Validate `iss`, `aud`, `exp`, and required scope claims.
- **Owner-scoped queries:** A user may only read or mutate resources they own. Apply ownership checks in the `application` layer: `payment.ownerId().equals(currentUserId())`.
- Use Spring Security's `@PreAuthorize` for scope-based access, e.g. `@PreAuthorize("hasAuthority('SCOPE_payments:write')")`.
- Never pass raw user IDs from the request body — resolve them from the validated JWT principal.

---

## 6. Dependency Injection and Object Construction

- Prefer **constructor injection** in all Spring beans. Avoid `@Autowired` on fields.
- Use `@RequiredArgsConstructor` (Lombok) to reduce boilerplate.
- Do not declare Spring beans `final` — CGLIB-based proxies (used by `@Transactional` and `@PreAuthorize`) require subclassing and will fail at runtime if the class is final.

---

## 7. Error Handling

- Throw typed domain exceptions (`PaymentNotFoundException`, `InsufficientFundsException`).
- A single `@RestControllerAdvice` in `shared/exception/` maps exceptions to HTTP responses.
- Do not catch-and-swallow exceptions in service or repository code.
- Log exceptions at `WARN` or `ERROR` level with enough context to diagnose — include IDs, not PII.

---

## 8. Logging and Observability

- Use SLF4J with Logback. Never use `System.out.println`.
- Log at `INFO` for significant state transitions (payment created, status changed).
- Log at `DEBUG` for internal decision points (only enabled in dev).
- Include a `traceId` in all log lines via MDC. Use **Micrometer Tracing** (Spring Boot 3+).
- Expose `/actuator/health/liveness` and `/actuator/health/readiness` probes (set `management.endpoint.health.probes.enabled=true`).
- Write logs as structured JSON to stdout in deployed environments. Use plain text only for the `local` profile.

---

## 9. Code Style

- Format: Google Java Style (enforced by `google-java-format` in CI).
- Max line length: **100 characters** (Google Java Style default).
- No wildcard imports.
- Unused code is deleted, not commented out.
- All public classes, interfaces, and methods have a one-line Javadoc summary (exception: trivial getters/setters generated by Lombok).
