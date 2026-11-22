import ffmpeg from 'fluent-ffmpeg';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { v4 as uuidv4 } from 'uuid';

export interface AudioMetadata {
  durationSeconds: number;
  format: string;
}

export function probeAudioFile(filePath: string): Promise<AudioMetadata> {
  return new Promise((resolve, reject) => {
    ffmpeg.ffprobe(filePath, (err, metadata) => {
      if (err) return reject(new Error(`Invalid or corrupted audio file: ${err.message}`));
      const duration = metadata.format.duration || 0;
      const format = metadata.format.format_name || 'unknown';
      resolve({ durationSeconds: duration, format });
    });
  });
}

export function normalizeToWav(inputPath: string): Promise<string> {
  const outputPath = path.join(os.tmpdir(), `norm_${uuidv4()}.wav`);
  return new Promise((resolve, reject) => {
    ffmpeg(inputPath)
      .audioFrequency(22050)
      .audioChannels(1)
      .audioCodec('pcm_s16le')
      .format('wav')
      .on('end', () => resolve(outputPath))
      .on('error', (err) => reject(new Error(`Audio normalization failed: ${err.message}`)))
      .save(outputPath);
  });
}

export function convertToMp3AndWav(inputAudioBuffer: Buffer, sourceExt = 'wav'): Promise<{ mp3Path: string; wavPath: string; duration: number }> {
  const id = uuidv4();
  const rawInput = path.join(os.tmpdir(), `raw_${id}.${sourceExt}`);
  const mp3Path = path.join(os.tmpdir(), `out_${id}.mp3`);
  const wavPath = path.join(os.tmpdir(), `out_${id}.wav`);

  fs.writeFileSync(rawInput, inputAudioBuffer);

  return new Promise((resolve, reject) => {
    ffmpeg(rawInput)
      .audioCodec('libmp3lame')
      .audioBitrate('192k')
      .format('mp3')
      .on('end', () => {
        ffmpeg(rawInput)
          .audioCodec('pcm_s16le')
          .audioFrequency(24000)
          .format('wav')
          .on('end', async () => {
            try {
              const meta = await probeAudioFile(wavPath);
              safeUnlink(rawInput);
              resolve({ mp3Path, wavPath, duration: meta.durationSeconds });
            } catch (e) {
              safeUnlink(rawInput);
              reject(e);
            }
          })
          .on('error', (err) => {
            safeUnlink(rawInput);
            reject(err);
          })
          .save(wavPath);
      })
      .on('error', (err) => {
        safeUnlink(rawInput);
        reject(err);
      })
      .save(mp3Path);
  });
}

export function concatWavBuffers(buffers: Buffer[]): Promise<Buffer> {
  if (buffers.length === 1) return Promise.resolve(buffers[0]);
  const batchId = uuidv4();
  const tempFiles: string[] = [];
  const listFile = path.join(os.tmpdir(), `concat_${batchId}.txt`);
  const outWav = path.join(os.tmpdir(), `merged_${batchId}.wav`);

  buffers.forEach((buf, idx) => {
    const p = path.join(os.tmpdir(), `part_${batchId}_${idx}.wav`);
    fs.writeFileSync(p, buf);
    tempFiles.push(p);
  });

  const listContent = tempFiles.map((f) => `file '${f.replace(/'/g, "'\\''")}'`).join('\n');
  fs.writeFileSync(listFile, listContent);

  return new Promise((resolve, reject) => {
    ffmpeg()
      .input(listFile)
      .inputOptions(['-f concat', '-safe 0'])
      .audioCodec('pcm_s16le')
      .audioFrequency(24000)
      .audioChannels(1)
      .on('end', () => {
        const merged = fs.readFileSync(outWav);
        tempFiles.forEach(safeUnlink);
        safeUnlink(listFile);
        safeUnlink(outWav);
        resolve(merged);
      })
      .on('error', (err) => {
        tempFiles.forEach(safeUnlink);
        safeUnlink(listFile);
        safeUnlink(outWav);
        reject(err);
      })
      .save(outWav);
  });
}

export function safeUnlink(filePath?: string | null) {
  if (!filePath) return;
  try {
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
  } catch {
    // ignore cleanup errors
  }
}
