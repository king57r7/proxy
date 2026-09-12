import dotenv from 'dotenv';
import { Logger } from '@/utils/logger';

// Load environment variables
dotenv.config();

const logger = new Logger('Config');

// Validate required environment variables
const requiredEnvVars = [
  'SUPABASE_URL',
  'SUPABASE_ANON_KEY',
  'SUPABASE_SERVICE_ROLE_KEY',
  'JWT_SECRET',
  'ENCRYPTION_KEY'
];

requiredEnvVars.forEach(envVar => {
  if (!process.env[envVar]) {
    logger.error(`Missing required environment variable: ${envVar}`);
    process.exit(1);
  }
});

export const config = {
  // App
  app: {
    name: 'Proxy Platform API',
    version: '1.0.0',
    env: process.env.NODE_ENV || 'development',
    port: parseInt(process.env.PORT || '3000', 10),
    apiVersion: process.env.API_VERSION || 'v1',
    isDevelopment: process.env.NODE_ENV === 'development',
    isProduction: process.env.NODE_ENV === 'production',
    isStaging: process.env.NODE_ENV === 'staging'
  },

  // Supabase
  supabase: {
    url: process.env.SUPABASE_URL!,
    anonKey: process.env.SUPABASE_ANON_KEY!,
    serviceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY!,
    databaseUrl: process.env.DATABASE_URL
  },

  // Authentication
  auth: {
    jwtSecret: process.env.JWT_SECRET!,
    jwtRefreshSecret: process.env.JWT_REFRESH_SECRET || process.env.JWT_SECRET!,
    jwtExpiration: process.env.JWT_EXPIRATION || '24h',
    jwtRefreshExpiration: process.env.JWT_REFRESH_EXPIRATION || '7d'
  },

  // Encryption
  encryption: {
    key: process.env.ENCRYPTION_KEY!,
    algorithm: process.env.ENCRYPTION_ALGORITHM || 'aes-256-cbc'
  },

  // Payment Providers
  payment: {
    stripe: {
      secretKey: process.env.STRIPE_SECRET_KEY,
      publicKey: process.env.STRIPE_PUBLIC_KEY,
      webhookSecret: process.env.STRIPE_WEBHOOK_SECRET
    },
    paypal: {
      clientId: process.env.PAYPAL_CLIENT_ID,
      clientSecret: process.env.PAYPAL_CLIENT_SECRET,
      mode: process.env.PAYPAL_MODE || 'sandbox'
    },
    razorpay: {
      keyId: process.env.RAZORPAY_KEY_ID,
      keySecret: process.env.RAZORPAY_KEY_SECRET
    }
  },

  // Email
  email: {
    smtp: {
      host: process.env.SMTP_HOST,
      port: parseInt(process.env.SMTP_PORT || '587', 10),
      username: process.env.SMTP_USERNAME,
      password: process.env.SMTP_PASSWORD,
      fromEmail: process.env.SMTP_FROM_EMAIL || 'noreply@proxyplatform.com',
      fromName: process.env.SMTP_FROM_NAME || 'Proxy Platform'
    },
    sendgrid: {
      apiKey: process.env.SENDGRID_API_KEY
    }
  },

  // Redis
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD,
    db: parseInt(process.env.REDIS_DB || '0', 10)
  },

  // AWS S3
  aws: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
    s3Bucket: process.env.AWS_S3_BUCKET,
    s3Region: process.env.AWS_S3_REGION || 'us-east-1'
  },

  // URLs
  urls: {
    frontend: process.env.FRONTEND_URL || 'http://localhost:3001',
    adminFrontend: process.env.ADMIN_FRONTEND_URL || 'http://localhost:3002',
    api: `http://localhost:${process.env.PORT || 3000}`
  },

  // Rate Limiting
  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000', 10),
    maxRequests: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '100', 10)
  },

  // Feature Flags
  features: {
    referral: process.env.FEATURE_REFERRAL === 'true',
    coupons: process.env.FEATURE_COUPONS === 'true',
    smartSelect: process.env.FEATURE_SMART_SELECT === 'true',
    autoProxySelection: process.env.FEATURE_AUTO_PROXY_SELECTION === 'true',
    analytics: process.env.FEATURE_ANALYTICS === 'true'
  },

  // Admin
  admin: {
    email: process.env.ADMIN_EMAIL || 'admin@proxyplatform.com',
    passwordHash: process.env.ADMIN_PASSWORD_HASH
  },

  // App Settings
  appSettings: {
    minAppVersion: process.env.MIN_APP_VERSION || '1.0.0',
    maintenanceMode: process.env.MAINTENANCE_MODE === 'true',
    maintenanceMessage: process.env.MAINTENANCE_MESSAGE || 'System is under maintenance'
  },

  // Proxy
  proxy: {
    testTimeoutMs: parseInt(process.env.PROXY_TEST_TIMEOUT_MS || '5000', 10),
    testUrl: process.env.PROXY_TEST_URL || 'http://ipinfo.io/json',
    healthCheckIntervalMinutes: parseInt(process.env.PROXY_HEALTH_CHECK_INTERVAL_MINUTES || '60', 10)
  },

  // Logging
  logging: {
    level: process.env.LOG_LEVEL || 'info',
    sentryDsn: process.env.SENTRY_DSN
  },

  // Webhooks
  webhooks: {
    signingSecret: process.env.WEBHOOK_SIGNING_SECRET
  },

  // Analytics
  analytics: {
    enabled: process.env.ANALYTICS_ENABLED === 'true',
    trackUserConnections: process.env.TRACK_USER_CONNECTIONS === 'true',
    collectUsageStats: process.env.COLLECT_USAGE_STATS === 'true'
  }
};

export default config;
