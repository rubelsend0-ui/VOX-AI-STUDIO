import { S3Client, PutObjectCommand, GetObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import fs from 'fs';
import path from 'path';
import { env } from '../config/env';

const LOCAL_STORAGE_DIR = '/tmp/vox-storage';

function createS3Client(): S3Client | null {
  try {
    const ep = (env.STORAGE_ENDPOINT || '').trim();
    if (!ep || !ep.startsWith('http') || ep.includes('example') || ep.includes('your-') || ep.includes('<')) {
      return null;
    }
    new URL(ep);
    return new S3Client({
      region: env.STORAGE_REGION || 'auto',
      endpoint: ep,
      forcePathStyle: true,
      credentials: {
        accessKeyId: env.STORAGE_ACCESS_KEY,
        secretAccessKey: env.STORAGE_SECRET_KEY,
      },
    });
  } catch {
    return null;
  }
}

const s3Client = createS3Client();

function ensureLocalPath(key: string): string {
  const fullPath = path.join(LOCAL_STORAGE_DIR, key);
  fs.mkdirSync(path.dirname(fullPath), { recursive: true });
  return fullPath;
}

export async function uploadBufferToStorage(key: string, buffer: Buffer, contentType: string): Promise<string> {
  if (s3Client) {
    try {
      await s3Client.send(
        new PutObjectCommand({
          Bucket: env.STORAGE_BUCKET,
          Key: key,
          Body: buffer,
          ContentType: contentType,
        })
      );
      return key;
    } catch {
      // Fallback to local disk
    }
  }
  fs.writeFileSync(ensureLocalPath(key), buffer);
  return key;
}

export async function uploadFileToStorage(key: string, filePath: string, contentType: string): Promise<string> {
  if (s3Client) {
    try {
      const fileStream = fs.createReadStream(filePath);
      await s3Client.send(
        new PutObjectCommand({
          Bucket: env.STORAGE_BUCKET,
          Key: key,
          Body: fileStream,
          ContentType: contentType,
        })
      );
      return key;
    } catch {
      // Fallback to local disk
    }
  }
  fs.copyFileSync(filePath, ensureLocalPath(key));
  return key;
}

export async function getPresignedDownloadUrl(key: string, expiresInSeconds = 3600): Promise<string> {
  if (s3Client) {
    try {
      const command = new GetObjectCommand({
        Bucket: env.STORAGE_BUCKET,
        Key: key,
      });
      return await getSignedUrl(s3Client, command, { expiresIn: expiresInSeconds });
    } catch {
      // Fallback to local disk
    }
  }
  const buf = fs.readFileSync(ensureLocalPath(key));
  const mime = key.endsWith('.mp3') ? 'audio/mpeg' : 'audio/wav';
  return `data:${mime};base64,${buf.toString('base64')}`;
}

export async function downloadStorageBuffer(key: string): Promise<Buffer> {
  if (s3Client) {
    try {
      const res = await s3Client.send(
        new GetObjectCommand({
          Bucket: env.STORAGE_BUCKET,
          Key: key,
        })
      );
      if (!res.Body) throw new Error(`Empty object body for storage key: ${key}`);
      const chunks: Uint8Array[] = [];
      for await (const chunk of res.Body as AsyncIterable<Uint8Array>) {
        chunks.push(chunk);
      }
      return Buffer.concat(chunks);
    } catch {
      // Fallback to local disk
    }
  }
  return fs.readFileSync(ensureLocalPath(key));
}

export async function deleteFromStorage(key: string): Promise<void> {
  if (s3Client) {
    try {
      await s3Client.send(
        new DeleteObjectCommand({
          Bucket: env.STORAGE_BUCKET,
          Key: key,
        })
      );
      return;
    } catch {
      // Fallback to local disk
    }
  }
  const fullPath = ensureLocalPath(key);
  if (fs.existsSync(fullPath)) fs.unlinkSync(fullPath);
}
