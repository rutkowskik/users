# Users App - monorepo

A user management application: registration, login, roles, an admin panel and session management.
Authentication is based on JWTs delivered as `HttpOnly` cookies, with refresh token rotation and
reuse detection.

This repository holds both services along with the shared deployment setup.

---

## Contents

| Directory | Description | Details |
|---|---|---|
| `backend/` | REST API - Spring Boot 3.5, Java 17, PostgreSQL, Redis | [backend/README.md](backend/README.md) |
| `frontend/` | Web panel - Angular 17 + SSR, Bootstrap 5, Nginx | [frontend/README.md](frontend/README.md) |
| `k8s/` | Kubernetes manifests for the whole stack (namespace, config, databases, both services, Ingress) | - |
| `helm/` | Helm chart skeleton (currently empty) | - |
| `scripts/` | Build / deploy / diagnostics scripts | - |

---

## Architecture

```
                          NGINX Ingress (host: users.local)
                                     |
                 /api/*  -------------+-------------  /*
                    |                                  |
            backend-service:8081              frontend-service:80
                    |                                  |
          users-backend (2 replicas)         users-frontend (2 replicas)
          Spring Boot / WAR / Tomcat          Angular build + Nginx
                    |
        +-----------+-----------+
        |                       |
   PostgreSQL                 Redis
   user data              sessions, refresh tokens,
                          rate limiting, metrics
```

The frontend stores no tokens. The backend issues `ACCESS_TOKEN` (15 min) and `REFRESH_TOKEN` (7 days)
as `HttpOnly` + `SameSite=Strict` cookies. On a `401` response the Angular `AuthInterceptor` performs a
single `POST /api/v1/user/refresh` and retries the original request. On every refresh the backend
revokes the old refresh token and issues a new one (token rotation); attempting to reuse an already
revoked token wipes the entire token family and terminates all sessions derived from it.

---

## Quick start (local)

Requirements: JDK 17+, Maven, Node.js 20+, Docker.

```bash
# 1. Redis
docker compose -f backend/REDIS_DOCKER/docker-compose.yml up -d

# 2. PostgreSQL - database `users`, user `users_app` / `password1`
#    (see backend/src/main/resources/application-local.yml)

# 3. Backend -> http://localhost:8081
./backend/run-local.sh

# 4. Frontend -> http://localhost:4200
cd frontend && npm install --legacy-peer-deps && npm start
```

The backend's `local` profile allows the `http://localhost:4200` origin together with
`allow-credentials`, without which the browser will not store the cookies.

Note: `frontend/src/environments/environment.ts` points at the host `backend`. To work on plain
localhost, set it to `http://localhost:8081/api/v1`.

---

## Scripts

Every script resolves paths relative to its own location, so it can be run from any directory.

| Script | Purpose |
|---|---|
| `scripts/build-and-push-backend.sh` | Maven package + image build + push to Docker Hub |
| `scripts/build-and-push-frontend.sh` | Production Angular build + image build + push |
| `scripts/kubectl-apply.sh` | Applies manifests in order, waits for the databases and the rollout |
| `scripts/deploy.sh` | Full pipeline: build, push and deploy |
| `scripts/health-check.sh` | Pod and service status plus `/actuator/health` |
| `scripts/logs.sh` | Tail logs from the `users-app` namespace |
| `scripts/generate-secrets.sh` | Generates `k8s/secrets.yaml` (kept out of git) |

`backend/run-local.sh`, `run-test.sh` and `run-prod.sh` start the backend in the given profile; the
`test` and `prod` variants validate the required environment variables before starting.

---

## Deployment

```bash
./scripts/generate-secrets.sh      # once
./scripts/build-and-push-backend.sh
./scripts/build-and-push-frontend.sh
./scripts/kubectl-apply.sh
./scripts/health-check.sh
```

Manifests are applied to the `users-app` namespace. Local access requires a `users.local` entry in
`/etc/hosts` pointing at the Ingress controller.

Secrets (`k8s/secrets.yaml`) are not under version control - generate them with
`scripts/generate-secrets.sh`.

---

## Repository structure

```
.
├── backend/            Spring Boot: pom.xml, src/, Dockerfile, run-*.sh, REDIS_DOCKER/
├── frontend/           Angular: package.json, src/, Dockerfile, nginx.conf, server.ts
├── k8s/
│   ├── namespace.yaml, configmap.yaml, secrets.yaml, ingress.yaml
│   ├── backend/        backend-deployment.yaml, backend-hpa.yaml
│   ├── frontend/       frontend-deployment.yaml, frontend-hpa.yaml
│   ├── postgres/       postgres-statefulset.yaml
│   └── redis/          redis-statefulset.yaml
├── helm/users-app/     chart skeleton (files are empty)
└── scripts/            build, deploy, diagnostics
```

---

## Repository history

This monorepo was created by merging two repositories:

- `rutkowskik/users` - the backend, which became the monorepo root; its code moved into `backend/`
- `rutkowskik/usersapp` - the frontend, imported with `git subtree` with its commit history intact

The history of both projects is available in `git log`.

---

## Known limitations

Shared across both services:

- `SecurityConfiguration.corsConfiguration()` hardcodes the allowed origin (`http://localhost:4200/`)
  and ignores the `cors.*` properties defined in the profiles.
- `cookie.setSecure(true)` is commented out in `TokenServiceImpl` - required before serving over HTTPS.
- `k8s/configmap.yaml` sets `SPRING_REDIS_PORT` while the application reads `SPRING_DATA_REDIS_PORT`.
- No pagination or sorting on the user list (needs changes on both sides).
- The backend exposes `GET /user/sessions`, `DELETE /user/session/{id}` and `POST /user/logout-all`,
  but the frontend has no active-sessions view yet.

Detailed lists live in the README of each service.
