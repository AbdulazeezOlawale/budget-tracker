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

Budget Tracker is a Spring Boot REST API for managing personal finances. It currently handles user registration with request validation, password hashing, and PostgreSQL persistence.

## Features

- User signup via a single REST endpoint
- Bean Validation for every request field with field-level error messages
- Duplicate-email detection with a `409 Conflict` response
- BCrypt password hashing — plain-text passwords are never stored
- Centralized JSON error responses for validation, conflict, and HTTP status errors
- PostgreSQL persistence through Spring Data JPA
- Stateless Spring Security configuration

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 18 |
| Security | Spring Security + BCrypt |
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
└── service
    └── AuthService.java            # Signup business logic
```

### Request flow

```text
AuthController
  └── validates SignupRequest (@Valid)
      └── AuthService
          ├── checks UserRepository.existsByEmail(...)
          ├── hashes the password with PasswordEncoder
          └── saves the User entity
              └── returns SignupResponse

Errors from any layer
  └── GlobalExceptionHandler → ApiErrorResponse
```

## Getting Started

### Prerequisites

- Java 21
- Docker (for the PostgreSQL container)

The Maven wrapper is included, so a separate Maven installation is not required.

### 1. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 18 on `localhost:5332` with the database `budget_tracker`.

### 2. Run the application

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
```

These values match `docker-compose.yaml`. Change both together if you use a different database setup.

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
  "message": "Account created successfully"
}
```

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
- Only `POST /api/v1/auth/signup` is public; every other route requires authentication.
- Spring Boot logs a generated security password at startup because no custom `UserDetailsService` exists yet. It does not affect the signup flow and will disappear once real authentication is added.
- `spring.jpa.hibernate.ddl-auto=update` is convenient for development. Use database migrations (for example, Flyway or Liquibase) before running in production.

## Testing

```bash
./mvnw test
```

The current test loads the full application context, so PostgreSQL must be running first.

## Further Reading

See [HELP.md](HELP.md) for Spring Boot, JPA, Security, and Validation reference links.
