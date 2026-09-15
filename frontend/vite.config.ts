import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.LOCALRAG_BACKEND_URL ?? 'http://localhost:18080', changeOrigin: true },
      '/actuator': { target: process.env.LOCALRAG_BACKEND_URL ?? 'http://localhost:18080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
})
