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

export class ElevenLabsProvider implements VoiceAIProvider {
  readonly name = 'elevenlabs';
  readonly supportedLanguages = [
    'en', 'hi', 'bn', 'es', 'fr', 'de', 'it', 'pt', 'pl', 'tr', 'ru', 'nl', 'cs', 'ar', 'zh', 'ja', 'ko', 'ta', 'te'
  ];
  private readonly baseUrl = 'https://api.elevenlabs.io/v1';

  constructor(private readonly apiKey: string) {}

  private assertConfigured() {
    if (!this.apiKey) {
      throw new Error('External AI Provider API Key is missing. Configure AI_PROVIDER_API_KEY in Render environment variables.');
    }
  }

  async checkHealth(): Promise<ProviderHealth> {
    if (!this.apiKey) {
      return {
        configured: false,
        reachable: false,
        providerName: 'External Cloud Voice API (ElevenLabs)',
        supportedLanguages: this.supportedLanguages,
        message: 'Missing AI_PROVIDER_API_KEY environment variable.',
      };
    }

    try {
      await axios.get(`${this.baseUrl}/user`, {
        headers: { 'xi-api-key': this.apiKey },
        timeout: 8000,
      });
      return {
        configured: true,
        reachable: true,
        providerName: 'External Cloud Voice API (ElevenLabs)',
        supportedLanguages: this.supportedLanguages,
        message: 'Connected to External Voice Cloning API.',
      };
    } catch (err: any) {
      return {
        configured: true,
        reachable: false,
        providerName: 'External Cloud Voice API (ElevenLabs)',
        supportedLanguages: this.supportedLanguages,
        message: `External API authentication or network error: ${err?.response?.status || err?.message}`,
      };
    }
  }

  async createVoice(input: CreateVoiceInput): Promise<CreatedVoiceResult> {
    this.assertConfigured();
    const form = new FormData();
    form.append('name', input.name);
    form.append('description', 'Authorized personal voice clone created via Vox AI Studio');
    form.append('files', fs.createReadStream(input.wavFilePath), {
      filename: 'voice_sample.wav',
      contentType: 'audio/wav',
    });

    const response = await axios.post(`${this.baseUrl}/voices/add`, form, {
      headers: {
        'xi-api-key': this.apiKey,
        ...form.getHeaders(),
      },
      timeout: 60000,
    });

    const voiceId = response.data?.voice_id;
    if (!voiceId) {
      throw new Error('External provider did not return a voice_id.');
    }

    return {
      providerVoiceId: voiceId,
      status: 'READY',
    };
  }

  async listVoices(): Promise<Array<{ id: string; name: string }>> {
    this.assertConfigured();
    const res = await axios.get(`${this.baseUrl}/voices`, {
      headers: { 'xi-api-key': this.apiKey },
      timeout: 15000,
    });
    const voices = res.data?.voices || [];
    return voices.map((v: any) => ({ id: v.voice_id, name: v.name }));
  }

  async deleteVoice(providerVoiceId: string): Promise<void> {
    this.assertConfigured();
    await axios.delete(`${this.baseUrl}/voices/${encodeURIComponent(providerVoiceId)}`, {
      headers: { 'xi-api-key': this.apiKey },
      timeout: 15000,
    });
  }

  async previewVoice(providerVoiceId: string): Promise<GenerateSpeechResult> {
    return this.generateSpeech({
      providerVoiceId,
      text: 'Hello! This is a verified voice clone preview generated inside Vox AI Studio.',
      language: 'en',
    });
  }

  async generateSpeech(input: GenerateSpeechInput): Promise<GenerateSpeechResult> {
    this.assertConfigured();
    if (!this.supportedLanguages.includes(input.language)) {
      throw new Error(`Language '${input.language}' is not supported by the configured external provider.`);
    }

    const res = await axios.post(
      `${this.baseUrl}/text-to-speech/${encodeURIComponent(input.providerVoiceId)}`,
      {
        text: input.text,
        model_id: 'eleven_multilingual_v2',
        voice_settings: {
          stability: 0.55,
          similarity_boost: 0.82,
        },
      },
      {
        headers: {
          'xi-api-key': this.apiKey,
          'Content-Type': 'application/json',
          Accept: 'audio/mpeg',
        },
        responseType: 'arraybuffer',
        timeout: 120000,
      }
    );

    return {
      audioBuffer: Buffer.from(res.data),
      format: 'mp3',
      supportsWav: true,
    };
  }
}
