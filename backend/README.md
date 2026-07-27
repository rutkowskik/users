# Users - Backend (Spring Boot)

A REST API for user management with authentication based on JWTs delivered as `HttpOnly` cookies,
refresh token rotation with reuse detection, and sessions stored in Redis.

This is the `backend/` module of the monorepo. The Angular frontend lives in
[`frontend/`](../frontend/README.md); the overall description and architecture are in the
[repository-level README](../README.md).

---

## Stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17, packaged as a WAR (`users_app.war`), Docker image on `eclipse-temurin:21` |
| Framework | Spring Boot 3.5.3 (Web, Security, Data JPA, Data Redis, Actuator) |
| Database | PostgreSQL (Hibernate, HikariCP) |
| Cache / sessions | Redis 7 (Lettuce) |
| Tokens | `com.auth0:java-jwt` 4.5.0 |
| Metrics | Micrometer + Prometheus |
| Other | Lombok, Guava (login attempt cache), javax.mail, Commons Codec (SHA-256) |
| Deployment | Docker, Kubernetes (namespace `users-app`), NGINX Ingress |

---

## Authentication model

Tokens **never reach LocalStorage** - they are issued as `HttpOnly` + `SameSite=Strict` cookies:

| Cookie | TTL (default) | Source of truth |
|---|---|---|
| `ACCESS_TOKEN` | 15 min (`JWT_ACCESS_TOKEN_EXPIRATION`) | the JWT signature alone |
| `REFRESH_TOKEN` | 7 days (`JWT_REFRESH_TOKEN_EXPIRATION`) | the JWT plus a Redis entry |

### Flow

1. **Login** (`POST /api/v1/user/login`) - `AuthenticationManager` verifies the password (BCrypt),
   `TokenServiceImpl` generates both tokens, stores the refresh token metadata in Redis and sets the cookies.
2. **Request authorization** - `JWTAuthorizationFilter` reads `ACCESS_TOKEN` from the cookie, validates
   the signature and populates `SecurityContextHolder`. A missing or invalid token results in `401`
   without continuing down the filter chain.
3. **Refresh** (`POST /api/v1/user/refresh`) - the old refresh token is **revoked and replaced**
   (token rotation). The new pair is written to the cookies.
4. **Reuse detection** - every refresh token belongs to a `tokenFamily` (UUID). Attempting to use a
   token that has already been revoked revokes the **entire family** and raises `TokenReusedException`,
   logging the user out of every device built on that rotation chain.

### Redis key layout

```
refresh_token:<sha256>   -> RefreshTokenData (JSON, TTL = refresh token TTL)
user_tokens:<username>   -> SET of token hashes  (powers "logout all devices")
token_family:<uuid>      -> SET of token hashes  (powers reuse detection)
revoked_token:<sha256>   -> revocation marker (TTL 1h)
revoked_info:<sha256>    -> RefreshTokenData of the revoked token (TTL 24h)
refresh_rate:<username>  -> rate limit counter (TTL 1 min)
```

The tokens themselves are never written to Redis - only their SHA-256 hashes.

### Abuse protection

- `LoginAttemptService` - a Guava cache; 5 failed login attempts within 15 minutes lock the account
  (driven by `AuthenticationFailureListener` / `AuthenticationSuccessListener`).
- `RedisRateLimiterService` - at most 10 token refreshes per minute per user.

---

## Roles and authorities

Roles map to a list of authorities (`pl.krutkowski.users.constant.Authorities`), and endpoints are
guarded with `@PreAuthorize` at the authority level rather than the role level.

| Role | Authorities |
|---|---|
| `ROLE_USER` | `user:read` |
| `ROLE_HR` | `user:read`, `user:update` |
| `ROLE_MANAGER` | `user:read`, `user:update` |
| `ROLE_ADMIN` | `user:read`, `user:create`, `user:update` |
| `ROLE_SUPER_ADMIN` | all of the above plus `user:delete`, `app:monitoring` |

---

## API

Base path: `/api/v1/user`

### Authentication and sessions

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/login` | public | Log in, sets the token cookies |
| POST | `/register` | public | Register; the password is generated and emailed |
| POST | `/refresh` | public (cookie) | Token rotation, `401` on reuse |
| POST | `/logout` | public (cookie) | Revokes the refresh token and clears the cookies |
| POST | `/logout-all` | authenticated | Revokes every session of the user |
| GET | `/me` | authenticated | Details of the currently authenticated user |
| GET | `/sessions` | authenticated | Active sessions (IP, User-Agent, current-session flag) |
| DELETE | `/session/{sessionId}` | authenticated | Revokes one session (`sessionId` = first 8 hash chars) |

### User management

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/list` | authenticated | List users |
| GET | `/find/{username}` | authenticated | User details |
| POST | `/add` | authenticated | Create a user (multipart, optional avatar) |
| POST | `/update` | authenticated | Update a user (multipart) |
| DELETE | `/delete/{username}` | `user:delete` | Delete a user |
| GET | `/resertpassword/{email}` | authenticated | Reset the password; the new one is emailed |
| POST | `/updateProfileImage` | authenticated | Replace the avatar |
| GET | `/image/{username}/{filename}` | public | Avatar from disk |
| GET | `/image/profile/{username}` | public | Fallback avatar (Robohash) |

