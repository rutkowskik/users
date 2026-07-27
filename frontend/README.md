# Users App - Frontend (Angular)

Panel zarzadzania uzytkownikami: logowanie, rejestracja, lista uzytkownikow, edycja, blokowanie
i usuwanie kont oraz upload avatara. Uwierzytelnianie oparte o cookies `HttpOnly` z automatycznym
odswiezaniem tokena.

Modul `frontend/` monorepo. Backend (Spring Boot) znajduje sie w [`backend/`](../backend/README.md),
opis calosci wraz z architektura - w [README na poziomie repozytorium](../README.md).

---

## Stack

| Warstwa | Technologia |
|---|---|
| Framework | Angular 17 (moduly NgModule, nie standalone) |
| SSR | Angular SSR + Express (`server.ts`), prerendering wlaczony |
| UI | Bootstrap 5.3, Font Awesome 4.7 |
| Powiadomienia | angular-notifier 14 |
| HTTP | `HttpClient` + `AuthInterceptor`, `withCredentials: true` |
| Jezyk | TypeScript 5.3, RxJS 7.8 |
| Testy | Karma + Jasmine |
| Serwowanie w produkcji | Nginx 1.25 (obraz wieloetapowy: `node:20-alpine` -> `nginx:1.25-alpine`) |

---

## Uwierzytelnianie

Aplikacja **nie przechowuje tokenow** - tokeny `ACCESS_TOKEN` i `REFRESH_TOKEN` sa cookies `HttpOnly`
ustawianymi przez backend i niedostepnymi z poziomu JavaScriptu. W `localStorage` trzymany jest wylacznie
cache profilu uzytkownika (klucz `user`) i listy uzytkownikow (`users`) - dane pomocnicze, nie poswiadczenia.

### `AuthInterceptor`

- Do kazdego zadania dokleja `withCredentials: true`, zeby cookies wedrowaly z zapytaniem.
- Endpointy publiczne (`/user/login`, `/user/logout`, `/user/register`, `/user/me`, `/user/refresh`)
  przepuszcza bez logiki ponawiania.
- Na odpowiedz `401` wykonuje jednokrotny `POST /user/refresh` (flaga `isRefreshing` zapobiega petli),
  a po udanej rotacji tokenow **ponawia oryginalne zadanie**. Gdy refresh sie nie powiedzie, przekierowuje
  na `/login`.

### Guardy

| Guard | Chroni | Zachowanie |
|---|---|---|
| `AuthenticationGuard` | `/user/management` | Weryfikuje sesje przez `GET /user/me`; przy braku sesji przekierowuje na `/login` z `returnUrl` i pokazuje powiadomienie |
| `LoginGuard` | `/login`, `/register` | Zalogowanego uzytkownika przekierowuje od razu na `/user/management` |

`AuthenticationService.isLoggedIn()` zwraca `Observable<boolean>` - najpierw sprawdza cache w pamieci,
a przy jego braku pyta backend o `/user/me`. Flaga `checkingAuth` chroni przed rownoleglymi zapytaniami.

---

## Routing

| Sciezka | Komponent | Guard |
|---|---|---|
| `/login` | `LoginComponent` | `LoginGuard` |
| `/register` | `RegisterComponent` | `LoginGuard` |
| `/user/management` | `UserComponent` | `AuthenticationGuard` |
| `/` | przekierowanie na `/login` | - |
| `/not-found` | `NotFoundComponent` | - |
| `**` | przekierowanie na `/not-found` | - |

---

## Struktura projektu

```
frontend/
├── src/app/
│   ├── login/            ekran logowania
│   ├── register/         rejestracja
│   ├── user/             panel zarzadzania uzytkownikami (lista, modale, upload avatara)
│   ├── not-found/        strona 404
│   ├── common/
│   │   └── loading-spinner/
│   ├── guard/            AuthenticationGuard, LoginGuard
│   ├── interceptor/      AuthInterceptor (withCredentials + refresh na 401)
│   ├── service/          AuthenticationService, UserService, NotificationService
│   ├── model/            User, CustomHttpResponse, FileUploadStatus
│   ├── enum/             Role, HeaderType, NotificationType
│   ├── app-routing.module.ts
│   ├── app.module.ts
│   └── app.module.server.ts
├── src/environments/     environment.ts (dev), environment.prod.ts (produkcja)
├── server.ts             serwer Express dla SSR
├── nginx.conf            konfiguracja Nginx dla obrazu produkcyjnego
├── Dockerfile            build wieloetapowy
└── docker-compose.yml
```

