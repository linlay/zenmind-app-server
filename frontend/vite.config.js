import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';

function loadRootEnv(mode, cwd) {
  const rootDir = path.resolve(cwd, '..');
  return loadEnv(mode, rootDir, '');
}

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

function resolveProxyTarget(env, cwd) {
  const backendPortFromEnv = Number(env.BACKEND_PORT);
  const backendPort = Number.isNaN(backendPortFromEnv) ? resolveBackendPort(cwd) : backendPortFromEnv;
  const localFallback = `http://localhost:${backendPort}`;
  const configured = env.VITE_API_PROXY_TARGET;
  if (!configured) {
    return localFallback;
  }

  const isBackendHost = /:\/\/backend(?::|\/|$)/.test(configured);
  const runningInContainer = fs.existsSync('/.dockerenv');
  if (isBackendHost && !runningInContainer) {
    return localFallback;
  }

  return configured;
}

export default defineConfig(({ mode }) => {
  const env = loadRootEnv(mode, process.cwd());
  const base = env.VITE_BASE_PATH || '/admin/';
  const port = Number(env.VITE_DEV_PORT || env.FRONTEND_PORT || 11950);
  const strictPort = (env.VITE_DEV_STRICT_PORT || 'true').toLowerCase() !== 'false';
  const proxyPath = env.VITE_API_PROXY_PATH || '/admin/api';
  const proxyTarget = resolveProxyTarget(env, process.cwd());

  return {
    plugins: [react()],
    base,
    server: {
      port: Number.isNaN(port) ? 11950 : port,
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
