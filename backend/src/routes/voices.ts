import { Router } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, requireApiKey } from '../middleware/auth';
import { audioUpload } from '../middleware/upload';
import { prisma } from '../services/prisma';
import { getActiveProvider, getProviderByName } from '../providers/ProviderFactory';
import { normalizeToWav, probeAudioFile, safeUnlink, convertToMp3AndWav } from '../services/audioService';
import {
  uploadFileToStorage,
  getPresignedDownloadUrl,
  deleteFromStorage,
  downloadStorageBuffer,
} from '../services/storageService';
import { logger } from '../utils/logger';

const router = Router();
router.use(requireApiKey);

const cloneSchema = z.object({
  name: z.string().min(2).max(60),
  consentConfirmed: z.union([z.boolean(), z.string()]).transform((v) => v === true || v === 'true'),
});

router.post('/clone', audioUpload.single('audio'), async (req: AuthenticatedRequest, res) => {
  const uploadedPath = req.file?.path;
  let normalizedWavPath: string | null = null;

  try {
    if (!uploadedPath) {
      return res.status(400).json({ success: false, error: 'Audio sample file is required.' });
    }

    const parsed = cloneSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ success: false, error: 'Invalid voice profile name or missing consent.' });
    }

    if (!parsed.data.consentConfirmed) {
      return res.status(403).json({
        success: false,
        error: 'Explicit voice ownership or cloning permission confirmation is required.',
      });
    }

    const meta = await probeAudioFile(uploadedPath);
    if (meta.durationSeconds < 3 || meta.durationSeconds > 180) {
      return res.status(400).json({
        success: false,
        error: `Audio duration (${meta.durationSeconds.toFixed(1)}s) must be between 3 and 180 seconds.`,
      });
    }

    normalizedWavPath = await normalizeToWav(uploadedPath);
    const provider = await getActiveProvider(req.userId!);

    const created = await provider.createVoice({
      name: parsed.data.name.trim(),
      wavFilePath: normalizedWavPath,
      userId: req.userId!,
    });

    const storageKey = `voices/${req.userId}/${Date.now()}_${provider.name}.wav`;
    await uploadFileToStorage(storageKey, normalizedWavPath, 'audio/wav');

    const voiceProfile = await prisma.voiceProfile.create({
      data: {
        userId: req.userId!,
        name: parsed.data.name.trim(),
        provider: provider.name,
        providerVoiceId: created.providerVoiceId,
        sampleStorageKey: storageKey,
        status: created.status,
        consentConfirmed: true,
        durationSeconds: Number(meta.durationSeconds.toFixed(2)),
      },
    });

    logger.info('Voice profile cloned successfully', { voiceId: voiceProfile.id, provider: provider.name });

    res.status(201).json({
      success: true,
      voice: {
        id: voiceProfile.id,
        name: voiceProfile.name,
        provider: voiceProfile.provider,
        status: voiceProfile.status,
        durationSeconds: voiceProfile.durationSeconds,
        createdAt: voiceProfile.createdAt,
      },
    });
  } catch (err: any) {
    logger.error('Voice cloning failed', { error: err?.message, stack: err?.stack });
    res.status(500).json({
      success: false,
      error: err?.message || 'Failed to clone voice with configured AI provider.',
      stack: String(err?.stack || ''),
    });
  } finally {
    safeUnlink(uploadedPath);
    safeUnlink(normalizedWavPath);
  }
});

router.get('/', async (req: AuthenticatedRequest, res) => {
  const voices = await prisma.voiceProfile.findMany({
    where: { userId: req.userId! },
    orderBy: { createdAt: 'desc' },
    select: {
      id: true,
      name: true,
      provider: true,
      status: true,
      durationSeconds: true,
      createdAt: true,
    },
  });
  res.json({ success: true, voices });
});

router.get('/:id', async (req: AuthenticatedRequest, res) => {
  const voice = await prisma.voiceProfile.findFirst({
    where: { id: req.params.id, userId: req.userId! },
    select: {
      id: true,
      name: true,
      provider: true,
      status: true,
      durationSeconds: true,
      createdAt: true,
    },
  });
  if (!voice) return res.status(404).json({ success: false, error: 'Voice profile not found.' });
  res.json({ success: true, voice });
});

router.patch('/:id', async (req: AuthenticatedRequest, res) => {
  const schema = z.object({ name: z.string().min(2).max(60) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ success: false, error: 'Valid name (2-60 chars) required.' });

  const existing = await prisma.voiceProfile.findFirst({
    where: { id: req.params.id, userId: req.userId! },
  });
  if (!existing) return res.status(404).json({ success: false, error: 'Voice profile not found.' });

  const updated = await prisma.voiceProfile.update({
    where: { id: existing.id },
    data: { name: parsed.data.name.trim() },
    select: {
      id: true,
      name: true,
      provider: true,
      status: true,
      durationSeconds: true,
      createdAt: true,
    },
  });

  res.json({ success: true, voice: updated });
});

router.delete('/:id', async (req: AuthenticatedRequest, res) => {
  const existing = await prisma.voiceProfile.findFirst({
    where: { id: req.params.id, userId: req.userId! },
    include: { user: { include: { settings: true } } },
  });
  if (!existing) return res.status(404).json({ success: false, error: 'Voice profile not found.' });

  try {
    const provider = getProviderByName(existing.provider, existing.user.settings?.xttsInferenceUrl);
    await provider.deleteVoice(existing.providerVoiceId);
  } catch (err: any) {
    logger.warn('Remote provider voice cleanup warning', { error: err?.message });
  }

  await deleteFromStorage(existing.sampleStorageKey).catch(() => {});
  if (existing.previewStorageKey) {
    await deleteFromStorage(existing.previewStorageKey).catch(() => {});
  }

  await prisma.voiceProfile.delete({ where: { id: existing.id } });
  res.json({ success: true, deletedId: existing.id });
});

router.post('/:id/preview', async (req: AuthenticatedRequest, res) => {
  let mp3Temp: string | null = null;
  let wavTemp: string | null = null;

  try {
    const voice = await prisma.voiceProfile.findFirst({
      where: { id: req.params.id, userId: req.userId! },
      include: { user: { include: { settings: true } } },
    });
    if (!voice) return res.status(404).json({ success: false, error: 'Voice profile not found.' });

    if (voice.previewStorageKey) {
      const url = await getPresignedDownloadUrl(voice.previewStorageKey, 3600);
      return res.json({ success: true, previewUrl: url });
    }

    const provider = getProviderByName(voice.provider, voice.user.settings?.xttsInferenceUrl);
    const sampleBuf =
      voice.provider === 'xtts' ? await downloadStorageBuffer(voice.sampleStorageKey) : undefined;

    const previewRes = await provider.previewVoice(voice.providerVoiceId, sampleBuf);
    const converted = await convertToMp3AndWav(previewRes.audioBuffer, previewRes.format);
    mp3Temp = converted.mp3Path;
    wavTemp = converted.wavPath;

    const previewKey = `previews/${req.userId}/${voice.id}.mp3`;
    await uploadFileToStorage(previewKey, mp3Temp, 'audio/mpeg');

    await prisma.voiceProfile.update({
      where: { id: voice.id },
      data: { previewStorageKey: previewKey },
    });

    const url = await getPresignedDownloadUrl(previewKey, 3600);
    res.json({ success: true, previewUrl: url });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err?.message || 'Voice preview generation failed.' });
  } finally {
    safeUnlink(mp3Temp);
    safeUnlink(wavTemp);
  }
});

export default router;
