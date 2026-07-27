# Users App - Frontend (Angular)

A user management panel: login, registration, user list, editing, locking and deleting accounts, and
avatar upload. Authentication is based on `HttpOnly` cookies with automatic token refresh.

This is the `frontend/` module of the monorepo. The Spring Boot backend lives in
[`backend/`](../backend/README.md); the overall description and architecture are in the
[repository-level README](../README.md).

---

## Stack

| Layer | Technology |
|---|---|
| Framework | Angular 17 (NgModule-based, not standalone) |
| SSR | Angular SSR + Express (`server.ts`), prerendering enabled |
| UI | Bootstrap 5.3, Font Awesome 4.7 |
| Notifications | angular-notifier 14 |
| HTTP | `HttpClient` + `AuthInterceptor`, `withCredentials: true` |
| Language | TypeScript 5.3, RxJS 7.8 |
| Tests | Karma + Jasmine |
| Production serving | Nginx 1.25 (multi-stage image: `node:20-alpine` -> `nginx:1.25-alpine`) |

---

## Authentication

The application **stores no tokens** - `ACCESS_TOKEN` and `REFRESH_TOKEN` are `HttpOnly` cookies set
by the backend and unreachable from JavaScript. `localStorage` only holds a cache of the user profile
(key `user`) and of the user list (`users`) - convenience data, not credentials.

### `AuthInterceptor`

- Adds `withCredentials: true` to every request so the cookies travel with it.
- Lets public endpoints (`/user/login`, `/user/logout`, `/user/register`, `/user/me`, `/user/refresh`)
  through without any retry logic.
- On a `401` response it issues a single `POST /user/refresh` (the `isRefreshing` flag prevents a loop)
  and, once rotation succeeds, **retries the original request**. If the refresh fails it redirects
  to `/login`.

### Guards

| Guard | Protects | Behaviour |
|---|---|---|
| `AuthenticationGuard` | `/user/management` | Verifies the session via `GET /user/me`; with no session redirects to `/login` with a `returnUrl` and shows a notification |
| `LoginGuard` | `/login`, `/register` | Redirects an already authenticated user straight to `/user/management` |

`AuthenticationService.isLoggedIn()` returns an `Observable<boolean>` - it checks the in-memory cache
first and falls back to asking the backend for `/user/me`. The `checkingAuth` flag guards against
concurrent requests.

---

## Routing

| Path | Component | Guard |
|---|---|---|
| `/login` | `LoginComponent` | `LoginGuard` |
| `/register` | `RegisterComponent` | `LoginGuard` |
| `/user/management` | `UserComponent` | `AuthenticationGuard` |
| `/` | redirects to `/login` | - |
| `/not-found` | `NotFoundComponent` | - |
| `**` | redirects to `/not-found` | - |

---

## Project structure

```
frontend/
├── src/app/
│   ├── login/            login screen
│   ├── register/         registration
│   ├── user/             user management panel (list, modals, avatar upload)
│   ├── not-found/        404 page
│   ├── common/
│   │   └── loading-spinner/
│   ├── guard/            AuthenticationGuard, LoginGuard
│   ├── interceptor/      AuthInterceptor (withCredentials + refresh on 401)
│   ├── service/          AuthenticationService, UserService, NotificationService
│   ├── model/            User, CustomHttpResponse, FileUploadStatus
│   ├── enum/             Role, HeaderType, NotificationType
│   ├── app-routing.module.ts
│   ├── app.module.ts
│   └── app.module.server.ts
├── src/environments/     environment.ts (dev), environment.prod.ts (production)
├── server.ts             Express server for SSR
├── nginx.conf            Nginx configuration for the production image
├── Dockerfile            multi-stage build
└── docker-compose.yml
```

The image build-and-publish script lives outside this module, next to its backend counterpart:
`scripts/build-and-push-frontend.sh`.

---

## Backend endpoints used

The base URL comes from `environment.apiUrl` (`/api/v1`).

| Method | Endpoint | Usage |
|---|---|---|
| POST | `/user/login` | login; the backend sets the cookies |
| POST | `/user/register` | registration |
| POST | `/user/logout` | log out and clear the cookies |
| POST | `/user/refresh` | token rotation (issued by the interceptor) |
| GET | `/user/me` | session check performed by the guards |
| GET | `/user/list` | user list |
| POST | `/user/add`, `/user/update` | create / edit a user (`FormData`) |
| POST | `/user/updateProfileImage` | avatar upload with progress reporting |
| DELETE | `/user/delete/{username}` | delete a user |
| GET | `/user/resertpassword/{email}` | password reset |

---

## Environment configuration

| File | `apiUrl` | Used by |
|---|---|---|
| `src/environments/environment.ts` | `http://backend:8081/api/v1` | development build |
| `src/environments/environment.prod.ts` | `http://users.local/api/v1` | production build (swapped in via `fileReplacements` in `angular.json`) |

---

## Running locally

Requirements: Node.js 20+, npm.

```bash
cd frontend
npm install --legacy-peer-deps
npm start                 # ng serve -> http://localhost:4200
```

The backend must be running alongside. Its `local` profile allows the `http://localhost:4200` origin
with `allow-credentials: true` - without that the browser will not store the cookies.

Note: under `ng serve` the `apiUrl` comes from `environment.ts` and points at the host `backend`.
To work on plain localhost, set it to `http://localhost:8081/api/v1`.

### Other commands

```bash
npm run build                    # production build into dist/usersapp
npm run watch                    # watch-mode build (development configuration)
npm test                         # Karma + Jasmine
npm run serve:ssr:usersapp       # run the built SSR server
```

---

## Docker

```bash
cd frontend
docker build -t users-frontend:latest .
docker run -p 80:80 users-frontend:latest
```

The image is built in two stages: `node:20-alpine` builds the application in the production
configuration, and `nginx:1.25-alpine` serves the contents of `dist/usersapp/browser`.

`nginx.conf` provides:
- the SPA fallback (`try_files $uri $uri/ /index.html`),
- a proxy from `/api/` to `http://backend-service:8081` (the Kubernetes service name),
- static asset caching (`ico|css|js|images|fonts`) for one month.

`../scripts/build-and-push-frontend.sh` builds the application, tags the image with a timestamp
(`kacperroot/users-frontend:YYYYMMDD-HHMMSS` plus `latest`) and pushes it to Docker Hub.

---

## Deployment

The Kubernetes manifests for the frontend (a `Deployment` with 2 replicas, `Service
frontend-service:80`, an HPA) live in `k8s/frontend/` at the repository root. The Ingress routes
`/api` to `backend-service` and all remaining traffic to `frontend-service`, which handles SPA routing.

---

## Known limitations and roadmap

- `environment.ts` (dev) points at the host `backend`, which does not work under `ng serve` without
  a proxy or an `/etc/hosts` entry.
- `UserService.deleteUser()` operates on `username` rather than `userId` (see the `todo` in the code).
- No unit tests - the Karma/Jasmine setup is in place, but no specs have been written.
- No pagination or sorting on the user list (also requires backend changes).
- The active-sessions view and "log out everywhere" are not exposed in the UI yet, even though the
  backend provides `GET /user/sessions`, `DELETE /user/session/{id}` and `POST /user/logout-all`.
