export const logger = {
  info: (msg: string, meta?: unknown) => {
    console.log(JSON.stringify({ level: 'INFO', time: new Date().toISOString(), msg, ...(meta ? { meta } : {}) }));
  },
  warn: (msg: string, meta?: unknown) => {
    console.warn(JSON.stringify({ level: 'WARN', time: new Date().toISOString(), msg, ...(meta ? { meta } : {}) }));
  },
  error: (msg: string, meta?: unknown) => {
    console.error(JSON.stringify({ level: 'ERROR', time: new Date().toISOString(), msg, ...(meta ? { meta } : {}) }));
  },
};
