# LearnSphere

LearnSphere is a full-stack learning platform built with React 19/Vite and Java 21/Spring Boot. PostgreSQL is the production database; Flyway owns production migrations. H2 is used only for isolated automated tests.

## Run the full stack with Docker Compose

Install Docker Desktop, then from the project root:

```powershell
Copy-Item .env.example .env
# Edit .env: set DATABASE_PASSWORD and a random JWT_SECRET before starting.
docker compose up --build -d
docker compose ps
```

Open [http://localhost:5173](http://localhost:5173). The Nginx frontend proxies `/api` to this project's API service. The API health page is [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). PostgreSQL data persists in the `learnsphere_postgres_data` volume. `docker compose down` preserves data; `docker compose down -v` deletes it.

## IntelliJ/local development

Install JDK 21, IntelliJ IDEA, Node.js 20.19+, and PostgreSQL 18. Copy `.env.example` to `.env` and start PostgreSQL with matching credentials. IntelliJ does not automatically import `.env`; set the following values in the Run Configuration's environment, or in the PowerShell session used for the API:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/learnsphere"
$env:DATABASE_USERNAME = "learnsphere"
$env:DATABASE_PASSWORD = "<the same value as .env>"
$env:JWT_SECRET = "<a random secret of at least 32 bytes>"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
```

Import `backend/pom.xml` as Maven, and run `com.learnsphere.LearnSphereApplication` with the `local` profile. In another terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Local Vite proxies `/api` to `http://localhost:8080`; override with `API_PROXY_TARGET` if needed. In Docker, the frontend proxies directly to its `api` container and never silently targets another local API.

### Find a conflicting Windows process on port 8080

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
$ownerProcessId = (Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1 -ExpandProperty OwningProcess)
Get-Process -Id $ownerProcessId
Stop-Process -Id $ownerProcessId -Force
```

Confirm the process is stale before stopping it.

## Features and API

The browser uses an HttpOnly `LEARN_SPHERE_TOKEN` cookie and CSRF token/header for state-changing requests. API clients may use bearer tokens. Login and registration share a response structure (`user`, `verificationRequired`, `message`); tokens are not returned in JSON.

- `POST /api/auth/register`, `/api/auth/login`, `/api/auth/logout`, `GET /api/auth/me`
- `POST /api/auth/verify-email`, `/api/auth/resend-verification`
- `POST /api/auth/forgot-password`, `/api/auth/reset-password`
- `GET /api/courses?page=0&size=9&search=java&category=Programming&level=Beginner&sort=name&direction=asc`
- `POST /api/courses/{id}/enroll`, `GET /api/courses/{id}/lessons`, `POST /api/courses/{id}/lessons/{lessonId}/complete`
- `POST /api/payments/courses/{id}/order`, `POST /api/payments/verify`
- `/api/admin/**` — admin-only user, course, lesson and enrollment management
- `GET /actuator/health`

Validation and API errors return JSON with status, message, timestamp and field errors where relevant. Passwords use BCrypt. JWT sessions expire; logout and password changes invalidate sessions. Verification and reset tokens are random, hashed at rest, single-use and expire.

## Configuration and external services

Set secrets in the ignored root `.env` or a deployment secret store. Never put server secrets in Vite variables.

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `JWT_SECRET` (at least 32 bytes), `CORS_ALLOWED_ORIGINS`, `TOKEN_HOURS`, `DB_POOL_SIZE`, `PORT`
- `FRONTEND_URL`, `AUTH_COOKIE_SECURE`, `AUTH_COOKIE_SAME_SITE`, `REQUIRE_EMAIL_VERIFICATION`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` (Razorpay test-mode credentials)
- Optional first-start admin: `ADMIN_EMAIL`, `ADMIN_PASSWORD` (12–72 UTF-8 bytes)

Email verification and reset send real messages only after SMTP credentials are configured. Razorpay checkout uses the provider order API, server-side signature verification, capture confirmation, and enrollment only after capture; it needs sandbox credentials and account-side test setup. Do not save card data in LearnSphere.

Lessons accept HTTPS video URLs and return those URLs only to enrolled learners. A video URL hosted publicly can still be opened independently; private paid playback requires a storage/CDN provider that issues expiring signed URLs. Configure such a provider before placing private paid videos in production.

## Build and tests

```powershell
cd backend
mvn test
cd ..\frontend
npm run build
```

The backend integration suite runs against H2. It does not prove PostgreSQL migration execution. Docker Desktop, PostgreSQL and external provider credentials were unavailable during local verification, so full Compose/PostgreSQL/provider checks must be run in an environment where those are installed and configured.
