import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import { env } from './config/env';
import { logger } from './utils/logger';
import healthRouter from './routes/health';
import voicesRouter from './routes/voices';
import dubbingRouter from './routes/dubbing';
import settingsRouter from './routes/settings';

const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '2mb' }));
app.use(morgan('combined'));

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 300,
  standardHeaders: true,
  legacyHeaders: false,
});
app.use('/api', limiter);

app.use('/api/health', healthRouter);
app.use('/api/voices', voicesRouter);
app.use('/api/dubbing', dubbingRouter);
app.use('/api/settings', settingsRouter);

app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  logger.error('Unhandled API error', { message: err?.message });
  res.status(err?.status || 500).json({
    success: false,
    error: err?.message || 'Internal server error',
  });
});

const port = Number(env.PORT) || 3000;
app.listen(port, '0.0.0.0', () => {
  logger.info(`Vox AI Studio Backend running on port ${port}`);
});
