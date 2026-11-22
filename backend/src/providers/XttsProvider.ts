import axios from 'axios';
import FormData from 'form-data';
import fs from 'fs';
import {
  VoiceAIProvider,
  CreateVoiceInput,
  CreatedVoiceResult,
  GenerateSpeechInput,
  GenerateSpeechResult,
  ProviderHealth,
} from './VoiceAIProvider';

/**
 * Connects to an external GPU-hosted XTTS-v2 compatible HTTP inference server.
 * Note: Render Node.js web services run on CPU; GPU XTTS inference must be hosted externally
 * (e.g., HuggingFace Spaces GPU, RunPod, Vast.ai, or self-hosted GPU node) and configured via AI_INFERENCE_URL.
 */
export class XttsProvider implements VoiceAIProvider {
  readonly name = 'xtts';
  readonly supportedLanguages = [
    'en', 'hi', 'bn', 'es', 'fr', 'de', 'it', 'pt', 'pl', 'tr', 'ru', 'nl', 'cs', 'ar', 'zh-cn', 'ja', 'hu', 'ko'
  ];

  constructor(private readonly inferenceUrl: string, private readonly apiKey?: string) {}

  private get headers() {
    return this.apiKey ? { Authorization: `Bearer ${this.apiKey}` } : {};
  }

  private assertConfigured() {
    if (!this.inferenceUrl || !this.inferenceUrl.startsWith('http')) {
      throw new Error(
        'XTTS Inference URL is not configured. Set AI_INFERENCE_URL in Render or Settings to your external XTTS-v2 server.'
      );
    }
  }

  async checkHealth(): Promise<ProviderHealth> {
    if (!this.inferenceUrl || !this.inferenceUrl.startsWith('http')) {
      return {
        configured: false,
        reachable: false,
        providerName: 'XTTS-v2 Open-Source Engine',
        supportedLanguages: this.supportedLanguages,
        message: 'Missing AI_INFERENCE_URL. Configure external XTTS-v2 inference endpoint in Settings.',
      };
    }

    try {
      const baseUrl = this.inferenceUrl.replace(/\/$/, '');
      await axios.get(`${baseUrl}/health`, { headers: this.headers, timeout: 6000 });
      return {
        configured: true,
        reachable: true,
        providerName: 'XTTS-v2 Open-Source Engine',
        supportedLanguages: this.supportedLanguages,
        message: 'Connected to external XTTS-v2 inference node.',
      };
    } catch (err: any) {
      return {
        configured: true,
        reachable: false,
        providerName: 'XTTS-v2 Open-Source Engine',
        supportedLanguages: this.supportedLanguages,
        message: `XTTS inference server unreachable: ${err?.message || 'Connection failed'}`,
      };
    }
  }

  async createVoice(input: CreateVoiceInput): Promise<CreatedVoiceResult> {
    this.assertConfigured();
    const baseUrl = this.inferenceUrl.replace(/\/$/, '');

    const form = new FormData();
    form.append('name', input.name);
    form.append('speaker_wav', fs.createReadStream(input.wavFilePath), {
      filename: 'reference.wav',
      contentType: 'audio/wav',
    });

    const response = await axios.post(`${baseUrl}/api/voices/register`, form, {
      headers: { ...this.headers, ...form.getHeaders() },
      timeout: 60000,
    });

    const speakerId = response.data?.speaker_id || response.data?.voice_id;
    if (!speakerId) {
      throw new Error('XTTS inference server did not confirm a valid speaker_id.');
    }

    return {
      providerVoiceId: String(speakerId),
      status: 'READY',
    };
  }

  async listVoices(): Promise<Array<{ id: string; name: string }>> {
    this.assertConfigured();
    const baseUrl = this.inferenceUrl.replace(/\/$/, '');
    const res = await axios.get(`${baseUrl}/api/voices`, { headers: this.headers, timeout: 15000 });
    return Array.isArray(res.data?.voices) ? res.data.voices : [];
  }

  async deleteVoice(providerVoiceId: string): Promise<void> {
    this.assertConfigured();
    const baseUrl = this.inferenceUrl.replace(/\/$/, '');
    await axios.delete(`${baseUrl}/api/voices/${encodeURIComponent(providerVoiceId)}`, {
      headers: this.headers,
      timeout: 15000,
    });
  }

  async previewVoice(providerVoiceId: string, sampleWavBuffer?: Buffer): Promise<GenerateSpeechResult> {
    return this.generateSpeech({
      providerVoiceId,
      text: 'Hello! This is a verified voice clone preview generated inside Vox AI Studio.',
      language: 'en',
      sampleWavBuffer,
    });
  }

  async generateSpeech(input: GenerateSpeechInput): Promise<GenerateSpeechResult> {
    this.assertConfigured();
    if (!this.supportedLanguages.includes(input.language)) {
      throw new Error(`Language '${input.language}' is not supported by XTTS-v2 provider.`);
    }

    const baseUrl = this.inferenceUrl.replace(/\/$/, '');

    if (input.sampleWavBuffer) {
      const form = new FormData();
      form.append('text', input.text);
      form.append('language', input.language);
      form.append('speaker_id', input.providerVoiceId);
      form.append('speaker_wav', input.sampleWavBuffer, {
        filename: 'speaker.wav',
        contentType: 'audio/wav',
      });

      const res = await axios.post(`${baseUrl}/api/tts`, form, {
        headers: { ...this.headers, ...form.getHeaders() },
        responseType: 'arraybuffer',
        timeout: 180000,
      });

      return {
        audioBuffer: Buffer.from(res.data),
        format: 'wav',
        supportsWav: true,
      };
    }

    const res = await axios.post(
      `${baseUrl}/api/tts`,
      {
        text: input.text,
        language: input.language,
        speaker_id: input.providerVoiceId,
      },
      {
        headers: { ...this.headers, 'Content-Type': 'application/json' },
        responseType: 'arraybuffer',
        timeout: 180000,
      }
    );

    return {
      audioBuffer: Buffer.from(res.data),
      format: 'wav',
      supportsWav: true,
    };
  }
}
