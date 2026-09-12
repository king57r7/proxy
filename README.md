# Proxy Platform

Proxy Platform is a **working MVP foundation** for a proxy marketplace. The repository contains a TypeScript/Express API, Supabase database scripts, and a buildable Android Compose client shell. Payment processing, production proxy fulfillment, and device-wide VPN tunneling are intentionally not enabled until their provider credentials, legal requirements, and security reviews are completed.

## Current capabilities

| Area | Status |
|---|---|
| Backend health and readiness endpoints | Implemented |
| Supabase Auth registration, login, and refresh | Implemented |
| Product marketplace read API | Implemented |
| Authenticated profile and subscription reads | Implemented |
| Android Compose application shell | Implemented |
| Database schema and sample data | Requires execution in a Supabase project; review migrations before production |
| Payments and webhook fulfillment | Not implemented |
| Device-wide VPN/tun2socks transport | Not implemented |
| Production deployment | Not included; deploy only after staging validation |

## Repository layout

```text
backend/                 Express API and tests
database/                Supabase schema and sample data
android/                 Android Compose client
docs/API.md              Implemented HTTP contract
docs/SECURITY.md         Security boundaries and release checklist
docker-compose.yml       Local PostgreSQL/Redis development services
.github/workflows/       Backend and Android verification workflow
```

## Requirements

- Node.js 22 and npm 10 or newer.
- JDK 17 for Android builds.
- Android SDK 35 for Android builds.
- A Supabase project for authenticated API and database operation.

## Backend setup

```bash
git clone https://github.com/king57r7/proxy.git
cd proxy/backend
cp .env.example .env
npm ci
npm run lint
npm run type-check
npm test
npm run build
npm run dev
```

The minimum required values are `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `JWT_SECRET`, and `ENCRYPTION_KEY`. Generate secrets locally with:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Never commit `.env`, service-role keys, payment secrets, or signing keys.

Verify the running API:

```bash
curl -fsS http://localhost:3000/livez
curl -fsS http://localhost:3000/api/health
```

## Database setup

Create a Supabase project, then run `database/01_schema.sql` followed by `database/02_seed_data.sql` in the Supabase SQL Editor. Use a disposable project first. Do not run sample seed data in production without reviewing every record and policy. The schema uses Supabase Auth's `auth.users` table and is not compatible with a plain PostgreSQL container without the Supabase Auth schema.

## Android setup

Open `android/` in Android Studio with JDK 17 and an installed Android SDK 35. From a terminal:

```bash
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The default emulator API endpoint is `http://10.0.2.2:3000/api/v1`. Supply another endpoint without editing source code:

```bash
./gradlew assembleDebug -PAPI_BASE_URL=https://staging.example.com/api/v1
```

The current client is a marketplace shell. It does not claim to establish a device-wide VPN connection.

## Docker

The backend image is built from `backend/`:

```bash
docker build -t proxy-platform-api ./backend
```

Use Docker Compose only for local infrastructure. Replace all development passwords and keep database and Redis ports private in shared environments.

## Quality gates

The repository's verification workflow runs backend install, lint, type-check, tests, build, dependency audit, and Android lint/unit-test/debug assembly. A failed quality gate must block merging. Local Android validation requires an installed Android SDK; the sandbox used to prepare this repository does not contain one.

## Important limitation

A production proxy platform requires provider integration, inventory allocation, payment verification, abuse prevention, privacy documentation, and a reviewed VPN transport. These are separate security-sensitive workstreams and must not be represented as complete merely because the MVP client and API compile.
