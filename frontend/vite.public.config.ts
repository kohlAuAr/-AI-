import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue(), {
    name: 'public-prototype-entry',
    transformIndexHtml: { order: 'pre', handler: html => html
      .replace('/src/main.ts', '/src/public-main.ts')
      .replace('</head>', `<meta name="description" content="校园社团活动与招新交互原型，支持手机浏览，所有业务记录均为本地模拟。" /><link rel="icon" type="image/svg+xml" href="data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="10" fill="#e76750"/><path d="M9 9h14v4H13v3h10v7H9v-4h10v-3H9Z" fill="white"/></svg>')}" /></head>`) }
  }],
  define: { 'import.meta.env.VITE_PUBLIC_PROTOTYPE': JSON.stringify('true'), 'import.meta.env.VITE_BUSINESS_API': JSON.stringify('false') },
  build: { outDir: 'dist-public', sourcemap: false }
});
