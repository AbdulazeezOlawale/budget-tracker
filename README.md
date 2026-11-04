<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://shieldcn.dev/header/graph.svg?title=Budget+Tracker&subtitle=A+straightforward+REST+API+for+tracking+personal+finances&logo=springboot&theme=green&align=center&mode=dark" />
    <img alt="Budget Tracker" src="https://shieldcn.dev/header/graph.svg?title=Budget+Tracker&subtitle=A+straightforward+REST+API+for+tracking+personal+finances&logo=springboot&theme=green&align=center&mode=light" />
  </picture>
</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/21/"><img alt="Java 21" src="https://shieldcn.dev/badge/Java-21-ED8B00?logo=openjdk&variant=secondary" /></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot 4.1.2" src="https://shieldcn.dev/badge/Spring_Boot-4.1.2-6DB33F?logo=springboot&variant=secondary" /></a>
  <a href="https://www.postgresql.org/"><img alt="PostgreSQL 18" src="https://shieldcn.dev/badge/PostgreSQL-18-4169E1?logo=postgresql&variant=secondary" /></a>
  <a href="https://maven.apache.org/"><img alt="Built with Maven" src="https://shieldcn.dev/badge/Built_with-Maven-C71A36?logo=apachemaven&variant=secondary" /></a>
</p>

Budget Tracker is a Spring Boot REST API for managing personal finances. It currently handles user registration with request validation, password hashing, PostgreSQL persistence, and JWT authentication — a signed access token is issued immediately on signup (auto-login).

## Features

- User signup via a single REST endpoint
- Bean Validation for every request field with field-level error messages
- Duplicate-email detection with a `409 Conflict` response
- BCrypt password hashing — plain-text passwords are never stored
- JWT (HMAC) access token issued on registration and returned in the response
- Stateless bearer-token authentication filter protecting every non-public route
- Centralized JSON error responses for validation, conflict, and HTTP status errors
- PostgreSQL persistence through Spring Data JPA

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 18 (Docker Compose) |
| Security | Spring Security + BCrypt |
| Auth tokens | JJWT 0.13.0 (HMAC-signed JWTs) |
| Validation | Jakarta Bean Validation |
| Build | Maven (wrapper included) |

## Project Structure

```text
src/main/java/com/example/budgettracker
├── BudgetTrackerApplication.java   # Application entry point
├── config
│   ├── PasswordConfig.java         # BCrypt PasswordEncoder bean
│   └── SecurityConfig.java         # Stateless security rules
├── controller
│   └── AuthController.java         # POST /api/v1/auth/signup
├── dto
│   ├── request
│   │   └── SignupRequest.java      # Signup payload + validation rules
│   └── response
│       ├── SignupResponse.java     # Successful signup payload
│       └── ApiErrorResponse.java   # Standard error payload
├── entity
│   └── User.java                   # users table mapping
├── enums
│   └── ContactMethod.java          # EMAIL, PHONE, WHATSAPP
├── exception
│   └── GlobalExceptionHandler.java # Maps exceptions to JSON responses
├── repository
│   └── UserRepository.java         # JPA repository
├── security
│   └── JwtAuthFilter.java          # Bearer-token validation filter
└── service
    ├── AuthService.java            # Signup business logic
    └── JwtService.java             # JWT generation and parsing
```

### Request flow

```text
AuthController
  └── validates SignupRequest (@Valid)
      └── AuthService
          ├── checks UserRepository.existsByEmail(...)
          ├── hashes the password with PasswordEncoder
          ├── saves the User entity
          ├── generates a JWT with JwtService
          └── returns SignupResponse (includes the token)

Every other request
  └── JwtAuthFilter
      ├── reads the Authorization: Bearer <token> header
      ├── validates the signature and expiry via JwtService
      └── populates the SecurityContext on success

Errors from any layer
  └── GlobalExceptionHandler → ApiErrorResponse
```

## Getting Started

### Prerequisites

- Java 21
- Docker (for the PostgreSQL container)

The Maven wrapper is included, so a separate Maven installation is not required.

### 1. Configure the JWT secret

Create a `.env` file in the project root (it is gitignored) with a base64 secret of at least 32 bytes:

