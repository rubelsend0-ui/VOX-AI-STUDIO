import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { env } from '../config/env';
import { ensureOwnerUser } from '../services/prisma';

export interface AuthenticatedRequest extends Request {
  userId?: string;
}

function timingSafeMatch(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

export async function requireApiKey(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  try {
    const providedKey = req.header('x-api-key') || req.header('authorization')?.replace(/^Bearer\s+/i, '');
    if (!providedKey || !timingSafeMatch(providedKey, env.APP_API_KEY)) {
      return res.status(401).json({
        success: false,
        error: 'Unauthorized: Invalid or missing X-API-Key header.',
      });
    }

    const owner = await ensureOwnerUser();
    req.userId = owner.id;
    next();
  } catch (err) {
    next(err);
  }
}
