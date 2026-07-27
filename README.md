# Users - Backend (Spring Boot)

REST API do zarzadzania uzytkownikami z uwierzytelnianiem opartym o JWT w cookies HttpOnly,
rotacja refresh tokenow z wykrywaniem ponownego uzycia (reuse detection) oraz sesjami
przechowywanymi w Redis.

Frontend (Angular) znajduje sie w osobnym repozytorium: [rutkowskik/usersapp](https://github.com/rutkowskik/usersapp).

---

## Stack

| Warstwa | Technologia |
|---|---|
| Jezyk / runtime | Java 17, pakowanie do WAR (`users_app.war`), obraz Docker na `eclipse-temurin:21` |
| Framework | Spring Boot 3.5.3 (Web, Security, Data JPA, Data Redis, Actuator) |
| Baza danych | PostgreSQL (Hibernate, HikariCP) |
| Cache / sesje | Redis 7 (Lettuce) |
| Tokeny | `com.auth0:java-jwt` 4.5.0 |
| Metryki | Micrometer + Prometheus |
| Pozostale | Lombok, Guava (cache prob logowania), javax.mail, Commons Codec (SHA-256) |
| Deployment | Docker, Kubernetes (namespace `users-app`), Ingress NGINX |

---

## Model uwierzytelniania

Tokeny **nie trafiaja do LocalStorage** - sa wydawane jako cookies `HttpOnly` + `SameSite=Strict`:

| Cookie | TTL (domyslnie) | Zrodlo prawdy |
|---|---|---|
| `ACCESS_TOKEN` | 15 min (`JWT_ACCESS_TOKEN_EXPIRATION`) | wylacznie podpis JWT |
| `REFRESH_TOKEN` | 7 dni (`JWT_REFRESH_TOKEN_EXPIRATION`) | JWT + wpis w Redis |

### Przeplyw

1. **Login** (`POST /api/v1/user/login`) - `AuthenticationManager` weryfikuje haslo (BCrypt),
   `TokenServiceImpl` generuje oba tokeny, zapisuje metadane refresh tokena w Redis i ustawia cookies.
2. **Autoryzacja zadania** - `JWTAuthorizationFilter` czyta `ACCESS_TOKEN` z cookie, waliduje podpis
   i wypelnia `SecurityContextHolder`. Brak / nieprawidlowy token konczy sie `401` bez wchodzenia dalej w lancuch.
3. **Refresh** (`POST /api/v1/user/refresh`) - stary refresh token jest **uniewazniany i wymieniany**
   (token rotation). Nowa para trafia do cookies.
4. **Reuse detection** - kazdy refresh token nalezy do `tokenFamily` (UUID). Proba uzycia tokena, ktory
   zostal juz uniewazniony, powoduje uniewaznienie **calej rodziny** i rzucenie `TokenReusedException`,
   czyli wylogowanie ze wszystkich urzadzen zbudowanych na tym lancuchu rotacji.

### Struktura kluczy w Redis

```
refresh_token:<sha256>   -> RefreshTokenData (JSON, TTL = TTL refresh tokena)
user_tokens:<username>   -> SET hashy tokenow  (obsluga "logout all devices")
token_family:<uuid>      -> SET hashy tokenow  (obsluga reuse detection)
revoked_token:<sha256>   -> marker uniewaznienia (TTL 1h)
revoked_info:<sha256>    -> RefreshTokenData uniewaznionego tokena (TTL 24h)
refresh_rate:<username>  -> licznik rate limitu (TTL 1 min)
```

Same tokeny nigdy nie trafiaja do Redis - przechowywany jest wylacznie ich hash SHA-256.

### Ochrona przed nadużyciami

- `LoginAttemptService` - cache Guava, 5 nieudanych prob logowania / 15 min blokuje konto
  (obslugiwany przez `AuthenticationFailureListener` / `AuthenticationSuccessListener`).
- `RedisRateLimiterService` - maks. 10 odswiezen tokena na minute na uzytkownika.

---

## Role i uprawnienia

Role sa mapowane na liste uprawnien (`pl.krutkowski.users.constant.Authorities`), a endpointy chronione
adnotacja `@PreAuthorize` na poziomie uprawnienia, nie roli.

| Rola | Uprawnienia |
|---|---|
| `ROLE_USER` | `user:read` |
| `ROLE_HR` | `user:read`, `user:update` |
| `ROLE_MANAGER` | `user:read`, `user:update` |
| `ROLE_ADMIN` | `user:read`, `user:create`, `user:update` |
| `ROLE_SUPER_ADMIN` | wszystkie + `user:delete`, `app:monitoring` |

---

## API

Base path: `/api/v1/user`

### Uwierzytelnianie i sesje

| Metoda | Sciezka | Dostep | Opis |
|---|---|---|---|
| POST | `/login` | publiczny | Logowanie, ustawia cookies z tokenami |
| POST | `/register` | publiczny | Rejestracja, haslo generowane i wysylane mailem |
| POST | `/refresh` | publiczny (cookie) | Rotacja tokenow, `401` przy reuse |
| POST | `/logout` | publiczny (cookie) | Uniewaznia refresh token i czysci cookies |
| POST | `/logout-all` | zalogowany | Uniewaznia wszystkie sesje uzytkownika |
| GET | `/me` | zalogowany | Dane aktualnie zalogowanego uzytkownika |
| GET | `/sessions` | zalogowany | Lista aktywnych sesji (IP, User-Agent, znacznik biezacej) |
| DELETE | `/session/{sessionId}` | zalogowany | Uniewaznia wskazana sesje (`sessionId` = 8 znakow hasha) |

### Zarzadzanie uzytkownikami

| Metoda | Sciezka | Dostep | Opis |
|---|---|---|---|
| GET | `/list` | zalogowany | Lista uzytkownikow |
| GET | `/find/{username}` | zalogowany | Szczegoly uzytkownika |
| POST | `/add` | zalogowany | Dodanie uzytkownika (multipart, opcjonalny avatar) |
| POST | `/update` | zalogowany | Aktualizacja uzytkownika (multipart) |
| DELETE | `/delete/{username}` | `user:delete` | Usuniecie uzytkownika |
| GET | `/resertpassword/{email}` | zalogowany | Reset hasla, nowe haslo wysylane mailem |
| POST | `/updateProfileImage` | zalogowany | Podmiana avatara |
| GET | `/image/{username}/{filename}` | publiczny | Avatar z dysku |
| GET | `/image/profile/{username}` | publiczny | Avatar zastepczy (Robohash) |

### Monitoring

| Metoda | Sciezka | Dostep | Opis |
|---|---|---|---|
| GET | `/admin/token-stats` | `app:monitoring` | Liczba aktywnych tokenow, uzytkownikow, rodzin, uniewaznien |
| GET | `/admin/top-active-users` | `app:monitoring` | Uzytkownicy z najwieksza liczba sesji (`?limit=10`) |
| GET | `/actuator/health`, `/actuator/prometheus` | zaleznie od profilu | Health probes i metryki |

Bledy domenowe (`UserNotFoundException`, `EmailExistException`, `UsernameExistException`,
`NotAnImageFileException`, `RateLimitException`, `InvalidTokenException`, `TokenReusedException`)
sa mapowane centralnie w `ExceptionHandling`.

---

## Struktura projektu

```
src/main/java/pl/krutkowski/users/
├── configuration/   SecurityConfiguration, RedisConfiguration, ProfileConfiguration
├── constant/        Authorities, SecurityConstant, FileConstant, EmailConstant, UserConstant
├── controller/      UserController, StatisticController
├── entity/          User (JPA), RefreshTokenData (Redis)
├── enumeration/     Role
├── exception/       ExceptionHandling + wyjatki domenowe
├── filter/          JWTAuthorizationFilter, JwtAccessDeniedHandler, JwtAccessForbiddenEntryPoint
├── listener/        AuthenticationSuccessListener, AuthenticationFailureListener
├── mapper/          UserMapper
├── model/           DTO, UserPrinciple, HttpResponse, SessionInfo, TokenStats
├── repository/      UserRepository
├── service/         UserService, TokenService, RedisTokenService, RedisRateLimiterService,
│                    TokenMetricsService, LoginAttemptService, EmailService
└── utility/         JTWTokenProvider
```

---

## Profile konfiguracyjne

| Profil | Plik | Charakterystyka |
|---|---|---|
| `local` | `application-local.yml` | PostgreSQL i Redis na localhost, `ddl-auto: update`, pelne logi SQL i Security, wszystkie endpointy Actuatora, CORS na `localhost:4200` |
| `test` | `application-test.yml` | Srodowisko staging, `ddl-auto: validate`, sekrety z ENV |
| `prod` | `application-prod.yml` | `ddl-auto: validate`, ukryte stacktrace, logi na poziomie WARN, wszystkie sekrety z ENV, pula Hikari 20 polaczen |

Profil wybiera `SPRING_PROFILES_ACTIVE` (domyslnie `local`).

### Zmienne srodowiskowe (`test` / `prod`)

```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT        # domyslnie 6379
JWT_SECRET                    # wymagane, bez wartosci domyslnej
CORS_ALLOWED_ORIGINS
SERVER_PORT                   # domyslnie 8081
```

---

## Uruchomienie lokalne

Wymagania: JDK 17+, Maven, Docker.

```bash
# 1. Redis
docker compose -f REDIS_DOCKER/docker-compose.yml up -d

# 2. PostgreSQL - baza `users`, uzytkownik `users_app` / `password1`
#    (zgodnie z application-local.yml)

# 3. Aplikacja
./run-local.sh          # albo: mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API: `http://localhost:8081`, Actuator: `http://localhost:8081/actuator`.

Frontend uruchomiony przez `ng serve` na `http://localhost:4200` jest dopuszczony w CORS profilu `local`.

Pozostale skrypty startowe: `run-test.sh`, `run-prod.sh` (oba walidują wymagane zmienne środowiskowe
przed startem i przerywaja prace, gdy ktorejs brakuje).

---

## Testy

```bash
mvn test
```

`TokenServiceIntegrationTest` (`src/test/java/redis/`) pokrywa rotacje tokenow i reuse detection
w oparciu o `RedisTestConfiguration`.

---

## Docker

```bash
mvn clean package
docker build -t users-backend:latest .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod ... users-backend:latest
```

Skrypt `scripts/build-and-push-backend.sh` buduje WAR, taguje obraz znacznikiem czasu
(`kacperroot/users-backend:YYYYMMDD-HHMMSS` + `latest`) i wypycha go do Docker Hub.

---

## Kubernetes

Manifesty leza w `k8s/` i obejmuja **caly stack** - rowniez frontend, ktorego kod zyje w drugim repozytorium.

```
k8s/
├── namespace.yaml                       namespace users-app
├── configmap.yaml                       konfiguracja nie-wrazliwa
├── secrets.yaml                         sekrety (poza gitem)
├── ingress.yaml                         host users.local, /api -> backend, reszta -> frontend (SPA)
├── postgres/postgres-statefulset.yaml
├── redis/redis-statefulset.yaml
├── backend/backend-deployment.yaml      Deployment (2 repliki) + Service backend-service:8081
├── backend/backend-hpa.yaml
├── frontend/frontend-deployment.yaml    Deployment (2 repliki) + Service frontend-service:80
└── frontend/frontend-hpa.yaml
```

Skrypty pomocnicze w `scripts/`:

| Skrypt | Rola |
|---|---|
| `build-and-push-backend.sh` | build Maven + Docker + push do rejestru |
| `kubectl-apply.sh` | aplikuje manifesty w kolejnosci, czeka na gotowosc baz i rollout backendu |
| `deploy.sh` | pelny pipeline deploymentu |
| `health-check.sh` | status podow, serwisow i `/actuator/health` |
| `logs.sh` | podglad logow z namespace |
| `generate-secrets.sh` | generuje `k8s/secrets.yaml` (plik ignorowany przez git) |

Dostep lokalny wymaga wpisu `users.local` w `/etc/hosts` wskazujacego na Ingress kontrolera.

---

## Znane ograniczenia i plan

- `SecurityConfiguration.corsConfiguration()` ma dozwolone origin zaszyte w kodzie
  (`http://localhost:4200/`) i ignoruje odczytane wlasciwosci `cors.*` z profili - do ujednolicenia.
- `cookie.setSecure(true)` jest zakomentowane w `TokenServiceImpl` - wymagane przed wystawieniem na HTTPS.
- `k8s/configmap.yaml` ustawia `SPRING_REDIS_PORT`, podczas gdy aplikacja czyta `SPRING_DATA_REDIS_PORT`.
- Avatary zapisywane sa na lokalnym dysku poda (`FileConstant.USER_FOLDER`) - przy wielu replikach
  wymagaja wspoldzielonego wolumenu lub przeniesienia do object storage.
- Brak paginacji i sortowania na `GET /list`.
- Refaktoryzacja w kierunku architektury portow i adapterow (galaz `refactor_ports_and_adapters`).
