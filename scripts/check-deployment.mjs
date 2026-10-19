import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Isolated production-profile stack, LOCAL only. Set NODE_EXTRA_CA_CERTS to its test certificate.
const base = process.env.DEPLOY_TEST_URL || 'https://localhost:8448';
const target = new URL(base);
assert(target.protocol === 'https:' && ['localhost', '127.0.0.1'].includes(target.hostname), 'This acceptance script is localhost-only.');
const admin = createSessionClient(base), student = createSessionClient(base), anonymous = createSessionClient(base);
const proofPath = new URL('../.run/deployment-proof.json', import.meta.url);
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored', 'ai-offline', 'redis-offline', 'quota-restored'].includes(stage));
if (stage === 'flow') assert.equal(process.env.DEPLOY_TEST_FIXTURES, '1', 'Explicitly opt in to retained fictional fixtures on an isolated test stack.');
const password = (await readFile(new URL('../deploy/secrets/admin_password', import.meta.url), 'utf8')).trim();
await admin.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: 'campus_admin', password }) });
const session = await admin.json('/api/auth/session'); assert.equal(session.user.role, 'PLATFORM_ADMIN'); assert.equal(session.demoAccounts, false);
assert((await anonymous.request('/api/auth/session')).headers.getSetCookie().some(cookie => /Secure/i.test(cookie)));
assert.equal((await anonymous.request('/clubs')).status, 200); assert.equal((await anonymous.request('/internal/knowledge')).status, 404);
await anonymous.json('/api/platform/banners', {}, 401);
if (stage !== 'ai-offline') assert.equal((await admin.json('/api/ai/status')).mode, 'LOCAL', 'No real or paid model calls in deployment acceptance.');
if (stage === 'ai-offline') {
  await admin.json('/api/ai/status', {}, 503);
  assert.equal((await admin.request('/api/clubs')).status, 200);
  console.log('PASS: isolated AI stopped; campus queries remain available.');
} else if (stage === 'redis-offline') {
  await admin.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: 'quota fixture' }) }, 503);
  assert.equal((await admin.request('/api/clubs')).status, 200);
  await admin.json('/api/ai/conversations');
  console.log('PASS: Redis stopped; new AI work fails closed, campus and authorized history remain readable.');
} else if (stage === 'quota-restored') {
  await admin.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: 'quota fixture' }) }, 429);
  console.log('PASS: daily quota survives isolated Redis restart via its persistent AOF.');
} else if (stage === 'restored') {
  const proof = JSON.parse(await readFile(proofPath, 'utf8'));
  assert.equal((await admin.json(`/api/ai/knowledge/${proof.doc.id}`)).content, proof.content);
  console.log('PASS: TLS/session/admin/no-demo, retained MySQL document after restart, no model inference.');
} else {
  await admin.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }, 400); // Admitted invalid attempts consume quota.
  const status = await admin.json('/api/ai/status'); assert.equal(status.mode, 'LOCAL');
  const content = '部署验证资料（虚构）：银杉摄影交流在图书馆南门集合。\n'.repeat(80);
  const form = new FormData(); form.append('file', new Blob([content], { type: 'text/plain' }), '部署隔离验证.txt');
  const doc = await admin.json('/api/ai/knowledge', { method: 'POST', body: form });
  assert.equal((await admin.json(`/api/ai/knowledge/${doc.id}`)).content, content);
  await admin.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '银杉摄影交流集合地点' }) }, 429);
  const username = `deploy_student_${Date.now().toString(36)}`;
  await student.json('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: 'IsolatedDeploy123!', name: '部署验证同学（虚构）', major: '测试专业' }) });
  await student.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'IsolatedDeploy123!' }) });
  const reply = await student.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '银杉摄影交流集合地点' }) });
  assert.equal(reply.mode, 'LOCAL'); assert(reply.references.some(row => row.documentId === doc.id));
  const rejected = await student.request('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '银杉摄影交流集合地点' }) });
  assert.equal(rejected.status, 429); assert(Number(rejected.headers.get('retry-after')) > 0);
  assert((await student.json('/api/clubs')).length === 0, 'Fresh production does not seed clubs');
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(proofPath, JSON.stringify({ doc, content, username, conversationId: reply.conversationId }, null, 2));
  console.log('PASS: actual TLS/SPA/API, secure Session, no demo seeds, MySQL long text + LOCAL citations, per-user/global daily quotas, public business remains available.');
}
