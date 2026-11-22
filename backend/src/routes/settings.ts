import { Router } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, requireApiKey } from '../middleware/auth';
import { prisma } from '../services/prisma';
import { getActiveProvider } from '../providers/ProviderFactory';
import { env } from '../config/env';

const router = Router();
router.use(requireApiKey);

router.get('/', async (req: AuthenticatedRequest, res) => {
  const settings = await prisma.providerSettings.findUnique({
    where: { userId: req.userId! },
  });
  const provider = await getActiveProvider(req.userId!);
  const health = await provider.checkHealth();

  res.json({
    success: true,
    settings: {
      activeProvider: settings?.activeProvider || env.AI_PROVIDER,
      xttsInferenceUrl: settings?.xttsInferenceUrl || env.AI_INFERENCE_URL || '',
      hasExternalApiKey: Boolean(env.AI_PROVIDER_API_KEY),
      defaultLanguage: settings?.defaultLanguage || 'en',
      supportedLanguages: provider.supportedLanguages,
      providerHealth: health,
    },
  });
});

router.patch('/', async (req: AuthenticatedRequest, res) => {
  const schema = z.object({
    activeProvider: z.enum(['xtts', 'elevenlabs']).optional(),
    xttsInferenceUrl: z.string().url().or(z.literal('')).optional(),
    defaultLanguage: z.string().min(2).max(10).optional(),
  });

  const parsed = schema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ success: false, error: 'Invalid settings payload.' });
  }

  const updated = await prisma.providerSettings.upsert({
    where: { userId: req.userId! },
    update: {
      ...(parsed.data.activeProvider ? { activeProvider: parsed.data.activeProvider } : {}),
      ...(parsed.data.xttsInferenceUrl !== undefined
        ? { xttsInferenceUrl: parsed.data.xttsInferenceUrl || null }
        : {}),
      ...(parsed.data.defaultLanguage ? { defaultLanguage: parsed.data.defaultLanguage } : {}),
    },
    create: {
      userId: req.userId!,
      activeProvider: parsed.data.activeProvider || env.AI_PROVIDER,
      xttsInferenceUrl: parsed.data.xttsInferenceUrl || env.AI_INFERENCE_URL || null,
      defaultLanguage: parsed.data.defaultLanguage || 'en',
    },
  });

  const provider = await getActiveProvider(req.userId!);
  const health = await provider.checkHealth();

  res.json({
    success: true,
    settings: {
      activeProvider: updated.activeProvider,
      xttsInferenceUrl: updated.xttsInferenceUrl || '',
      hasExternalApiKey: Boolean(env.AI_PROVIDER_API_KEY),
      defaultLanguage: updated.defaultLanguage,
      supportedLanguages: provider.supportedLanguages,
      providerHealth: health,
    },
  });
});

export default router;
