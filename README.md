# Proxy Platform

Proxy Platform is a production-oriented MVP foundation for a proxy marketplace. It contains a TypeScript/Express API, Supabase schema, and an Android Compose client with real authentication and API networking. Payment fulfillment, provider allocation, and device-wide VPN transport remain explicitly gated until their providers and security requirements are configured.

## Implemented client flow

The Android client now includes registration, login, persisted session state, logout, real product loading from the API, featured filtering, product detail pages, profile, email verification status, and subscription reads. It never receives the Supabase service-role key. It uses `BuildConfig.API_BASE_URL` and sends the user access token to the backend.

## Railway deployment

Create a Railway service from this repository and set the service **Root Directory** to `/backend`. Railway will then use `backend/Dockerfile`. Add these variables to the Railway service:

```env
NODE_ENV=production
PORT=3000
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
JWT_SECRET=long-random-secret
JWT_REFRESH_SECRET=different-long-random-secret
ENCRYPTION_KEY=long-random-secret
CORS_ORIGINS=https://your-allowed-web-origin.example
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100
LOG_LEVEL=info
```

Do not put `SUPABASE_SERVICE_ROLE_KEY`, JWT secrets, or `ENCRYPTION_KEY` into Android or GitHub source. After Railway generates a public HTTPS URL, verify:

```bash
curl -fsS https://YOUR-RAILWAY-DOMAIN/livez
curl -fsS https://YOUR-RAILWAY-DOMAIN/readyz
curl -fsS https://YOUR-RAILWAY-DOMAIN/api/v1/products
```

## Android build against Railway

Set `API_BASE_URL` to the Railway API URL including `/api/v1`:

```bash
cd android
./gradlew assembleDebug -PAPI_BASE_URL=https://YOUR-RAILWAY-DOMAIN/api/v1
```

For GitHub Actions, add a repository variable named `API_BASE_URL` or a secret with the same name. The workflow uses it when present and falls back to the Android emulator address for development builds. The workflow uploads `app-debug.apk` as `proxy-platform-debug-apk`.

## Local backend

```bash
cd backend
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

## Database

Run `database/01_schema.sql` and then the corrected `database/02_seed_data.sql` in a disposable Supabase project before production. The schema uses Supabase Auth's `auth.users` table and requires Supabase rather than plain PostgreSQL.

## Current release boundary

The implemented client and API do not yet claim to process payments, allocate live provider inventory, or establish a device-wide VPN. Those functions require payment credentials, a proxy provider contract, webhook/idempotency logic, and an independently reviewed network transport.
