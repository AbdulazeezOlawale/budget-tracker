<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://shieldcn.dev/header/graph.svg?title=Budget+Tracker&subtitle=Personal+finance+REST+API+%C2%B7+Spring+Boot+4+%C2%B7+Java+21+%C2%B7+PostgreSQL&logo=java&align=left&mode=dark" />
    <img alt="Budget Tracker — Personal finance REST API" src="https://shieldcn.dev/header/graph.svg?title=Budget+Tracker&subtitle=Personal+finance+REST+API+%C2%B7+Spring+Boot+4+%C2%B7+Java+21+%C2%B7+PostgreSQL&logo=java&align=left&mode=light" />
  </picture>
</p>

<p align="center">
  <a href="https://github.com/AbdulazeezOlawale/budget-tracker/stargazers"><img alt="GitHub stars" src="https://shieldcn.dev/github/stars/AbdulazeezOlawale/budget-tracker.svg?variant=secondary" /></a>
  <a href="https://github.com/AbdulazeezOlawale/budget-tracker/network/members"><img alt="GitHub forks" src="https://shieldcn.dev/github/forks/AbdulazeezOlawale/budget-tracker.svg?variant=secondary" /></a>
  <a href="https://github.com/AbdulazeezOlawale/budget-tracker/graphs/contributors"><img alt="Contributors" src="https://shieldcn.dev/github/contributors/AbdulazeezOlawale/budget-tracker.svg?variant=secondary" /></a>
  <a href="https://github.com/AbdulazeezOlawale/budget-tracker/commits/main"><img alt="Last commit" src="https://shieldcn.dev/github/last-commit/AbdulazeezOlawale/budget-tracker.svg?variant=secondary" /></a>
</p>

