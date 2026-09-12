import express, { Express, Request, Response, NextFunction } from 'express';
import 'express-async-errors';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import config from '@/config';
import { Logger } from '@/utils/logger';
import { testSupabaseConnection } from '@/utils/supabase';

// Route imports (to be created)
// import authRoutes from '@/routes/auth';
// import userRoutes from '@/routes/users';
// import proxyRoutes from '@/routes/proxies';
// import orderRoutes from '@/routes/orders';
// import paymentRoutes from '@/routes/payments';

const app: Express = express();
const logger = new Logger('App');

// ============================================================================
// SECURITY MIDDLEWARE
// ============================================================================

// Helmet - Set various HTTP headers
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      scriptSrc: ["'self'"],
      imgSrc: ["'self'", 'data:', 'https:']
    }
  },
  hsts: { maxAge: 31536000, includeSubDomains: true }
}));

// CORS configuration
app.use(cors({
  origin: (origin, callback) => {
    const allowedOrigins = [
      config.urls.frontend,
      config.urls.adminFrontend,
      'http://localhost:3001',
      'http://localhost:3002',
      'http://localhost:8080' // Android debug server
    ];

    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('CORS policy violation'));
    }
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
  maxAge: 86400
}));

// ============================================================================
// REQUEST PARSING MIDDLEWARE
// ============================================================================

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ limit: '10mb', extended: true }));

// ============================================================================
// LOGGING MIDDLEWARE
// ============================================================================

app.use(morgan(':remote-addr - :remote-user [:date[clf]] ":method :url HTTP/:http-version" :status :res[content-length] ":referrer" ":user-agent" :response-time ms'));

// ============================================================================
// RATE LIMITING
// ============================================================================

const limiter = rateLimit({
  windowMs: config.rateLimit.windowMs,
  max: config.rateLimit.maxRequests,
  message: 'Too many requests from this IP, please try again later.',
  standardHeaders: true,
  legacyHeaders: false,
  skip: (req: Request) => {
    // Skip rate limiting for health check
    return req.path === '/api/health';
  }
});

app.use('/api/', limiter);

// Stricter rate limiting for auth endpoints
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5,
  skipSuccessfulRequests: true
});

// ============================================================================
// HEALTH CHECK ENDPOINT
// ============================================================================

app.get('/api/health', (req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    version: config.app.version,
    environment: config.app.env
  });
});

// ============================================================================
// API ROUTES (To be implemented)
// ============================================================================

// app.use('/api/v1/auth', authLimiter, authRoutes);
// app.use('/api/v1/users', userRoutes);
// app.use('/api/v1/proxies', proxyRoutes);
// app.use('/api/v1/orders', orderRoutes);
// app.use('/api/v1/payments', paymentRoutes);
// app.use('/api/v1/admin', adminRoutes);

// ============================================================================
// ERROR HANDLING MIDDLEWARE
// ============================================================================

// 404 handler
app.use((req: Request, res: Response) => {
  res.status(404).json({
    success: false,
    message: 'Endpoint not found',
    path: req.path,
    method: req.method
  });
});

// Global error handler
app.use((error: Error, req: Request, res: Response, next: NextFunction) => {
  logger.error('Unhandled error', error);

  // Default error response
  res.status(500).json({
    success: false,
    message: config.app.isDevelopment ? error.message : 'Internal server error',
    ...(config.app.isDevelopment && { stack: error.stack })
  });
});

// ============================================================================
// SERVER INITIALIZATION
// ============================================================================

export async function startServer(): Promise<void> {
  try {
    // Test Supabase connection
    logger.info('Testing Supabase connection...');
    const supabaseConnected = await testSupabaseConnection();

    if (!supabaseConnected) {
      logger.error('Failed to connect to Supabase');
      process.exit(1);
    }

    // Start Express server
    const server = app.listen(config.app.port, () => {
      logger.info(`
        🚀 Proxy Platform API Server Started
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Environment: ${config.app.env}
        Port: ${config.app.port}
        Version: ${config.app.version}
        URL: http://localhost:${config.app.port}
      `);
    });

    // Graceful shutdown
    process.on('SIGTERM', () => {
      logger.info('SIGTERM received, shutting down gracefully...');
      server.close(() => {
        logger.info('Server closed');
        process.exit(0);
      });
    });

    process.on('SIGINT', () => {
      logger.info('SIGINT received, shutting down gracefully...');
      server.close(() => {
        logger.info('Server closed');
        process.exit(0);
      });
    });

  } catch (error) {
    logger.error('Failed to start server', error as Error);
    process.exit(1);
  }
}

export default app;
