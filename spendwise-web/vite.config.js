import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 5173 is the gateway's spa-base-url and its only CORS origin
// (config-repo/api-gateway.yml), so the dev server must not drift to 5174.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 5173,
    strictPort: true,
  },
});
