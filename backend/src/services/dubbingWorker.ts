import { prisma } from './prisma';
import { getProviderByName } from '../providers/ProviderFactory';
import { downloadStorageBuffer, uploadFileToStorage } from './storageService';
import { concatWavBuffers, convertToMp3AndWav, safeUnlink } from './audioService';
import { logger } from '../utils/logger';

function splitScriptIntoChunks(text: string, maxChars = 420): string[] {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean.length <= maxChars) return [clean];

  const sentences = clean.match(/[^.!?।]+[.!?।]*\s*/g) || [clean];
  const chunks: string[] = [];
  let current = '';

  for (const sentence of sentences) {
    if ((current + sentence).length <= maxChars) {
      current += sentence;
    } else {
      if (current.trim()) chunks.push(current.trim());
      if (sentence.length > maxChars) {
        for (let i = 0; i < sentence.length; i += maxChars) {
          chunks.push(sentence.slice(i, i + maxChars).trim());
        }
        current = '';
      } else {
        current = sentence;
      }
    }
  }
  if (current.trim()) chunks.push(current.trim());
  return chunks;
}

export function enqueueDubbingJob(generationId: string) {
  setImmediate(async () => {
    let mp3Temp: string | null = null;
    let wavTemp: string | null = null;

    try {
      const job = await prisma.generationHistory.findUnique({
        where: { id: generationId },
        include: { voice: true, user: { include: { settings: true } } },
      });

      if (!job) return;

      await prisma.generationHistory.update({
        where: { id: generationId },
        data: { status: 'PROCESSING', progress: 10 },
      });

      const provider = getProviderByName(job.provider, job.user.settings?.xttsInferenceUrl);

      if (!provider.supportedLanguages.includes(job.language)) {
        throw new Error(`Language '${job.language}' is unsupported by ${provider.name}.`);
      }

      let sampleWavBuffer: Buffer | undefined;
      if (job.provider === 'xtts' && job.voice.sampleStorageKey) {
        sampleWavBuffer = await downloadStorageBuffer(job.voice.sampleStorageKey);
      }

      const chunks = splitScriptIntoChunks(job.scriptText, 400);
      const chunkAudioBuffers: Buffer[] = [];
      let detectedFormat: 'wav' | 'mp3' = 'wav';

      for (let i = 0; i < chunks.length; i++) {
        const result = await provider.generateSpeech({
          providerVoiceId: job.voice.providerVoiceId,
          text: chunks[i],
          language: job.language,
          sampleWavBuffer,
        });

        detectedFormat = result.format;
        chunkAudioBuffers.push(result.audioBuffer);

        const progress = Math.min(80, Math.round(15 + ((i + 1) / chunks.length) * 65));
        await prisma.generationHistory.update({
          where: { id: generationId },
          data: { progress },
        });
      }

      const combinedRaw =
        chunkAudioBuffers.length === 1
          ? chunkAudioBuffers[0]
          : await concatWavBuffers(chunkAudioBuffers);

      const converted = await convertToMp3AndWav(combinedRaw, detectedFormat);
      mp3Temp = converted.mp3Path;
      wavTemp = converted.wavPath;

      const mp3Key = `dubbing/${job.userId}/${generationId}.mp3`;
      const wavKey = `dubbing/${job.userId}/${generationId}.wav`;

      await uploadFileToStorage(mp3Key, mp3Temp, 'audio/mpeg');
      await uploadFileToStorage(wavKey, wavTemp, 'audio/wav');

      await prisma.generationHistory.update({
        where: { id: generationId },
        data: {
          status: 'COMPLETED',
          progress: 100,
          mp3StorageKey: mp3Key,
          wavStorageKey: wavKey,
          durationSeconds: converted.duration,
        },
      });

      logger.info('Dubbing job completed', { generationId, duration: converted.duration });
    } catch (err: any) {
      logger.error('Dubbing job failed', { generationId, error: err?.message });
      await prisma.generationHistory.update({
        where: { id: generationId },
        data: {
          status: 'FAILED',
          errorMessage: err?.message || 'Speech synthesis failed',
        },
      });
    } finally {
      safeUnlink(mp3Temp);
      safeUnlink(wavTemp);
    }
  });
}
