# API Guidelines

This document defines the REST API design standards for all services in the `payment-poc` monorepo. These guidelines are derived from and aligned with two industry-published standards:

- **Microsoft Azure REST API Guidelines** — https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md
- **PayPal REST API Style Guide** — https://github.com/paypal/api-standards/blob/master/api-style-guide.md

Where the two standards differ, the more explicit rule is preferred. Payment-specific extensions are noted.

---

## 1. URL Structure

```
https://<host>/<service-root>/v{N}/<resource-collection>[/<resource-id>][/<sub-collection>]
```

Examples:

```
GET  /payments/v1/payments
GET  /payments/v1/payments/{paymentId}
POST /payments/v1/payments/{paymentId}/captures
```

### Rules

- Path segments use **lowercase kebab-case**: `/payment-methods`, not `/paymentMethods`.
- Collection names are **plural nouns**: `/payments`, not `/payment`.
- Do not use verbs in URLs for CRUD operations. Use HTTP method semantics.
- Action endpoints that cannot be expressed as a resource may use a verb sub-resource: `/payments/{id}/cancel`.
- Do not expose internal identifiers (auto-increment IDs) — use UUIDs.

---

## 2. Versioning

- API versions appear **in the URL path**: `/v1/payments`, `/v2/payments`.
- The version segment is a single integer (`v1`, `v2`). No minor versions in the path.
- Breaking changes require a new major version. The old version is supported for a minimum deprecation period of 12 months.
- A breaking change is any of: removing a field, changing a field type, changing HTTP method/status semantics, removing an endpoint.
- Adding optional fields or new endpoints is **non-breaking**.

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
| `409 Conflict` | Duplicate request (use with idempotency key collision), state conflict |
| `422 Unprocessable Entity` | Syntactically valid but semantically invalid (e.g. insufficient funds) |
| `429 Too Many Requests` | Rate limit exceeded; include `Retry-After` header |
| `500 Internal Server Error` | Unexpected server fault |
| `503 Service Unavailable` | Temporary unavailability; include `Retry-After` header |

---

## 5. Request and Response Format

- All request and response bodies are **JSON** (`Content-Type: application/json`).
- JSON field names use **camelCase**: `paymentId`, `createdAt`, `amount`.
- Timestamps use **ISO 8601 / RFC 3339** in UTC: `"2024-03-15T10:30:00Z"`.
- Monetary amounts are **strings** to avoid floating-point precision issues: `"amount": "19.99"`. Always include `currency` (ISO 4217 code).
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
  "amount": "49.99",
  "currency": "USD",
  "links": [
    { "rel": "self",    "href": "/payments/v1/payments/pay_8f3a2b1c", "method": "GET" },
    { "rel": "refund",  "href": "/payments/v1/payments/pay_8f3a2b1c/refunds", "method": "POST" }
  ]
}
```

---

## 6. HATEOAS Links

Every resource response **should** include a `links` array for discoverability (following PayPal's convention):

```json
"links": [
  { "rel": "self",   "href": "/payments/v1/payments/{id}", "method": "GET" },
  { "rel": "update", "href": "/payments/v1/payments/{id}", "method": "PATCH" },
  { "rel": "cancel", "href": "/payments/v1/payments/{id}/cancel", "method": "POST" }
]
```

- `rel` describes the relationship. Use `self` for the canonical URL of the resource.
- `href` is an absolute path (no host). Clients must not construct URLs by string manipulation.

---

## 7. Error Response Format

All error responses use a consistent envelope:

```json
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "The payment could not be processed because the account balance is insufficient.",
    "target": "amount",
    "details": [
      {
        "code": "BALANCE_TOO_LOW",
        "message": "Available balance is 10.00 USD; requested 49.99 USD.",
        "target": "amount"
      }
    ],
    "debugId": "dbg_4c2a1f8e"
  }
}
```

| Field | Required | Description |
|---|---|---|
| `error.code` | Yes | Machine-readable, stable, SCREAMING_SNAKE_CASE. Part of the API contract — never change. |
| `error.message` | Yes | Human-readable explanation. May change between versions. |
| `error.target` | No | Field or parameter name that caused the error. |
| `error.details` | No | Array of sub-errors for multi-field validation failures. |
| `error.debugId` | Yes | Opaque ID for support tracing. Correlates to server logs. |

- Include `X-Error-Code: INSUFFICIENT_FUNDS` as a response header matching `error.code` (Microsoft convention).
- Validation errors that affect multiple fields return a single `400` with all failing fields in `error.details`.

---

## 8. Pagination

All collection endpoints **must** support pagination. Never return an unbounded list.

```json
{
  "items": [ ... ],
  "totalCount": 142,
  "pageSize": 20,
  "nextCursor": "eyJpZCI6InBheV8xMjMifQ==",
  "links": [
    { "rel": "self", "href": "/payments/v1/payments?cursor=...&pageSize=20", "method": "GET" },
    { "rel": "next", "href": "/payments/v1/payments?cursor=eyJpZCI6InBheV8xMjMifQ==&pageSize=20", "method": "GET" }
  ]
}
```

- Use **cursor-based pagination** (not offset). Cursor is an opaque, base64-encoded string.
- Default page size: `20`. Maximum: `100`.
- When there is no next page, omit `nextCursor` and the `next` link.
- Query parameters: `pageSize` (integer), `cursor` (string).

---

## 9. Idempotency

**All state-mutating operations must be idempotent** (Microsoft requirement). This is especially critical for payments.

- Clients **must** include a `PayPal-Request-Id` (or `Idempotency-Key`) header on all `POST`, `PUT`, and `PATCH` requests.
- The header value is a client-generated UUID (v4).
- The server caches the response for the same key for a minimum of 24 hours.
- A duplicate request with the same key returns the **original cached response** (`200 OK`), not `409`.
- If the same key is used with a different request body, return `409 Conflict`.

```
POST /payments/v1/payments
PayPal-Request-Id: 7f9d2b1a-3c4e-4f5a-b6d7-8e9f0a1b2c3d
```

---

## 10. Filtering, Sorting, and Searching

```
GET /payments/v1/payments?status=CAPTURED&currency=USD
GET /payments/v1/payments?sort=createdAt:desc,amount:asc
GET /payments/v1/payments?q=john
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
