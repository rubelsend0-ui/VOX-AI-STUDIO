import { PrismaClient } from '@prisma/client';
import { env } from '../config/env';

export const prisma = new PrismaClient();

export async function ensureOwnerUser() {
  const ownerEmail = 'owner@voxstudio.local';
  let user = await prisma.user.findUnique({ where: { email: ownerEmail } });
  if (!user) {
    user = await prisma.user.create({
      data: {
        email: ownerEmail,
        displayName: 'Studio Owner',
        settings: {
          create: {
            activeProvider: env.AI_PROVIDER,
            xttsInferenceUrl: env.AI_INFERENCE_URL || null,
            externalApiKeyHint: env.AI_PROVIDER_API_KEY ? `****${env.AI_PROVIDER_API_KEY.slice(-4)}` : null,
            defaultLanguage: 'en',
          },
        },
      },
    });
  }
  return user;
}
