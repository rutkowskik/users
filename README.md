# Users App - monorepo

Aplikacja do zarzadzania uzytkownikami: rejestracja, logowanie, role, panel administracyjny
i zarzadzanie sesjami. Uwierzytelnianie oparte o JWT w cookies `HttpOnly` z rotacja refresh
tokenow i wykrywaniem ich ponownego uzycia.

Repozytorium zawiera oba serwisy oraz wspolny opis wdrozenia.

---

## Zawartosc

| Katalog | Opis | Szczegoly |
|---|---|---|
| `backend/` | REST API - Spring Boot 3.5, Java 17, PostgreSQL, Redis | [backend/README.md](backend/README.md) |
| `frontend/` | Panel webowy - Angular 17 + SSR, Bootstrap 5, Nginx | [frontend/README.md](frontend/README.md) |
| `k8s/` | Manifesty Kubernetes calego stacku (namespace, config, bazy, oba serwisy, Ingress) | - |
| `helm/` | Szkielet chartu Helm (obecnie pusty) | - |
| `scripts/` | Skrypty build / deploy / diagnostyka | - |

---

## Architektura

```
                          Ingress NGINX (host: users.local)
                                     |
                 /api/*  -------------+-------------  /*
                    |                                  |
            backend-service:8081              frontend-service:80
                    |                                  |
          users-backend (2 repliki)          users-frontend (2 repliki)
          Spring Boot / WAR / Tomcat          Angular build + Nginx
                    |
        +-----------+-----------+
        |                       |
   PostgreSQL                 Redis
   dane uzytkownikow      sesje, refresh tokeny,
                          rate limiting, metryki
```

Frontend nie przechowuje tokenow - backend wydaje `ACCESS_TOKEN` (15 min) i `REFRESH_TOKEN` (7 dni)
jako cookies `HttpOnly` + `SameSite=Strict`. Angularowy `AuthInterceptor` na odpowiedz `401` wykonuje
jednokrotny `POST /api/v1/user/refresh` i ponawia oryginalne zadanie. Backend przy kazdym odswiezeniu
uniewaznia stary refresh token i wydaje nowy (token rotation); proba uzycia juz uniewaznionego tokena
kasuje cala rodzine tokenow i konczy wszystkie powiazane sesje.

---

## Szybki start (lokalnie)

Wymagania: JDK 17+, Maven, Node.js 20+, Docker.

```bash
# 1. Redis
docker compose -f backend/REDIS_DOCKER/docker-compose.yml up -d

# 2. PostgreSQL - baza `users`, uzytkownik `users_app` / `password1`
#    (parametry w backend/src/main/resources/application-local.yml)

# 3. Backend -> http://localhost:8081
./backend/run-local.sh

# 4. Frontend -> http://localhost:4200
cd frontend && npm install --legacy-peer-deps && npm start
```

Profil `local` backendu dopuszcza w CORS origin `http://localhost:4200` wraz z `allow-credentials`,
bez czego przegladarka nie zapisze cookies.

Uwaga: `frontend/src/environments/environment.ts` wskazuje na host `backend`. Do pracy na czystym
localhoscie ustaw tam `http://localhost:8081/api/v1`.

---

## Skrypty

Wszystkie skrypty licza sciezki wzgledem wlasnego polozenia, wiec mozna je uruchamiac z dowolnego katalogu.

| Skrypt | Rola |
|---|---|
| `scripts/build-and-push-backend.sh` | Maven package + build obrazu + push do Docker Hub |
| `scripts/build-and-push-frontend.sh` | Angular build produkcyjny + build obrazu + push |
| `scripts/kubectl-apply.sh` | Aplikuje manifesty w kolejnosci, czeka na gotowosc baz i rollout |
| `scripts/deploy.sh` | Pelny pipeline: build, push i deployment |
| `scripts/health-check.sh` | Status podow, serwisow i `/actuator/health` |
| `scripts/logs.sh` | Podglad logow z namespace `users-app` |
| `scripts/generate-secrets.sh` | Generuje `k8s/secrets.yaml` (plik poza gitem) |

Skrypty `backend/run-local.sh`, `run-test.sh` i `run-prod.sh` uruchamiaja backend w danym profilu;
warianty `test` i `prod` waliduja wymagane zmienne srodowiskowe przed startem.

---

## Deployment

```bash
./scripts/generate-secrets.sh      # jednorazowo
./scripts/build-and-push-backend.sh
./scripts/build-and-push-frontend.sh
./scripts/kubectl-apply.sh
./scripts/health-check.sh
```

Manifesty trafiaja do namespace `users-app`. Dostep lokalny wymaga wpisu `users.local` w `/etc/hosts`
wskazujacego na kontroler Ingress.

Sekrety (`k8s/secrets.yaml`) sa poza kontrola wersji - generuje je `scripts/generate-secrets.sh`.

---

## Struktura repozytorium

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
├── helm/users-app/     szkielet chartu (pliki puste)
└── scripts/            build, deploy, diagnostyka
```

---

## Historia repozytorium

Monorepo powstalo ze scalenia dwoch repozytoriow:

- `rutkowskik/users` - backend, stal sie korzeniem monorepo, kod przeniesiony do `backend/`
- `rutkowskik/usersapp` - frontend, wciagniety przez `git subtree` z zachowana historia commitow

Historia obu projektow jest dostepna w `git log`.

---

## Znane ograniczenia

Wspolne dla obu serwisow:

- `SecurityConfiguration.corsConfiguration()` ma dozwolone origin zaszyte w kodzie
  (`http://localhost:4200/`) i ignoruje wlasciwosci `cors.*` z profili.
- `cookie.setSecure(true)` jest zakomentowane w `TokenServiceImpl` - wymagane przed HTTPS.
- `k8s/configmap.yaml` ustawia `SPRING_REDIS_PORT`, a aplikacja czyta `SPRING_DATA_REDIS_PORT`.
- Brak paginacji i sortowania listy uzytkownikow (wymaga zmian po obu stronach).
- Backend wystawia `GET /user/sessions`, `DELETE /user/session/{id}` i `POST /user/logout-all`,
  ale frontend nie ma jeszcze widoku aktywnych sesji.

Szczegolowe listy znajduja sie w README poszczegolnych serwisow.
