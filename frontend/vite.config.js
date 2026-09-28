import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api is proxied to the Spring Boot backend.
// In the container, nginx does the same proxying (see nginx/default.conf.template).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_DEV_BACKEND || 'http://localhost:8080', changeOrigin: true },
    },
  },
});