A personal finance REST API built with Spring Boot 4, Java 21 and PostgreSQL.
This phase of the project implements the complete, production-hardened
**authentication layer**: registration, login, and refresh-token rotation with
token-family reuse detection — the foundation every budget feature
(categories, transactions, reports) will sit on.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Architecture](#architecture)
- [API Reference](#api-reference)
  - [POST /api/v1/auth/register](#post-apiv1authregister)
  - [POST /api/v1/auth/login](#post-apiv1authlogin)
  - [POST /api/v1/auth/refresh](#post-apiv1authrefresh)
  - [Calling authenticated endpoints](#calling-authenticated-endpoints)
- [Error Handling](#error-handling)
- [Security Design Decisions](#security-design-decisions)
- [Database Schema](#database-schema)
- [Roadmap](#roadmap)

---

## Tech Stack

| Concern            | Choice                                   | Why                                                                 |
|--------------------|------------------------------------------|---------------------------------------------------------------------|
| Language / Runtime | Java 21                                  | Records for DTOs, modern text blocks, long-term support             |
| Framework          | Spring Boot 4.0.9                        | First-class security, validation, JPA and actuator integration      |
| Database           | PostgreSQL 18 (via Docker Compose)       | Correct `NUMERIC` money type, `TIMESTAMPTZ`, real constraints        |
| Migrations         | Flyway                                   | Schema is versioned SQL, never generated from entities              |
| ORM                | Spring Data JPA, `ddl-auto=validate`     | Hibernate validates the schema but can never mutate it              |
| Auth               | Stateless JWT (jjwt 0.12.6, HS256)       | No server sessions; horizontal scaling for free                     |
| Passwords          | BCrypt (`BCryptPasswordEncoder`)         | Adaptive, salted, industry standard                                 |
| Rate limiting      | Bucket4j 8.10.1                          | In-process token bucket, no external dependency to start            |
| Boilerplate        | Lombok                                   | `@Builder` / `@RequiredArgsConstructor` keep entities lean          |
| Validation         | Jakarta Bean Validation                  | Constraints declared once on the request records                    |

---

## Getting Started

### Prerequisites

- Java 21+
- Docker (for PostgreSQL) — or any reachable PostgreSQL 16+ instance
- Maven (or the included `./mvnw` wrapper)

### 1. Start the database

```bash
docker compose up -d
```

This runs PostgreSQL 18 with database `budget_tracker` on `localhost:5432`.

### 2. Provide the JWT signing secret

The app refuses to start without `APP_JWT_SECRET`. Generate one and export it:

```bash
export APP_JWT_SECRET="$(openssl rand -base64 32)"
```

(For local convenience you can keep it in `.env.local` and `source` it — that
file is git-ignored and never committed.)

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Flyway applies the migrations on boot; the API is then live on
`http://localhost:8080`. Health check: `GET /actuator/health` (public).

---

## Architecture

Layered Spring Boot application — each layer has exactly one job and
dependencies only ever point downward:

```
HTTP request
   │
   ▼
RateLimitFilter ────────► 429 if the IP exceeded its auth budget
   │
   ▼
JwtAuthenticationFilter ► verifies Bearer token, populates SecurityContext
   │
   ▼
SecurityConfig ─────────► single enforcement point: who may enter
   │
   ▼
Controller (thin) ──────► maps HTTP ⇄ DTO, no business logic
   │
   ▼
Service (@Transactional) ► all rules: hashing, rotation, reuse detection
   │
   ▼
Repository (Spring Data) ► persistence only
   │
   ▼
PostgreSQL (Flyway-versioned schema)
```

```
src/main/java/com/olawale/budgettracker/
├── config/        SecurityConfig          filter chain + entry point
├── controller/    RegisterController      POST /register
│                  LoginController         POST /login, POST /refresh
├── dto/           records                 request/response contracts + validation
├── entity/        User, RefreshToken      JPA entities (never cross the API boundary)
├── exception/     domain exceptions       + GlobalExceptionHandler (one error shape)
├── repository/    UserRepository          RefreshTokenRepository (atomic spend SQL)
├── security/      JwtAuthenticationFilter RateLimitFilter
└── service/       AuthService             JwtService
```

Key structural choices:

- **`AuthService` is the single mint point.** Register, login and refresh all
  funnel through one private `issueTokenPair(...)` — the token contract can
  never drift between flows.
- **All three auth endpoints return the identical `AuthResponse` record.**
  Clients write the token-handling code once.
- **Controllers are thin.** Zero logic in the HTTP layer; everything testable
  lives in services.
- **Filters are deliberately *not* `@Component`s.** They are registered
  manually in `SecurityConfig` so Spring Boot does not also install them as
  global servlet filters (which would double-run them and bypass chain order).

---

## API Reference

Base URL: `http://localhost:8080`
All bodies are `application/json`. All dates/times in errors are UTC (`Instant`).

---

### POST /api/v1/auth/register

Creates a user account **and signs them in immediately** — registration *is*
authentication, so the client gets a usable token pair with one round trip.

**Request body**

| Field      | Type   | Constraints                          |
|------------|--------|--------------------------------------|
| `fullName` | string | required, 2–100 chars                |
| `email`    | string | required, valid email, ≤ 255 chars   |
| `password` | string | required, 8–128 chars                |

```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "password": "s3cret-pass"
}
```

**Response — `201 Created`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "accessTokenExpiresInSeconds": 900,
  "user": {
    "id": 1,
    "fullName": "Jane Doe",
    "email": "jane@example.com"
  }
}
```

**Errors**

| Status | When                                                        |
|--------|-------------------------------------------------------------|
| `400`  | Validation failed — `fieldErrors` names each bad field      |
| `409`  | Email already registered                                    |
| `429`  | Rate limit exceeded for this IP (default 10 req/min)        |

> Note: `409` on register is a deliberate trade-off — account existence is
> public knowledge at signup. Login (the enumeration-attack surface) stays
> timing-safe and generic; see [Security Design Decisions](#security-design-decisions).

---

### POST /api/v1/auth/login

Exchanges email + password for a fresh access/refresh token pair. Each login
starts a **new refresh-token family** (a new session lineage), so a stolen old
family can never collide with a new legitimate session.

**Request body**

| Field      | Type   | Constraints                        |
|------------|--------|------------------------------------|
| `email`    | string | required, valid email, ≤ 255 chars |
| `password` | string | required, ≤ 128 chars              |

```json
{
  "email": "jane@example.com",
  "password": "s3cret-pass"
}
```

**Response — `200 OK`** — same `AuthResponse` shape as register.

**Errors**

| Status | When                                                             |
|--------|------------------------------------------------------------------|
| `400`  | Validation failed                                                |
| `401`  | Wrong email **or** wrong password — one indistinguishable answer |
| `429`  | Rate limit exceeded                                              |

---

### POST /api/v1/auth/refresh

Spends the presented refresh token and rotates it. This is the security-critical
endpoint; the full rules live in
[`AuthService.refresh`](src/main/java/com/olawale/budgettracker/service/AuthService.java).

**Request body**

| Field          | Type   | Constraints |
|----------------|--------|-------------|
| `refreshToken` | string | required    |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response — `200 OK`**

A **new** access token *and* a **new** refresh token (same family). The refresh
token you just sent is now permanently dead — using it a second time revokes
the entire family.

**Errors**

| Status | When                                                                        |
|--------|-----------------------------------------------------------------------------|
| `400`  | Validation failed                                                           |
| `401`  | Token malformed / expired / wrong type / unknown / **reuse detected**       |
| `429`  | Rate limit exceeded                                                         |

**What makes a refresh fail (in check order):**

1. JWT signature or expiry invalid, or `type` claim is not `refresh`
   (an access token cannot be used here) — rejected statelessly, no DB hit.
2. Token hash not found in `refresh_tokens` (e.g. purged).
3. Token already `revoked` → **reuse detected** → the whole token family is
   burned and the event is logged at `ERROR` level.
4. Row past `expires_at` → spent anyway, then rejected.
5. Concurrent double-spend (two requests raced with the same token) → the
   loser's atomic compare-and-spend affects 0 rows → treated as reuse,
   family burned.

---

### Calling authenticated endpoints

Every route other than the three auth endpoints and `/actuator/health`
requires the access token:

```bash
curl -H "Authorization: Bearer <accessToken>" http://localhost:8080/api/v1/...
```

Missing/invalid token → `401` with `WWW-Authenticate: Bearer`.

**Token lifetimes** (configurable in `application.properties`):

| Token            | TTL              | Where verified                          |
|------------------|------------------|------------------------------------------|
| Access (`type=access`)  | 15 minutes | Stateless — signature + claims only |
| Refresh (`type=refresh`) | 7 days    | Signature **plus** DB row lookup     |

The refresh token carries a random `jti` (so two tokens minted in the same
second are never byte-identical → never hash-identical) and an `fid` claim —
the **family id** shared by every rotated successor, which is what makes
whole-lineage revocation possible.

---

## Error Handling

Every error leaves the system in **one shape**, produced by
`GlobalExceptionHandler` (`@RestControllerAdvice`):

```json
{
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 128"
  },
  "timestamp": "2026-11-20T21:30:00Z"
}
```

| Exception                     | Status | Message policy                                   |
|-------------------------------|--------|---------------------------------------------------|
| `MethodArgumentNotValidException` | `400` | Per-field validation messages                    |
| `EmailAlreadyExistsException` | `409`  | Names the email (the caller just supplied it)     |
| `InvalidCredentialsException` | `401`  | Generic — never says whether email or password failed |
| `InvalidTokenException`       | `401`  | Generic — never distinguishes why the token died  |
| Unauthenticated request       | `401`  | `Authentication required` + `WWW-Authenticate`    |
| Rate limited                  | `429`  | `Too many requests` + `Retry-After: 60`           |

Anything unmapped falls through to Spring's default `5xx` handling — no
exception message or stack trace is ever serialized to clients.

---

## Security Design Decisions

The choices below are the real substance of this phase. Each one exists to
defeat a specific attack or failure mode.

### 1. Store refresh-token **hashes**, never raw tokens

`refresh_tokens.token_hash` holds the SHA-256 hex of the JWT, not the JWT.
A database leak therefore yields **zero API-usable credentials** — an attacker
would still have to forge a validly signed token.

### 2. Rotate-on-refresh with token **families** and reuse detection

A refresh token is single-use. Presenting an already-spent token is either a
buggy client double-fire or a **stolen token** — and the two are
indistinguishable, so the safe response is to burn the entire family
(`revokeAllInFamily`): every live descendant dies, including tokens the
attacker already rotated to. This converts silent token theft into a loud,
total session invalidation (`ERROR` log line, easy to alert on).

### 3. Atomic compare-and-spend closes the double-spend race

A naive *find → check → save* lets two concurrent requests both "win" with the
same token. `RefreshTokenRepository.spendToken` is a single conditional
`UPDATE ... WHERE token_hash = ? AND revoked = false`; the row lock makes it a
compare-and-swap. A return of `0` means someone else spent it first → reuse
path.

### 4. `@Transactional(noRollbackFor = InvalidTokenException.class)`

The reuse-detection writes **must survive the exception** that reports the
attack. With default rollback semantics, throwing `InvalidTokenException`
would undo the family burn and the stolen tokens would live on. This is a
subtle bug class worth remembering: security-critical writes on failure paths
need explicit transaction semantics.

### 5. Timing-attack equalization on login

Unknown-email logins still run a BCrypt comparison against a real pre-computed
hash of a dummy password. Both failure branches burn ~100 ms, so response
**time** cannot enumerate which emails have accounts.

### 6. One indistinguishable `401` for wrong email and wrong password

`InvalidCredentialsException` carries a single generic message regardless of
which half failed. Combined with #5, login leaks nothing about account
existence. (Register intentionally returns `409` — signup is a UX-first flow;
the trade-off is documented above.)

### 7. Per-IP token-bucket rate limiting on auth endpoints only

Bucket4j, 10 requests/minute default, applied *before* JWT verification so
brute-forcers are throttled before the server spends CPU on signature checks
or BCrypt. `429` + `Retry-After` returned as JSON.
**Known limitation (documented in code):** buckets are in-JVM — per-instance
and reset on restart. Multi-instance deployments must move this to the gateway
or a shared store (bucket4j + Hazelcast/Redis).

### 8. Truly stateless security chain

`SessionCreationPolicy.STATELESS`, CSRF disabled (no cookies are ever used for
auth — the token lives in the `Authorization` header, which CSRF cannot
reach), and `httpBasic`/`formLogin` left off. Disabling httpBasic removes
Spring's default 401 door, so a custom `authenticationEntryPoint` restores
correct `401 + WWW-Authenticate: Bearer` semantics (otherwise anonymous
requests would wrongly get `403`).

### 9. The JWT filter never rejects on its own

A missing/invalid token simply leaves the request anonymous; the single
`authorizeHttpRequests` rule set in `SecurityConfig` produces the 401. One
enforcement point beats scattered `response.sendError(...)` calls — you can
audit "who may enter" by reading one file.

### 10. Type-claim separation of access vs refresh tokens

Both tokens are signed with the same HS256 key, so each carries a `type`
claim (`access` / `refresh`). Verification always asserts the expected type:
an access token can never impersonate a refresh token and vice versa.

### 11. Fail-all, never leak: `JwtService.verify` returns `Optional.empty()`

Bad signature, expiry, malformed structure — every JWT failure collapses into
one indistinguishable empty result. Verification can never throw a `500` that
reveals *why* a token died.

### 12. Email normalization in exactly one place

`AuthService.normalizeEmail` (trim + lowercase, `Locale.ROOT`) is applied at
every entry point — register, login, refresh lookups. Normalizing in two
places differently is how accounts become unreachable; one rule, one home.

### 13. Schema owned by Flyway, validated by Hibernate

`ddl-auto=validate` means Hibernate will refuse to boot if the entities and
migrations disagree, but can never mutate the schema. Money is
`NUMERIC(15,2)` (never floating point), timestamps are `TIMESTAMPTZ`, and
invariants live in the database: `CHECK (amount > 0)`, `CHECK (type IN
('INCOME','EXPENSE'))`, and a composite foreign key
`(category_id, type) → categories (id, type)` that makes it **impossible** to
attach an EXPENSE transaction to an INCOME category, no matter what application
bug occurs.

### 14. Secrets out of the repo

`app.jwt.secret=${APP_JWT_SECRET}` — the environment variable is mandatory at
startup; `.env*` files are git-ignored. No default fallback: a forgotten secret
fails loudly, never silently runs on a known key.

### Other deliberate choices

- **BCrypt over Argon2/SCrypt** — universally available in Spring Security,
  tuned cost, and no parameter-tuning footgun for a first production system.
- **HS256 symmetric signing over RS256** — a single-service architecture has
  one verifier; key distribution for asymmetric signing only pays off once
  other services must verify tokens independently. The secret is 32 random
  bytes (`openssl rand -base64 32`).
- **Short access token (15 min), long refresh (7 days)** — the classic trade:
  stolen access tokens expire quickly and statelessly; the stateful layer
  (DB-backed rotation + family revocation) is only paid for where revocation
  actually matters.
- **`DispatcherType.ERROR` permitted in the security chain** — without it,
  Tomcat's internal forward to `/error` re-enters the chain anonymously and
  every 404 on a protected path would be misreported as a 401.
- **`@ManyToOne(LAZY)` from `RefreshToken` to `User`** — refresh verification
  usually only needs the claims already in the token; the user row is loaded
  on demand (and re-fetched deliberately after the persistence context is
  cleared by the bulk spend update).

---

## Database Schema

Applied by Flyway from `src/main/resources/db/migration/`.

### V1 — core tables

```
users
├── id            BIGINT identity PK
├── full_name     VARCHAR(100)  NOT NULL
├── email         VARCHAR(255)  NOT NULL UNIQUE
├── password_hash VARCHAR(255)  NOT NULL          -- BCrypt, never the password
└── created_at    TIMESTAMPTZ   NOT NULL

categories                                       -- reserved for the next phase
├── id        BIGINT identity PK
├── user_id   BIGINT → users(id)
├── name      VARCHAR(100) NOT NULL
├── type      VARCHAR(20)  CHECK IN ('INCOME','EXPENSE')
├── UNIQUE (user_id, name, type)
└── UNIQUE (id, type)      -- target of the transaction type-lock FK

transactions                                     -- reserved for the next phase
├── id               BIGINT identity PK
├── user_id          BIGINT → users(id)
├── category_id      BIGINT, (category_id,type) → categories(id,type)
├── type             VARCHAR(20) CHECK IN ('INCOME','EXPENSE')
├── amount           NUMERIC(15,2) CHECK (amount > 0)
├── description      TEXT
├── transaction_date DATE NOT NULL
└── indexes on user_id, category_id, transaction_date
```

### V2 — refresh token registry

```
refresh_tokens
├── id         BIGINT identity PK
├── user_id    BIGINT → users(id)
├── token_hash VARCHAR(64) NOT NULL UNIQUE        -- SHA-256 hex, never the raw JWT
├── family_id  UUID NOT NULL                      -- rotation lineage
├── expires_at TIMESTAMPTZ NOT NULL
├── revoked    BOOLEAN NOT NULL DEFAULT FALSE
└── indexes on family_id, user_id
```

---

## Roadmap

- [ ] Categories CRUD API (schema already migrated and FK-locked)
- [ ] Transactions CRUD API with pagination and date-range queries
- [ ] Monthly summary / report endpoints
- [ ] Logout endpoint (revoke current refresh family) + admin token revocation
- [ ] Scheduled purge of expired/revoked `refresh_tokens` rows
- [ ] Test suite: MockMvc contract tests + Testcontainers integration tests
- [ ] Shared-store rate limiting when moving to multi-instance deployment

---

## Project Metadata

- Group / artifact: `com.olawale:budget-tracker`
- Package: `com.olawale.budgettracker`
- Build: `./mvnw clean verify`
- Spring Boot: 4.0.9 · Java: 21
