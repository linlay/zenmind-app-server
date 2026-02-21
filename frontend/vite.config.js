import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';

function resolveBackendPort(cwd) {
  const candidates = [
    path.resolve(cwd, '../backend/application.yml'),
    path.resolve(cwd, 'backend/application.yml')
  ];

  for (const file of candidates) {
    if (!fs.existsSync(file)) continue;
    const content = fs.readFileSync(file, 'utf8');
    const match = content.match(/^\s*port:\s*(\d+)\s*$/m);
    if (match) {
      return Number(match[1]);
    }
  }

  return 8080;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const base = env.VITE_BASE_PATH || '/admin/';
  const port = Number(env.VITE_DEV_PORT || 5174);
  const strictPort = (env.VITE_DEV_STRICT_PORT || 'true').toLowerCase() !== 'false';
  const proxyPath = env.VITE_API_PROXY_PATH || '/admin/api';
  const backendPort = resolveBackendPort(process.cwd());
  const proxyTarget = env.VITE_API_PROXY_TARGET || `http://localhost:${backendPort}`;

  return {
    plugins: [react()],
    base,
    server: {
      port: Number.isNaN(port) ? 5174 : port,
      strictPort,
      proxy: {
        [proxyPath]: {
          target: proxyTarget,
          changeOrigin: true
        }
      }
    }
  };
});
