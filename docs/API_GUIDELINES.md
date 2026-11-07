# API Guidelines

This document defines the REST API design standards for all services in the `payment-poc` monorepo. These guidelines are derived from and aligned with two industry-published standards, plus payment-specific patterns from Stripe's API:

- **Microsoft Azure REST API Guidelines** — https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md  
  (also: [Graph guidelines](https://github.com/microsoft/api-guidelines/blob/vNext/graph/GuidelinesGraph.md), [repo root](https://github.com/microsoft/api-guidelines))
- **PayPal REST API Style Guide** — [archived](https://web.archive.org/web/20220114091735/https://github.com/paypal/api-standards/blob/master/api-style-guide.md) · [PayPal Tech blog](https://medium.com/paypal-tech/api-style-guide-and-patterns-the-evolutionary-journey-continues-bb2bad7a87ee)
- **Stripe API reference** (payment-specific patterns, see §13) — https://docs.stripe.com/api

---

## Standards Comparison: Where Microsoft and PayPal Differ

Where the two standards conflict, the choice made for this project is shown in the **We use** column with the rationale.

| Topic | Microsoft (Azure) | PayPal | We use | Rationale |
|---|---|---|---|---|
| **Versioning** | Query param `?api-version=2024-01-01` (date-based) | Path segment `/v1/`, `/v2/` (integer) | Path segment `/v1/` | Path versioning is explicit in URLs, easier to route at the gateway, and aligns with the existing PayPal payment-service convention. |
| **JSON field casing** | `camelCase` | `snake_case` | `camelCase` | Spring Boot / Jackson defaults to camelCase; less friction with the Java codebase. |
| **Error body** | `{"error": {"code", "message", "target", "details", "innererror"}}` | `{"name", "message", "details", "debug_id", "links"}` | Microsoft envelope with `debugId` | The Microsoft shape nests cleanly and is better for tooling; `debugId` is kept from PayPal for support traceability. |
| **Pagination** | `{"value": [...], "nextLink": "..."}` | `{"items": [...], "links": [...]}` | Cursor-based with `items` + `nextCursor` + `links` | Cursor pagination is adopted from both; response field names follow PayPal convention (`items`) since `value` is ambiguous. |
| **Idempotency header** | `Repeatability-Request-ID` + `Repeatability-First-Sent` | `PayPal-Request-Id` | `Idempotency-Key` | A single UUID header is simpler for clients. `Idempotency-Key` is widely understood (also used by Stripe) and avoids a PayPal-branded name in a generic service. |
| **Money format** | No specific guidance | String with separate `currency_code` | Integer minor units `amount` + `currency` (ISO 4217); follows Stripe's convention (§13.4) | Integer minor units avoid floating-point precision loss and are unambiguous across currencies with different decimal places (JPY has 0, KWD has 3). |
| **HATEOAS** | Not required | Required `links` array on every resource | Required | HATEOAS improves discoverability. Links help clients navigate next states without constructing URLs. |

---

## 1. URL Structure

```
https://<host>/v{N}/<resource-collection>[/<resource-id>][/<sub-resource>]
```

Examples:

```
GET  /v1/payments
GET  /v1/payments/{paymentId}
POST /v1/payments/{paymentId}/captures
```

The service root (e.g. `/payments`) is a gateway concern — services bind at `/` and expose `/v1/...` directly. Do not embed a service-name prefix in the application's path.

### Rules

- Path segments use **lowercase kebab-case**: `/payment-methods`, not `/paymentMethods`.
- Collection names are **plural nouns**: `/payments`, not `/payment`.
- Do not use verbs in URLs for CRUD operations. Use HTTP method semantics instead.
- Action endpoints that cannot be expressed as a resource noun may use a verb sub-resource: `POST /v1/payments/{id}/cancel`.
- Do not expose internal identifiers (auto-increment IDs). Use UUIDs.

---

## 2. Versioning

- API versions appear **in the URL path**: `/v1/payments`, `/v2/payments`.
- The version segment is a single integer (`v1`, `v2`). No minor versions in the path.
- Breaking changes require a new major version. The old version is supported for a minimum deprecation period of 12 months.
- **Breaking change** = removing a field, changing a field type, changing HTTP method/status semantics, removing an endpoint.
- **Non-breaking** = adding optional fields, adding new endpoints.

---

## 3. HTTP Methods

| Method | Semantics | Success Code |
|---|---|---|
| `GET` | Retrieve resource(s). **Must** be safe and idempotent. | `200 OK` |
| `POST` | Create a new resource or trigger an action. | `201 Created` (create), `200 OK` (action) |
| `PUT` | Replace a resource entirely. Idempotent. | `200 OK` or `201 Created` |
| `PATCH` | Partial update using JSON Merge Patch ([RFC 7396](https://tools.ietf.org/html/rfc7396)). | `200 OK` |
| `DELETE` | Remove a resource. Idempotent. | `204 No Content` |

- `GET` requests **must not** have a request body.
- `DELETE` on a non-existent resource returns `204 No Content` (not `404`) to preserve idempotency.

---

## 4. HTTP Status Codes

| Code | Usage |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH, or action POST |
| `201 Created` | Resource successfully created; `Location` header points to the new resource |
| `202 Accepted` | Async operation started; `Location` header points to the status monitor |
| `204 No Content` | Successful DELETE or operation with no response body |
| `400 Bad Request` | Malformed request, invalid field value, missing required field |
| `401 Unauthorized` | Missing or invalid authentication token |
| `403 Forbidden` | Authenticated but not authorised for this resource |
| `404 Not Found` | Resource does not exist (or existence is hidden by access policy) |
| `409 Conflict` | Duplicate idempotency key with a different request body; state conflict |
| `422 Unprocessable Entity` | Syntactically valid but semantically invalid (e.g. insufficient funds) |
| `429 Too Many Requests` | Rate limit exceeded; include `Retry-After` header |
| `500 Internal Server Error` | Unexpected server fault |
| `503 Service Unavailable` | Temporary unavailability; include `Retry-After` header |

---

## 5. Request and Response Format

- All request and response bodies are **JSON** (`Content-Type: application/json`).
- JSON field names use **camelCase**: `paymentId`, `createdAt`, `amount`.
- Timestamps use **ISO 8601 / RFC 3339** in UTC: `"2024-03-15T10:30:00Z"`.
- Monetary amounts are **integers in minor units** (§13.4): `"amount": 1999`. Always include `currency` (ISO 4217 code). Never use a decimal or string for `amount`; the receiver must reject float tokens (e.g. `500.0`) with 400.
- Enums are **SCREAMING_SNAKE_CASE** strings: `"status": "PENDING_APPROVAL"`.
- Do not return `null` fields. Omit optional fields that have no value.
- Boolean fields are named affirmatively: `isRefundable`, not `notRefundable`.

### Standard response envelope for single resources

```json
{
  "id": "pay_8f3a2b1c",
  "createdAt": "2024-03-15T10:30:00Z",
  "updatedAt": "2024-03-15T10:30:05Z",
  "status": "CAPTURED",
  "amount": 4999,
  "currency": "USD",
  "links": [
    { "rel": "self",    "href": "/v1/payments/pay_8f3a2b1c", "method": "GET" },
    { "rel": "refund",  "href": "/v1/payments/pay_8f3a2b1c/refunds", "method": "POST" }
  ]
}
```

---

## 6. HATEOAS Links

Every resource response **must** include a `links` array for discoverability:

```json
"links": [
  { "rel": "self",   "href": "/v1/payments/{id}", "method": "GET" },
  { "rel": "update", "href": "/v1/payments/{id}", "method": "PATCH" },
  { "rel": "cancel", "href": "/v1/payments/{id}/cancel", "method": "POST" }
]
```

- `rel` describes the relationship. Use `self` for the canonical URL of the resource.
- `href` is an absolute path (no host). Clients must not construct URLs by string manipulation.

---

## 7. Error Response Format

All error responses use a consistent envelope (Microsoft structure with PayPal's `debugId`):

```json
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "The payment could not be processed because the account balance is insufficient.",
    "target": "amount",
    "details": [
      {
        "code": "BALANCE_TOO_LOW",
        "message": "Available balance is 1000 USD minor units; requested 4999 USD minor units.",
        "target": "amount"
      }
    ],
    "debugId": "3f2b8c1e-9d4a-4e7b-a6c5-1b2d3e4f5a6b"
  }
}
```

| Field | Required | Description |
|---|---|---|
| `error.code` | Yes | Machine-readable, stable, SCREAMING_SNAKE_CASE. Part of the API contract — never change. |
| `error.message` | Yes | Human-readable explanation. May change between versions. |
| `error.target` | No | Field or parameter name that caused the error. |
| `error.details` | No | Array of sub-errors for multi-field validation failures. |
| `error.debugId` | Yes | Opaque ID for support tracing. Equals the `Request-Id` response header (§13.1) and the `requestId` field in server logs. |

- Include `X-Error-Code: INSUFFICIENT_FUNDS` as a response header matching `error.code`.
- Validation errors that affect multiple fields return a single `400` with all failing fields in `error.details`.

---

## 8. Pagination

All collection endpoints **must** support pagination. Never return an unbounded list.

```json
{
  "items": [ ... ],
  "nextCursor": "eyJpZCI6InBheV8xMjMifQ==",
  "links": [
    { "rel": "self", "href": "/v1/payments?cursor=...&pageSize=20", "method": "GET" },
    { "rel": "next", "href": "/v1/payments?cursor=eyJpZCI6InBheV8xMjMifQ==&pageSize=20", "method": "GET" }
  ]
}
```

- Use **cursor-based pagination** (not offset). Cursor is an opaque, base64-encoded string.
- Default page size: `20`. Maximum: `100`.
- When there is no next page, omit `nextCursor` and the `next` link.
- `totalCount` is **optional**. Omit it by default — computing an accurate count on large tables is expensive. Include it only when the client explicitly requests it (e.g. `?includeTotalCount=true`).
- Query parameters: `pageSize` (integer), `cursor` (string).

---

## 9. Idempotency

**All state-mutating operations must be idempotent** (critical for payments).

- Clients **must** include an `Idempotency-Key` header on all `POST`, `PUT`, and `PATCH` requests.
- The header value is a client-generated UUID (v4).
- The server caches the response for the same key for a minimum of 24 hours.
- A duplicate request with the same key returns the **original cached response with its original status code** — `201 Created` for a successful creation, not `200 OK`.
- If the same key is used with a different request body, return `409 Conflict`.
- Keys are **scoped to the caller** (`ownerId` + key). One caller's key never returns another caller's response.
- Key length: at most 255 characters. Keys must be random (UUID v4 recommended) and must never contain PII such as emails.
- A second request that arrives while the first one with the same key is still running returns `409 Conflict`; the client may retry it later.
- Requests rejected by validation (`400`) are **not** stored, so the client can fix the request and retry with the same key.
- Idempotency keys apply to `POST` only in our services today; sending one on `GET` or `DELETE` has no effect.

```
POST /v1/payments
Idempotency-Key: 7f9d2b1a-3c4e-4f5a-b6d7-8e9f0a1b2c3d
```

---

## 10. Filtering, Sorting, and Searching

```
GET /v1/payments?status=CAPTURED&currency=USD
GET /v1/payments?sort=createdAt:desc,amount:asc
GET /v1/payments?q=john
```

- Filter parameters match resource field names in `camelCase`.
- Sort format: `sort=field:asc|desc`. Multiple sort fields comma-separated.
- Full-text search uses `q`.
- Invalid filter or sort parameter values return `400 Bad Request`.

---

## 11. Authentication and Security

- All endpoints require a **JWT Bearer token** (OAuth 2.0).
- Required claims: `sub` (subject/user ID), `iss` (issuer), `aud` (audience), `exp` (expiry), `scope`.
- Use scopes to gate access: `payments:read`, `payments:write`, `payments:admin`.
- Never include sensitive data (card numbers, CVV, PII) in URLs or headers.
- All traffic must use **TLS 1.2+**. HTTP requests must be rejected.

---

## 12. API Documentation

- Every endpoint must have an OpenAPI 3.x specification in `openapi.yaml`.
- Use `springdoc-openapi` to generate the spec from annotations.
- Each endpoint documents: summary, description, all request parameters, request body schema, all response codes and body schemas, and security requirements.
- Include example values for all fields in the schema.

---

## 13. Payment Patterns Adopted from Stripe

Stripe's API is the most widely used payments API, so we reuse its proven patterns where they fit our Microsoft/PayPal-based conventions. Source pages are linked per item.

### 13.1 Request IDs

- Every response carries a `Request-Id` header, equal to the `debugId` used in error bodies and logs. Clients quote it when reporting problems. ([Stripe: request IDs](https://docs.stripe.com/api/request_ids))

### 13.2 Error body extras

Our error envelope (§7) stays as is. For payment errors, two optional fields may be added inside `error`:

| Field | When | Example |
|---|---|---|
| `declineCode` | A payment processor declined the payment; carries the processor's reason, never shown raw to end users | `"insufficient_funds"` |
| `docUrl` | A public doc explains the `code` | `"https://docs.example.com/errors/INVALID_PAYMENT_STATE"` |

([Stripe: errors](https://docs.stripe.com/api/errors))

### 13.3 Metadata

Payment-type resources may accept a `metadata` object of string key/value pairs for the client's own references (e.g. order ID):

- At most 50 keys; keys up to 40 characters; values up to 500 characters; no `[` or `]` in keys.
- Metadata is stored and returned as-is, never interpreted by the service.
- **Never** put card numbers, bank details or other sensitive data in metadata.

([Stripe: metadata](https://docs.stripe.com/api/metadata))

### 13.4 Amounts in minor units

All monetary amounts are represented as **integers in minor units** — the smallest denomination of the currency — with a separate ISO 4217 `currency` field. This is Stripe's convention and eliminates floating-point ambiguity. ([Stripe: currencies](https://docs.stripe.com/currencies#zero-decimal))

| Currency | Exponent | Example: "ten dollars/yen/dinars" |
|----------|----------|-----------------------------------|
| USD, EUR, GBP | 2 (100 minor units = 1 major unit) | `{"amount": 1000, "currency": "USD"}` |
| JPY | 0 (1 minor unit = 1 yen) | `{"amount": 10, "currency": "JPY"}` |
| KWD | 3 (1000 fils = 1 KWD) | `{"amount": 10000, "currency": "KWD"}` |

Rules:

- Maximum `amount`: `99999999` minor units per request.
- Currency must be a known ISO 4217 code with a defined exponent. Currencies with no minor-unit definition (e.g. `XAU`, precious metals) are rejected with `400 VALIDATION_ERROR, target=currency`.
- Clients **must** send `amount` as a JSON integer. Decimal-point tokens (`500.0`) and string tokens (`"500"`) are rejected with `400`.
- The exponent is looked up from `java.util.Currency.getInstance(code).getDefaultFractionDigits()` at the service layer; it is never trusted from the client.

### 13.5 Payment lifecycle

Model payments as an explicit state machine (see `PaymentStatus`), and design new payment flows with these Stripe lessons in mind:

- Real payments are asynchronous. Plan for in-between states such as "processing" and "requires customer action" (e.g. 3-D Secure), instead of assuming one call ends in success or failure.
- Keep **authorization** and **capture** separate when funds are held before being taken (Stripe's `capture_method=manual` and `requires_capture` status).
- Clients must not infer the outcome from the HTTP status alone. They read the resource's `status` field, or listen for events (§13.6).

([Stripe: PaymentIntent object](https://docs.stripe.com/api/payment_intents/object))

### 13.6 Events and webhooks (for future outbound notifications)

When a service notifies other systems of changes, it uses events:

- **Event shape:** `{ "id", "type", "createdAt", "apiVersion", "data": { "object": { ...resource } } }`, with `type` such as `payment.cancelled`.
- **Signing:** each delivery carries a signature header with a timestamp and an HMAC-SHA256 of `timestamp + "." + rawBody`. Receivers verify it on the raw body and reject timestamps older than 5 minutes.
- **Delivery:** at-least-once, with exponential backoff retries for up to 3 days. Any non-2xx response counts as a failure.
- **Receivers** must deduplicate by event `id`, must not rely on delivery order, and should return 2xx quickly and process asynchronously.

([Stripe: webhooks](https://docs.stripe.com/webhooks), [event object](https://docs.stripe.com/api/events/object))

### 13.7 Stripe patterns we deliberately do not follow

| Stripe | Ours | Why |
|---|---|---|
| Date-based version header (`Stripe-Version: 2026-10-28.endive`) | Path version `/v1` (§2) | Simpler to route and test; we don't run Stripe-scale parallel versions. |
| `snake_case` fields, lowercase currency (`"usd"`) | `camelCase`, uppercase ISO 4217 (`"USD"`) | Consistent with §5 and Jackson defaults. |
| List shape `{object:"list", data, has_more}`; `starting_after` object-ID cursors, default page size 10 | `{items, nextCursor, links}`; opaque cursor, default 20 (§8) | Opaque cursors let us change sort keys without breaking clients. |
| `expand[]` to inline related objects | Not supported | Not needed at POC scale; adds query complexity. |
| Prefixed IDs (`pi_...`) and an `object` type field | Plain UUIDs | Optional nicety; revisit if we expose many resource types. |