### Monitoring

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/admin/token-stats` | `app:monitoring` | Counts of active tokens, users, families and revocations |
| GET | `/admin/top-active-users` | `app:monitoring` | Users with the most sessions (`?limit=10`) |
| GET | `/actuator/health`, `/actuator/prometheus` | profile dependent | Health probes and metrics |

Domain errors (`UserNotFoundException`, `EmailExistException`, `UsernameExistException`,
`NotAnImageFileException`, `RateLimitException`, `InvalidTokenException`, `TokenReusedException`)
are mapped centrally in `ExceptionHandling`.

---

## Project structure

```
src/main/java/pl/krutkowski/users/
├── configuration/   SecurityConfiguration, RedisConfiguration, ProfileConfiguration
├── constant/        Authorities, SecurityConstant, FileConstant, EmailConstant, UserConstant
├── controller/      UserController, StatisticController
├── entity/          User (JPA), RefreshTokenData (Redis)
├── enumeration/     Role
├── exception/       ExceptionHandling + domain exceptions
├── filter/          JWTAuthorizationFilter, JwtAccessDeniedHandler, JwtAccessForbiddenEntryPoint
├── listener/        AuthenticationSuccessListener, AuthenticationFailureListener
├── mapper/          UserMapper
├── model/           DTOs, UserPrinciple, HttpResponse, SessionInfo, TokenStats
├── repository/      UserRepository
├── service/         UserService, TokenService, RedisTokenService, RedisRateLimiterService,
│                    TokenMetricsService, LoginAttemptService, EmailService
└── utility/         JTWTokenProvider
```

---

## Configuration profiles

| Profile | File | Characteristics |
|---|---|---|
| `local` | `application-local.yml` | PostgreSQL and Redis on localhost, `ddl-auto: update`, verbose SQL and Security logs, all Actuator endpoints, CORS for `localhost:4200` |
| `test` | `application-test.yml` | Staging environment, `ddl-auto: validate`, secrets from ENV |
| `prod` | `application-prod.yml` | `ddl-auto: validate`, stack traces hidden, WARN-level logging, all secrets from ENV, Hikari pool of 20 connections |

The profile is selected by `SPRING_PROFILES_ACTIVE` (defaults to `local`).

### Environment variables (`test` / `prod`)

```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT        # defaults to 6379
JWT_SECRET                    # required, no default
CORS_ALLOWED_ORIGINS
SERVER_PORT                   # defaults to 8081
```

---

## Running locally

Requirements: JDK 17+, Maven, Docker. Commands are run from the `backend/` directory.

```bash
# 1. Redis
docker compose -f REDIS_DOCKER/docker-compose.yml up -d

# 2. PostgreSQL - database `users`, user `users_app` / `password1`
#    (as configured in application-local.yml)

# 3. Application
./run-local.sh          # or: mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API: `http://localhost:8081`, Actuator: `http://localhost:8081/actuator`.

A frontend served by `ng serve` on `http://localhost:4200` is allowed by the CORS settings of the
`local` profile.

Other startup scripts: `run-test.sh`, `run-prod.sh` (both validate the required environment variables
before starting and abort if any are missing).

---

## Tests

```bash
mvn test
```

`TokenServiceIntegrationTest` (`src/test/java/redis/`) covers token rotation and reuse detection,
backed by `RedisTestConfiguration`.

---

## Docker

```bash
mvn clean package
docker build -t users-backend:latest .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod ... users-backend:latest
```

`../scripts/build-and-push-backend.sh` builds the WAR, tags the image with a timestamp
(`kacperroot/users-backend:YYYYMMDD-HHMMSS` plus `latest`) and pushes it to Docker Hub.

---

## Kubernetes

The manifests live in `k8s/` at the repository root and cover the **whole stack**, the frontend included.

```
k8s/
├── namespace.yaml                       namespace users-app
├── configmap.yaml                       non-sensitive configuration
├── secrets.yaml                         secrets (kept out of git)
├── ingress.yaml                         host users.local, /api -> backend, rest -> frontend (SPA)
├── postgres/postgres-statefulset.yaml
├── redis/redis-statefulset.yaml
├── backend/backend-deployment.yaml      Deployment (2 replicas) + Service backend-service:8081
├── backend/backend-hpa.yaml
├── frontend/frontend-deployment.yaml    Deployment (2 replicas) + Service frontend-service:80
└── frontend/frontend-hpa.yaml
```

The helper scripts live in `scripts/` at the repository root and resolve paths relative to their own
location, so they can be run from any directory - see the full list in the
[repository README](../README.md#scripts).

Local access requires a `users.local` entry in `/etc/hosts` pointing at the Ingress controller.

---

## Known limitations and roadmap

- `SecurityConfiguration.corsConfiguration()` hardcodes the allowed origin (`http://localhost:4200/`)
  and ignores the `cors.*` properties read from the profiles - worth unifying.
- `cookie.setSecure(true)` is commented out in `TokenServiceImpl` - required before serving over HTTPS.
- `k8s/configmap.yaml` sets `SPRING_REDIS_PORT` while the application reads `SPRING_DATA_REDIS_PORT`.
- Avatars are written to the pod's local disk (`FileConstant.USER_FOLDER`) - with multiple replicas
  this needs a shared volume or a move to object storage.
- No pagination or sorting on `GET /list`.
- Refactoring towards a ports-and-adapters architecture (branch `refactor_ports_and_adapters`).
