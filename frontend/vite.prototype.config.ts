import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// LAN preview serves only this frontend. Do not expose the business API proxy.
export default defineConfig({
  plugins: [vue()],
  define: { 'import.meta.env.VITE_BUSINESS_API': JSON.stringify('false') },
  server: { host: '0.0.0.0', port: 5178, strictPort: true },
  preview: { host: '0.0.0.0', port: 5178, strictPort: true }
});