Skrypt budowania i publikacji obrazu lezy poza modulem, razem z odpowiednikiem backendowym:
`scripts/build-and-push-frontend.sh`.

---

## Uzywane endpointy backendu

Bazowy URL pochodzi z `environment.apiUrl` (`/api/v1`).

| Metoda | Endpoint | Uzycie |
|---|---|---|
| POST | `/user/login` | logowanie, backend ustawia cookies |
| POST | `/user/register` | rejestracja |
| POST | `/user/logout` | wylogowanie i wyczyszczenie cookies |
| POST | `/user/refresh` | rotacja tokenow (wywolywana przez interceptor) |
| GET | `/user/me` | weryfikacja sesji przez guardy |
| GET | `/user/list` | lista uzytkownikow |
| POST | `/user/add`, `/user/update` | dodanie / edycja uzytkownika (`FormData`) |
| POST | `/user/updateProfileImage` | upload avatara z raportowaniem postepu |
| DELETE | `/user/delete/{username}` | usuniecie uzytkownika |
| GET | `/user/resertpassword/{email}` | reset hasla |

---

## Konfiguracja srodowisk

| Plik | `apiUrl` | Uzycie |
|---|---|---|
| `src/environments/environment.ts` | `http://backend:8081/api/v1` | build deweloperski |
| `src/environments/environment.prod.ts` | `http://users.local/api/v1` | build produkcyjny (podmieniany przez `fileReplacements` w `angular.json`) |

---

## Uruchomienie lokalne

Wymagania: Node.js 20+, npm.

```bash
cd frontend
npm install --legacy-peer-deps
npm start                 # ng serve -> http://localhost:4200
```

Backend musi dzialac rownolegle. Profil `local` backendu dopuszcza w CORS origin `http://localhost:4200`
wraz z `allow-credentials: true` - bez tego cookies nie zostana zapisane przez przegladarke.

Uwaga: przy `ng serve` `apiUrl` pochodzi z `environment.ts` i wskazuje na host `backend`.
Do pracy na czystym localhoscie ustaw tam `http://localhost:8081/api/v1`.

### Pozostale komendy

```bash
npm run build                    # build produkcyjny do dist/usersapp
npm run watch                    # build w trybie watch (konfiguracja development)
npm test                         # Karma + Jasmine
npm run serve:ssr:usersapp       # uruchomienie zbudowanego serwera SSR
```

---

## Docker

```bash
cd frontend
docker build -t users-frontend:latest .
docker run -p 80:80 users-frontend:latest
```

Obraz jest budowany dwuetapowo: `node:20-alpine` buduje aplikacje w konfiguracji produkcyjnej,
a `nginx:1.25-alpine` serwuje zawartosc `dist/usersapp/browser`.

`nginx.conf` zapewnia:
- fallback SPA (`try_files $uri $uri/ /index.html`),
- proxy `/api/` na `http://backend-service:8081` (nazwa uslugi w Kubernetes),
- cache statykow (`ico|css|js|obrazy|fonty`) na 1 miesiac.

Skrypt `../scripts/build-and-push-frontend.sh` buduje aplikacje, taguje obraz znacznikiem czasu
(`kacperroot/users-frontend:YYYYMMDD-HHMMSS` + `latest`) i wypycha go do Docker Hub.

---

## Deployment

Manifesty Kubernetes dla frontendu (`Deployment` z 2 replikami, `Service frontend-service:80`, HPA)
leza w `k8s/frontend/` na poziomie repozytorium. Ingress kieruje `/api` do `backend-service`,
a caly pozostaly ruch do `frontend-service` (obsluga routingu SPA).

---

## Znane ograniczenia i plan

- `environment.ts` (dev) wskazuje na host `backend`, co nie dziala przy `ng serve` bez proxy
  lub wpisu w `/etc/hosts`.
- `UserService.deleteUser()` operuje na `username` zamiast na `userId` (komentarz `todo` w kodzie).
- Brak testow jednostkowych - konfiguracja Karma/Jasmine jest gotowa, ale specyfikacje nie zostaly napisane.
- Brak paginacji i sortowania listy uzytkownikow (wymaga rowniez zmian po stronie backendu).
- Widok aktywnych sesji i wylogowania ze wszystkich urzadzen nie jest jeszcze wystawiony w UI,
  mimo ze backend udostepnia `GET /user/sessions`, `DELETE /user/session/{id}` i `POST /user/logout-all`.
