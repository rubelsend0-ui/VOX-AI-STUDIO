export interface CreateVoiceInput {
  name: string;
  wavFilePath: string;
  userId: string;
}

export interface CreatedVoiceResult {
  providerVoiceId: string;
  status: 'READY' | 'PROCESSING';
}

export interface GenerateSpeechInput {
  providerVoiceId: string;
  text: string;
  language: string;
  sampleWavBuffer?: Buffer;
}

export interface GenerateSpeechResult {
  audioBuffer: Buffer;
  format: 'wav' | 'mp3';
  supportsWav: boolean;
}

export interface ProviderHealth {
  configured: boolean;
  reachable: boolean;
  providerName: string;
  supportedLanguages: string[];
  message: string;
}

export interface VoiceAIProvider {
  readonly name: string;
  readonly supportedLanguages: string[];
  checkHealth(): Promise<ProviderHealth>;
  createVoice(input: CreateVoiceInput): Promise<CreatedVoiceResult>;
  listVoices(): Promise<Array<{ id: string; name: string }>>;
  deleteVoice(providerVoiceId: string): Promise<void>;
  previewVoice(providerVoiceId: string, sampleWavBuffer?: Buffer): Promise<GenerateSpeechResult>;
  generateSpeech(input: GenerateSpeechInput): Promise<GenerateSpeechResult>;
}
