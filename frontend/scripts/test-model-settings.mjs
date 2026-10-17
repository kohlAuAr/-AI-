import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from './render.mjs';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const admin = { id: 3, name: '测试平台管理员', role: 'PLATFORM_ADMIN', managedClubIds: [] };
const student = { ...admin, id: 1, role: 'STUDENT' }, manager = { ...admin, id: 2, role: 'MANAGER' };
const choice = { protocol: 'OLLAMA', vendor: 'Ollama', baseUrl: 'http://127.0.0.1:11434', model: 'fixture-chat', keyConfigured: true };
const overview = { allowedBaseUrls: [choice.baseUrl], secretStorageReady: true, profiles: ['CHAT', 'EMBEDDING'].map(purpose => ({ purpose, revision: 1, saved: true, tested: true, draft: choice, active: choice, activatedAt: null, activatedBy: null })) };
let business, calls = [], delayed, rejected = false;
globalThis.fetch = async (url, options = {}) => {
  const path = String(url), method = options.method || 'GET';
  if (path === '/api/auth/session') return Response.json({ csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'test-csrf' });
  calls.push({ path, method, body: options.body });
  if (method !== 'GET') assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'test-csrf');
  if (delayed) { const wait = delayed; delayed = undefined; await wait; }
  if (rejected) return Response.json({ detail: '配置版本冲突，请重新读取' }, { status: 409 });
  if (path === '/api/platform/models') return Response.json(overview);
  if (path.startsWith('/api/platform/models/')) return Response.json({ ok: true });
  throw new Error('Unexpected request ' + path);
};
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
try {
  ({ business } = await server.ssrLoadModule('/src/prototype/business.ts'));
  const settings = await server.ssrLoadModule('/src/community/platformModels.ts');
  for (const account of [null, student, manager]) {
    business.user = account; await settings.loadModels();
    assert.equal(await settings.modelOperation('CHAT/activate', { revision: 1 }), undefined);
  }
  assert.equal(calls.length, 0, 'non-admin UI does not call platform model endpoints');
  business.user = admin; await settings.loadModels(); assert.equal(settings.modelAdmin.overview.profiles[0].active.model, 'fixture-chat');
  await settings.modelOperation('CHAT/save', { apiKey: 'synthetic-secret-not-a-real-key', revision: 1 });
  assert.equal(calls.at(-1).path, '/api/platform/models/CHAT/save');
  assert.equal(calls.at(-1).method, 'POST');
  assert(!calls.some(c => c.path.includes('synthetic-secret')), 'credentials never enter URL');
  let release; delayed = new Promise(resolve => release = resolve);
  const lateRead = settings.loadModels(); await new Promise(resolve => setTimeout(resolve, 0));
  business.user = student; assert.equal(settings.modelAdmin.overview, null, 'logout/role change clears immediately');
  release(); await lateRead; assert.equal(settings.modelAdmin.overview, null, 'late read cannot restore privileged settings');
  business.user = admin; await settings.loadModels();
  delayed = new Promise(resolve => release = resolve);
  const lateWrite = settings.modelOperation('CHAT/test', { revision: 1 }); await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(await settings.modelOperation('CHAT/activate', { revision: 1 }), undefined, 'busy guard prevents duplicate action');
  business.user = manager; release(); assert.equal(await lateWrite, undefined, 'late write result is not shown to another account');
  business.user = admin; rejected = true; assert.equal(await settings.modelOperation('CHAT/activate', { revision: 0 }), undefined);
  assert(settings.modelAdmin.error.includes('冲突')); rejected = false;
  await settings.loadModels();
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  await router.push('/platform/models'); await router.isReady();
  let html = await renderToString(createSSRApp(App).use(router));
  assert(html.includes('当前生效') && html.includes('待启用配置') && html.includes('检测服务并读取模型'));
  assert(html.includes('type="password"') && !html.includes('synthetic-secret-not-a-real-key'));
  assert(html.includes('云端 API') && html.includes('Embedding 模型'));
  business.user = student;
  html = await renderToString(createSSRApp(App).use(router));
  assert(html.includes('请使用平台管理员账号')); assert(!html.includes('model-key'));
  console.log('PASS model settings: admin-only calls, CSRF, busy guard, failed activation, synchronous clearing, late-response fencing and real Vue/Naive UI SSR. Not browser or provider quality acceptance.');
} finally { await server.close(); }