```bash
echo "JWT_SECRET=$(head -c 48 /dev/urandom | base64)" > .env
```

Spring loads it via `spring.config.import=optional:file:.env[.properties]`. A real environment variable (`export JWT_SECRET=...`) works too and takes precedence.

### 2. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 18 on `localhost:5332` (host port mapped to the container's 5432) with the database `budget_tracker`. Port 5332 is used deliberately so the container does not collide with a system PostgreSQL on the default 5432.

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

### Configuration

Database settings live in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5332/budget_tracker
spring.datasource.username=orion
spring.datasource.password=password

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

spring.config.import=optional:file:.env[.properties]
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-minutes=30
```

The datasource values match `docker-compose.yaml`. The JWT secret is **never committed** — it is resolved from `JWT_SECRET` in `.env` or the environment. Change datasource settings in both files together if you use a different database setup.

## API Reference

### `POST /api/v1/auth/signup`

Creates a new account.

**Request body**

```json
{
  "fullName": "Abdulazeez Olamide",
  "email": "abdulazeez@example.com",
  "phoneNumber": "+2348012345678",
  "preferredContactMethod": "WHATSAPP",
  "password": "MyStrongPass123!"
}
```

**Validation rules**

| Field | Rules |
|---|---|
| `fullName` | Required, max 120 characters |
| `email` | Required, valid email, max 254 characters; trimmed and lowercased before saving |
| `phoneNumber` | Required, E.164 format, e.g. `+2348012345678` |
| `preferredContactMethod` | Required, one of `EMAIL`, `PHONE`, `WHATSAPP` |
| `password` | Required, 8–72 characters, stored as a BCrypt hash |

**Success — `201 Created`**

```json
{
  "id": 1,
  "fullName": "Abdulazeez Olamide",
  "email": "abdulazeez@example.com",
  "phoneNumber": "+2348012345678",
  "preferredContactMethod": "WHATSAPP",
  "createdAt": "2026-10-30T21:00:00Z",
  "tokenType": "Bearer",
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "expiresIn": 1800,
  "message": "Account created successfully"
}
```

The response includes a signed JWT so the client is logged in immediately (auto-login pattern). Claims: `sub` (user id), `email`, `iat`, `exp`. The token expires after `app.jwt.expiration-minutes` (30 by default).

> Note: if email verification is added later, the token should no longer be issued at signup — an unverified user must not receive an authenticated session.

### Authenticating subsequent requests

Send the token as a bearer header on every request to a protected route:

```bash
curl http://localhost:8080/api/v1/... \
  -H "Authorization: Bearer <token>"
```

`JwtAuthFilter` validates the signature and expiry, then sets the security context. Missing or invalid tokens are rejected before reaching any controller.

**Validation error — `400 Bad Request`**

```json
{
  "timestamp": "2026-10-30T22:02:14Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": {
    "email": "Enter a valid email address",
    "password": "Password must contain between 8 and 72 characters"
  }
}
```

**Duplicate email — `409 Conflict`**

```json
{
  "timestamp": "2026-10-30T22:02:14Z",
  "status": 409,
  "error": "Conflict",
  "message": "An account with this email already exists",
  "fieldErrors": null
}
```

Errors handled by `GlobalExceptionHandler` use the same `ApiErrorResponse` shape: `timestamp`, `status`, `error`, `message`, and optional `fieldErrors`.

## Security Notes

- Sessions are stateless and CSRF is disabled because the API does not use browser sessions.
- Only `POST /api/v1/auth/signup` is public; every other route requires a valid bearer token.
- JWTs are signed with HMAC (algorithm selected by key size, HS384 for the recommended 48-byte secret). The secret must decode to at least 256 bits or startup fails with `WeakKeyException`.
- Tokens are short-lived (30 minutes) and there is no refresh mechanism yet — add refresh tokens before relying on this in production.
- The signing secret lives only in `.env` / the environment and is never committed.
- `spring.jpa.hibernate.ddl-auto=update` is convenient for development. Use database migrations (for example, Flyway or Liquibase) before running in production.

## Testing

```bash
./mvnw test
```

The current test loads the full application context, so PostgreSQL must be running first.

## Further Reading

See [HELP.md](HELP.md) for Spring Boot, JPA, Security, and Validation reference links.
