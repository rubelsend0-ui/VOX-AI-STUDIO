import { Router } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, requireApiKey } from '../middleware/auth';
import { prisma } from '../services/prisma';
import { getActiveProvider } from '../providers/ProviderFactory';
import { enqueueDubbingJob } from '../services/dubbingWorker';
import { getPresignedDownloadUrl } from '../services/storageService';

const router = Router();
router.use(requireApiKey);

const generateSchema = z.object({
  voiceId: z.string().uuid(),
  scriptText: z.string().min(1).max(10000),
  language: z.string().min(2).max(10).default('en'),
  projectTitle: z.string().max(100).optional(),
  saveProject: z.boolean().optional().default(true),
});

router.post('/generate', async (req: AuthenticatedRequest, res) => {
  const parsed = generateSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({
      success: false,
      error: 'Invalid dubbing request parameters.',
      details: parsed.error.flatten(),
    });
  }

  const voice = await prisma.voiceProfile.findFirst({
    where: { id: parsed.data.voiceId, userId: req.userId! },
  });
  if (!voice) {
    return res.status(404).json({ success: false, error: 'Selected voice profile not found.' });
  }

  const provider = await getActiveProvider(req.userId!);
  if (!provider.supportedLanguages.includes(parsed.data.language)) {
    return res.status(400).json({
      success: false,
      error: `Language '${parsed.data.language}' is not supported by active provider (${provider.name}). Supported: ${provider.supportedLanguages.join(', ')}`,
    });
  }

  let projectId: string | null = null;
  if (parsed.data.saveProject) {
    const project = await prisma.audioProject.create({
      data: {
        userId: req.userId!,
        voiceId: voice.id,
        title: parsed.data.projectTitle?.trim() || `Dubbing ${new Date().toLocaleDateString()}`,
        scriptText: parsed.data.scriptText,
        language: parsed.data.language,
      },
    });
    projectId = project.id;
  }

  const generation = await prisma.generationHistory.create({
    data: {
      userId: req.userId!,
      projectId,
      voiceId: voice.id,
      scriptText: parsed.data.scriptText,
      language: parsed.data.language,
      provider: provider.name,
      status: 'QUEUED',
      progress: 0,
    },
  });

  enqueueDubbingJob(generation.id);

  res.status(202).json({
    success: true,
    generation: {
      id: generation.id,
      status: generation.status,
      progress: generation.progress,
      projectId,
    },
  });
});

router.get('/history', async (req: AuthenticatedRequest, res) => {
  const items = await prisma.generationHistory.findMany({
    where: { userId: req.userId! },
    orderBy: { createdAt: 'desc' },
    take: 50,
    include: {
      voice: { select: { id: true, name: true } },
      project: { select: { id: true, title: true } },
    },
  });

  const enriched = await Promise.all(
    items.map(async (item) => ({
      id: item.id,
      voiceId: item.voiceId,
      voiceName: item.voice.name,
      projectTitle: item.project?.title || 'Quick Dub',
      scriptText: item.scriptText,
      language: item.language,
      provider: item.provider,
      status: item.status,
      progress: item.progress,
      durationSeconds: item.durationSeconds,
      errorMessage: item.errorMessage,
      mp3Url: item.mp3StorageKey ? await getPresignedDownloadUrl(item.mp3StorageKey, 3600) : null,
      wavUrl: item.wavStorageKey ? await getPresignedDownloadUrl(item.wavStorageKey, 3600) : null,
      createdAt: item.createdAt,
    }))
  );

  res.json({ success: true, history: enriched });
});

router.get('/:id', async (req: AuthenticatedRequest, res) => {
  const item = await prisma.generationHistory.findFirst({
    where: { id: req.params.id, userId: req.userId! },
    include: {
      voice: { select: { id: true, name: true } },
      project: { select: { id: true, title: true } },
    },
  });

  if (!item) return res.status(404).json({ success: false, error: 'Generation record not found.' });

  res.json({
    success: true,
    generation: {
      id: item.id,
      voiceId: item.voiceId,
      voiceName: item.voice.name,
      projectTitle: item.project?.title || 'Quick Dub',
      scriptText: item.scriptText,
      language: item.language,
      provider: item.provider,
      status: item.status,
      progress: item.progress,
      durationSeconds: item.durationSeconds,
      errorMessage: item.errorMessage,
      mp3Url: item.mp3StorageKey ? await getPresignedDownloadUrl(item.mp3StorageKey, 3600) : null,
      wavUrl: item.wavStorageKey ? await getPresignedDownloadUrl(item.wavStorageKey, 3600) : null,
      createdAt: item.createdAt,
    },
  });
});

router.get('/:id/download', async (req: AuthenticatedRequest, res) => {
  const format = req.query.format === 'wav' ? 'wav' : 'mp3';
  const item = await prisma.generationHistory.findFirst({
    where: { id: req.params.id, userId: req.userId! },
  });

  if (!item || item.status !== 'COMPLETED') {
    return res.status(404).json({ success: false, error: 'Completed audio file not available.' });
  }

  const key = format === 'wav' ? item.wavStorageKey : item.mp3StorageKey;
  if (!key) {
    return res.status(400).json({ success: false, error: `Requested format (${format}) is unavailable.` });
  }

  const downloadUrl = await getPresignedDownloadUrl(key, 900);
  res.json({ success: true, format, downloadUrl });
});

export default router;
