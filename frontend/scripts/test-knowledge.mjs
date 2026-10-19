import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from './render.mjs';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
try {
  const { knowledgeFileError: validate } = await server.ssrLoadModule('/src/knowledgeFiles.ts');
  assert.equal(validate(), '');
  for (const [name, limit] of [['campus.PDF', 5 * 1024 * 1024], ['rules.md', 128 * 1024], ['rules.txt', 128 * 1024]]) {
    assert.equal(validate({ name, size: limit }), '');
    assert.match(validate({ name, size: limit + 1 }), /最大/);
    assert.match(validate({ name, size: 0 }), /为空/);
  }
  assert.match(validate({ name: 'renamed.pdf.exe', size: 10 }), /请选择/);
  const { business } = await server.ssrLoadModule('/src/prototype/business.ts');
  business.user = { id: 99, username: 'test_admin', name: '测试管理员', major: '', role: 'PLATFORM_ADMIN', managedClubIds: [] };
  const { default: View } = await server.ssrLoadModule('/src/views/KnowledgeView.vue');
  const app = createSSRApp(View);
  app.component('RouterLink', { template: '<a><slot /></a>' });
  const html = await renderToString(app);
  assert(html.includes('accept=".pdf,.md,.txt"'));
  assert(html.includes('5MB、50 页') && html.includes('Markdown/TXT（最大 128KB）'));
  assert(html.includes('knowledge-file-help') && html.includes('不支持纯扫描件和加密文件'));
  assert(html.includes('社团资料仅该社团成员可见'));
  assert(html.includes('正文会发送到服务器配置的 Embedding 服务'));
  console.log('PASS: file type/size/empty validation, PDF upload controls and accessible help, role scope and Embedding disclosure. Pure validation + SSR, not real PDF parsing or a browser.');
} finally { await server.close(); }
