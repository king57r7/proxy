# 🚀 Proxy Platform - Complete Production-Ready Solution

A comprehensive, enterprise-grade proxy management platform with Android app, backend API, admin dashboard, and complete infrastructure.

**Status**: Production-Ready Foundation | **Version**: 1.0.0

---

## 📋 Table of Contents

1. [Project Structure](#project-structure)
2. [Features](#features)
3. [Requirements](#requirements)
4. [Quick Start](#quick-start)
5. [Environment Setup](#environment-setup)
6. [Database Setup](#database-setup)
7. [Backend Setup](#backend-setup)
8. [Android App Setup](#android-app-setup)
9. [Deployment](#deployment)
10. [API Documentation](#api-documentation)
11. [Security Considerations](#security-considerations)
12. [Troubleshooting](#troubleshooting)

---

## 📁 Project Structure

```
ProxyPlatform/
├── backend/                    # Express.js API
│   ├── src/
│   │   ├── config/            # Configuration files
│   │   ├── models/            # Data models
│   │   ├── services/          # Business logic
│   │   ├── controllers/       # Request handlers
│   │   ├── routes/            # API routes
│   │   ├── middleware/        # Custom middleware
│   │   ├── utils/             # Helper utilities
│   │   ├── providers/         # Payment/Proxy providers
│   │   └── index.ts           # Entry point
│   ├── tests/                 # Test files
│   ├── .env.example           # Environment template
│   ├── Dockerfile             # Production image
│   ├── docker-compose.yml     # Dev environment
│   └── package.json
│
├── android/                   # Android app
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/proxyplatform/app/
│   │   │   │   │   ├── ui/                # Compose screens
│   │   │   │   │   ├── services/         # VPN/Network services
│   │   │   │   │   ├── data/             # Room DB & DataStore
│   │   │   │   │   ├── domain/           # Business logic
│   │   │   │   │   ├── workers/          # Background tasks
│   │   │   │   │   └── MainActivity.kt
│   │   │   │   ├── res/                  # Resources
│   │   │   │   └── AndroidManifest.xml
│   │   │   ├── test/                    # Unit tests
│   │   │   └── androidTest/             # Instrumentation tests
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── local.properties
│   └── gradle.properties
│
├── database/                  # Database schema & migrations
│   ├── 01_schema.sql         # Complete schema
│   ├── 02_seed_data.sql      # Initial data
│   ├── 03_functions.sql      # PL/pgSQL functions
│   └── migrations/           # Version control
│
├── devops/                    # Infrastructure
│   ├── docker/
│   │   ├── api.Dockerfile
│   │   ├── nginx.Dockerfile
│   │   └── worker.Dockerfile
│   ├── kubernetes/
│   │   ├── api-deployment.yaml
│   │   ├── redis-deployment.yaml
│   │   └── nginx-ingress.yaml
│   └── terraform/            # IaC configuration
│
├── docs/                      # Documentation
│   ├── ARCHITECTURE.md        # System architecture
│   ├── API.md                 # API reference
│   ├── DATABASE.md            # DB schema docs
│   ├── ANDROID.md             # Android app guide
│   ├── DEPLOYMENT.md          # Deployment guide
│   ├── SECURITY.md            # Security guidelines
│   └── CONTRIBUTING.md        # Contribution guide
│
├── .github/
│   └── workflows/
│       ├── build.yml          # Android & Backend build
│       ├── test.yml           # Run tests
│       └── deploy.yml         # Production deployment
│
├── .env.example               # Environment template (root)
├── docker-compose.yml         # Dev environment (root)
├── README.md                  # This file
└── LICENSE
```

---

## ✨ Features

### 🎯 Core Features
- ✅ Multi-proxy marketplace with filtering & search
- ✅ VPN/Proxy connection engine (Android VPNService)
- ✅ Real-time proxy health monitoring
- ✅ Complete payment integration (Stripe, PayPal, Razorpay)
- ✅ User subscription management
- ✅ Professional admin dashboard
- ✅ Smart proxy selection algorithm
- ✅ Comprehensive diagnostics & logging

### 🔐 Security
- ✅ End-to-end encryption for credentials
- ✅ JWT authentication with refresh tokens
- ✅ Row-level security (RLS) on database
- ✅ Kill switch functionality
- ✅ DNS leak protection
- ✅ IPv6 handling
- ✅ Secure storage using Android Keystore
- ✅ Rate limiting & DDoS protection

### 📊 Analytics & Monitoring
- ✅ Connection statistics & metrics
- ✅ Proxy health scoring
- ✅ User behavior analytics
- ✅ Performance monitoring
- ✅ Error tracking (Sentry integration)
- ✅ Detailed audit logs

### 🎨 User Experience
- ✅ Material Design 3 with Jetpack Compose
- ✅ Dark/Light theme support
- ✅ Arabic (RTL) & English localization
- ✅ Offline mode with local caching
- ✅ Smooth animations & transitions
- ✅ Responsive design for all screen sizes

---

## 📦 Requirements

### Backend
- Node.js >= 18.0.0
- npm >= 9.0.0
- Supabase account (or PostgreSQL + Docker)
- Redis (for caching & job queues)

### Android
- Android Studio >= 2023.1.1
- SDK 26-34
- Kotlin 1.9+
- Gradle 8.0+

### DevOps
- Docker & Docker Compose
- Git
- GitHub account (for Actions)
- (Optional) Kubernetes cluster
- (Optional) Terraform

---

## 🚀 Quick Start

### 1️⃣ Clone & Setup

```bash
# Clone repository
git clone https://github.com/yourusername/proxy-platform.git
cd ProxyPlatform

# Copy environment template
cp .env.example .env
cp backend/.env.example backend/.env
```

### 2️⃣ Configure Environment Variables

See [Environment Setup](#environment-setup) section below.

### 3️⃣ Start Development Environment

```bash
# Option A: Using Docker Compose (recommended)
docker-compose up -d

# Option B: Manual setup (see sections below)
```

### 4️⃣ Setup Database

```bash
# With Docker Compose: Already initialized
# Manual: Run SQL files in order
psql -U postgres -d proxy_platform < database/01_schema.sql
psql -U postgres -d proxy_platform < database/02_seed_data.sql
```

### 5️⃣ Start Backend

```bash
cd backend
npm install
npm run dev
# API available at http://localhost:3000
```

### 6️⃣ Build Android App

```bash
cd android
# Open in Android Studio or build from command line
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 Environment Setup

### Root .env File

Create `.env` in project root:

```bash
# Node Environment
NODE_ENV=development
PORT=3000

# Supabase Configuration
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_anon_key
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key

# Database (for local PostgreSQL)
DATABASE_URL=postgresql://postgres:password@localhost:5432/proxy_platform

# Authentication
JWT_SECRET=$(openssl rand -hex 32)
JWT_REFRESH_SECRET=$(openssl rand -hex 32)
JWT_EXPIRATION=24h
JWT_REFRESH_EXPIRATION=7d

# Encryption (use 32 bytes)
ENCRYPTION_KEY=$(openssl rand -hex 32)

# Payment Providers (Stripe)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLIC_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Email (Gmail)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_FROM_EMAIL=noreply@proxyplatform.com

# Redis
REDIS_PASSWORD=redis123
REDIS_HOST=localhost
REDIS_PORT=6379

# Frontend URLs
FRONTEND_URL=http://localhost:3001
ADMIN_FRONTEND_URL=http://localhost:3002

# Feature Flags
FEATURE_REFERRAL=true
FEATURE_COUPONS=true
FEATURE_SMART_SELECT=true
```

### Generate Secrets

```bash
# Generate strong encryption keys
node -e "console.log('JWT_SECRET=' + require('crypto').randomBytes(32).toString('hex'))"
node -e "console.log('ENCRYPTION_KEY=' + require('crypto').randomBytes(32).toString('hex'))"
```

### Android Configuration

Update `backend/src/config/index.ts`:
- API_BASE_URL
- SUPABASE credentials
- Payment provider keys

---

## 🗄️ Database Setup

### Option 1: Using Supabase (Recommended)

1. Create account at https://supabase.com
2. Create new project
3. Go to SQL Editor
4. Copy & run `database/01_schema.sql`
5. Copy & run `database/02_seed_data.sql`
6. Get connection credentials from project settings

### Option 2: Local PostgreSQL with Docker

```bash
# Start PostgreSQL container
docker run --name postgres-proxy \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=proxy_platform \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:15-alpine

# Initialize database
psql -h localhost -U postgres -d proxy_platform < database/01_schema.sql
```

### Option 3: Docker Compose

```bash
docker-compose up postgres -d
# Wait for initialization
docker-compose exec postgres psql -U postgres -d proxy_platform -c "SELECT 1;"
```

---

## 🔨 Backend Setup

### Development

```bash
cd backend

# Install dependencies
npm install

# Create .env file
cp .env.example .env
# Edit .env with your configuration

# Development server
npm run dev

# API available at http://localhost:3000/api/v1
```

### Project Structure

```
backend/src/
├── config/           # Configuration loader
├── models/           # TypeScript interfaces/types
├── services/         # Business logic
│   ├── proxy/
│   ├── user/
│   ├── payment/
│   └── subscription/
├── controllers/      # Route handlers
├── routes/           # Express routes
├── middleware/       # Custom middleware
│   ├── auth.ts       # JWT verification
│   ├── validation.ts # Input validation
│   └── errorHandler.ts
├── utils/           # Helpers
│   ├── logger.ts
│   ├── supabase.ts
│   ├── encryption.ts
│   └── validation.ts
├── providers/       # External services
│   ├── stripe.ts
│   ├── paypal.ts
│   └── proxyProvider.ts
├── types/          # TypeScript definitions
└── index.ts        # Entry point
```

### Available Commands

```bash
npm run dev          # Development with auto-reload
npm run build        # Build to dist/
npm start           # Run production build
npm test            # Run tests
npm test:watch      # Watch mode
npm run lint        # Run ESLint
npm run migrate     # Database migrations
npm run seed        # Seed database
```

---

## 📱 Android App Setup

### Prerequisites

- Android Studio >= 2023.1.1
- Gradle 8.0+
- Java 17 JDK
- Android SDK 26-34
- Kotlin Plugin 1.9+

### Setup Steps

```bash
cd android

# Option 1: Open in Android Studio
open -a "Android Studio" .

# Option 2: Build from command line
./gradlew clean
./gradlew build
./gradlew assembleDebug  # Build debug APK
./gradlew assembleRelease # Build release (requires keystore)
```

### Project Structure

```
android/app/src/main/java/com/proxyplatform/app/
├── ui/                          # Jetpack Compose screens
│   ├── screens/
│   │   ├── home/               # Home Dashboard
│   │   ├── marketplace/        # Proxy marketplace
│   │   ├── proxies/            # My Proxies
│   │   ├── connection/         # Connection settings
│   │   ├── orders/             # Purchase history
│   │   ├── account/            # User account
│   │   └── admin/              # Admin dashboard
│   ├── components/             # Reusable components
│   ├── theme/                  # Material 3 theming
│   └── navigation/             # Compose navigation
│
├── services/                    # Android services
│   ├── vpn/                    # VPN/Proxy engine
│   │   └── ProxyVpnService.kt
│   ├── connection/             # Connection monitoring
│   └── notification/           # Push notifications
│
├── data/                        # Data layer
│   ├── local/                  # Room DB & DataStore
│   ├── remote/                 # Supabase/API
│   ├── repository/             # Data repository
│   └── models/                 # Data models
│
├── domain/                      # Business logic
│   ├── models/
│   ├── repositories/
│   └── usecases/
│
├── di/                          # Hilt dependency injection
├── workers/                     # WorkManager tasks
├── utils/                       # Utilities
│   ├── encryption.kt
│   ├── network.kt
│   └── permissions.kt
│
└── MainActivity.kt              # Entry point
```

### Configuration

Update `build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://api.proxyplatform.com/\"")
buildConfigField("String", "SUPABASE_URL", "\"https://your-project.supabase.co\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"your_key\"")
```

### Building Release APK

```bash
# 1. Create keystore
keytool -genkey -v -keystore release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias release-key

# 2. Configure signing in build.gradle.kts
signingConfigs {
    release {
        storeFile = file("path/to/release.jks")
        storePassword = "..."
        keyAlias = "release-key"
        keyPassword = "..."
    }
}

# 3. Build signed APK
./gradlew assembleRelease

# APK: app/build/outputs/apk/release/app-release.apk
```

---

## 🚀 Deployment

### Backend Deployment

#### Docker

```bash
# Build image
docker build -t proxy-platform-api:latest backend/

# Run container
docker run -p 3000:3000 \
  -e SUPABASE_URL="..." \
  -e JWT_SECRET="..." \
  proxy-platform-api:latest
```

#### Using Docker Compose

```bash
# Build all services
docker-compose build

# Start services
docker-compose up -d

# View logs
docker-compose logs -f backend
```

#### Kubernetes

```bash
# Create namespace
kubectl create namespace proxy-platform

# Deploy API
kubectl apply -f devops/kubernetes/api-deployment.yaml

# Deploy Redis
kubectl apply -f devops/kubernetes/redis-deployment.yaml

# Check status
kubectl get pods -n proxy-platform
```

### Android App Deployment

#### Firebase App Distribution

```bash
# Install firebase CLI
npm install -g firebase-tools

# Distribute debug build
./gradlew assembleDebug appDistributionUploadDebug

# Distribute release build
./gradlew assembleRelease appDistributionUploadRelease
```

#### Google Play Store

```bash
# Build release bundle
./gradlew bundleRelease

# Upload using Play Console or:
bundletool install-apks \
  --apks=app-release.apks \
  --device-id=emulator-5554
```

### GitHub Actions (CI/CD)

Push to GitHub to trigger automated builds:

```bash
git push origin main
```

Workflows:
- `.github/workflows/build.yml` - Builds Android APK/AAB & Backend
- `.github/workflows/test.yml` - Runs tests
- `.github/workflows/deploy.yml` - Production deployment

---

## 📚 API Documentation

### Base URL
```
Development: http://localhost:3000/api/v1
Production: https://api.proxyplatform.com/api/v1
```

### Authentication
All requests require JWT token in header:
```
Authorization: Bearer <token>
```

### Core Endpoints

#### Authentication
```
POST   /auth/register          - Register new user
POST   /auth/login             - Login user
POST   /auth/refresh           - Refresh JWT token
POST   /auth/logout            - Logout user
POST   /auth/forgot-password   - Request password reset
POST   /auth/reset-password    - Reset password
```

#### Proxies
```
GET    /proxies                - Get available proxies (marketplace)
GET    /proxies/:id            - Get proxy details
GET    /proxies/user/mine      - Get user's purchased proxies
POST   /proxies/:id/test       - Test proxy connectivity
POST   /proxies/:id/favorite   - Add to favorites
DELETE /proxies/:id/favorite   - Remove from favorites
```

#### Subscriptions
```
GET    /subscriptions          - Get user subscriptions
POST   /subscriptions          - Create subscription
PUT    /subscriptions/:id      - Update subscription
DELETE /subscriptions/:id      - Cancel subscription
POST   /subscriptions/:id/renew - Renew subscription
```

#### Orders
```
GET    /orders                 - Get user orders
POST   /orders                 - Create order
GET    /orders/:id             - Get order details
POST   /orders/:id/invoice     - Download invoice
```

#### Payments
```
POST   /payments/create        - Create payment
POST   /payments/webhook       - Payment webhook handler
GET    /payments/:id/status    - Check payment status
```

#### User Account
```
GET    /users/profile          - Get current user
PUT    /users/profile          - Update profile
POST   /users/change-password  - Change password
POST   /users/settings         - Update settings
GET    /users/devices          - List user devices
DELETE /users/devices/:id      - Remove device
```

#### Admin
```
GET    /admin/dashboard        - Dashboard stats
GET    /admin/users            - List all users
GET    /admin/proxies          - Manage proxies
POST   /admin/proxies          - Add proxy
PUT    /admin/proxies/:id      - Edit proxy
DELETE /admin/proxies/:id      - Delete proxy
GET    /admin/analytics        - Analytics data
```

See `docs/API.md` for complete API documentation.

---

## 🔒 Security Considerations

### 1. Environment Variables
- ✅ Never commit `.env` file
- ✅ Use `.env.example` as template
- ✅ Rotate secrets regularly
- ✅ Use strong encryption keys (32 bytes minimum)

### 2. Database Security
- ✅ Enable Row-Level Security (RLS)
- ✅ Use parameterized queries (ORM handles this)
- ✅ Regular backups & point-in-time recovery
- ✅ Monitor query logs for suspicious activity

### 3. API Security
- ✅ HTTPS only in production
- ✅ Rate limiting on all endpoints
- ✅ CORS properly configured
- ✅ Input validation & sanitization
- ✅ JWT token expiration (24 hours)
- ✅ Refresh token rotation

### 4. Credential Storage
- ✅ Proxy passwords encrypted with AES-256
- ✅ Sensitive data encrypted before database storage
- ✅ No credentials in logs
- ✅ Use Android Keystore for app secrets

### 5. Android App Security
- ✅ ProGuard/R8 obfuscation enabled
- ✅ Certificate pinning recommended
- ✅ WebView hardening
- ✅ Secure storage using Keystore
- ✅ No hardcoded credentials

### 6. Infrastructure Security
- ✅ Firewall rules (allow only necessary ports)
- ✅ DDoS protection (Cloudflare/AWS Shield)
- ✅ SSL/TLS certificates (Let's Encrypt)
- ✅ Regular security audits
- ✅ Penetration testing

---

## 🐛 Troubleshooting

### Backend Issues

#### "Cannot connect to Supabase"
```bash
# Verify credentials
echo $SUPABASE_URL
echo $SUPABASE_ANON_KEY

# Check network connectivity
curl https://your-project.supabase.co

# Verify API key is valid in Supabase console
```

#### "JWT token invalid"
```bash
# Regenerate JWT_SECRET
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"

# Update .env
JWT_SECRET=<new_value>

# Restart server
npm run dev
```

#### "Database connection timeout"
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Verify connection string
postgresql://user:password@host:port/database

# Check firewall
nc -zv localhost 5432
```

### Android Issues

#### "Build fails: No space left on device"
```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle

# Free up disk space
docker system prune -a
rm -rf ~/Library/Android/sdk/build-tools/**/examples
```

#### "Cannot connect to API"
```bash
# Check build config
grep "API_BASE_URL" app/build.gradle.kts

# Verify backend is running
curl http://localhost:3000/api/health

# For Android emulator: Use 10.0.2.2 instead of localhost
```

#### "VPN service crashes"
```bash
# Check logcat
adb logcat | grep ProxyVpnService

# Verify permissions in AndroidManifest.xml
grep "android.permission.BIND_VPN_SERVICE"

# Rebuild and reinstall
./gradlew clean assembleDebug
adb uninstall com.proxyplatform.app
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Docker Compose Issues

#### "Port already in use"
```bash
# Find process using port
lsof -i :3000

# Kill process
kill -9 <PID>

# Or use different port
docker-compose up -e PORT=3001
```

#### "Service fails to start"
```bash
# Check logs
docker-compose logs -f service-name

# Verify environment variables
docker-compose config

# Ensure .env file exists
cp .env.example .env
```

---

## 📞 Support

- 📧 Email: support@proxyplatform.com
- 💬 GitHub Issues: https://github.com/yourusername/proxy-platform/issues
- 📖 Documentation: See `/docs` directory
- 🐛 Bug Report: Create GitHub issue with logs

---

## 📄 License

This project is licensed under the MIT License - see `LICENSE` file for details.

---

## 🤝 Contributing

See `docs/CONTRIBUTING.md` for guidelines.

## 🎯 Roadmap

- [ ] Web dashboard
- [ ] iOS app (Kotlin Multiplatform)
- [ ] Advanced analytics
- [ ] API v2 with GraphQL
- [ ] Machine learning for proxy selection
- [ ] Blockchain integration (optional)

---

**Last Updated**: 2024  
**Version**: 1.0.0-stable
