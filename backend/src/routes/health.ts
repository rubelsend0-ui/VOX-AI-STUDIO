import { Router } from 'express';
import { prisma } from '../services/prisma';
import { getActiveProvider } from '../providers/ProviderFactory';
import { ensureOwnerUser } from '../services/prisma';

const router = Router();

router.get('/', async (_req, res) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    const owner = await ensureOwnerUser();
    const provider = await getActiveProvider(owner.id);
    const aiHealth = await provider.checkHealth();

    res.json({
      success: true,
      status: 'ONLINE',
      timestamp: new Date().toISOString(),
      database: 'CONNECTED',
      aiEngine: aiHealth,
    });
  } catch (err: any) {
    res.status(503).json({
      success: false,
      status: 'DEGRADED',
      error: err?.message || 'Service check failed',
    });
  }
});

export default router;
