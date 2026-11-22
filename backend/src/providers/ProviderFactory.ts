import { env } from '../config/env';
import { prisma } from '../services/prisma';
import { VoiceAIProvider } from './VoiceAIProvider';
import { XttsProvider } from './XttsProvider';
import { ElevenLabsProvider } from './ElevenLabsProvider';

export async function getActiveProvider(userId: string): Promise<VoiceAIProvider> {
  const settings = await prisma.providerSettings.findUnique({ where: { userId } });
  const providerType = settings?.activeProvider || env.AI_PROVIDER;

  if (providerType === 'elevenlabs') {
    return new ElevenLabsProvider(env.AI_PROVIDER_API_KEY);
  }

  const inferenceUrl = settings?.xttsInferenceUrl || env.AI_INFERENCE_URL;
  return new XttsProvider(inferenceUrl, env.AI_PROVIDER_API_KEY || undefined);
}

export function getProviderByName(providerName: string, customXttsUrl?: string | null): VoiceAIProvider {
  if (providerName === 'elevenlabs') {
    return new ElevenLabsProvider(env.AI_PROVIDER_API_KEY);
  }
  return new XttsProvider(customXttsUrl || env.AI_INFERENCE_URL, env.AI_PROVIDER_API_KEY || undefined);
}
