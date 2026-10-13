/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = process.env.MOCKHUB_BACKEND ?? 'http://localhost:8080';

export default defineConfig({
  base: '/__admin/mockhub/ui/',
  plugins: [react()],
  server: {
    proxy: {
      '^/__admin/(?!mockhub/ui).*': { target: backend, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
