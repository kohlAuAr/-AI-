import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createSessionClient } from './http-session.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const client = createSessionClient(base);
const json = client.json;
async function ready() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const response = await fetch(base + '/api/ai/status', { signal: AbortSignal.timeout(2000) });
      if (response.ok) return;
    } catch { /* Starting services may not be listening yet. */ }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Services did not become ready. Read .run/*-error.log and .run/*.log.');
}
await ready();
const system = await json('/api/system');
assert.equal(system.security, 'LOCAL_DEMO_ONLY');
assert(system.modules.some(module => module.key === 'recruitment' && module.status === 'READY'));
const clubs = await json('/api/clubs');
assert(clubs.length >= 3 && clubs.every(club => club.demo));
assert.equal((await json(`/api/clubs/${clubs[0].id}`)).id, clubs[0].id);
const activities = await json('/api/activities');
assert(activities.length >= 2 && activities.every(activity => activity.status === 'SAMPLE'));
const status = await json('/api/ai/status');
assert.equal(status.mode, 'LOCAL', 'This smoke test is for local mode; it must not call paid model APIs.');
const fixture = await readFile(new URL('./fixtures/smoke-club.md', import.meta.url));
const upload = async () => {
  const body = new FormData();
  body.append('file', new Blob([fixture], { type: 'text/markdown' }), 'smoke-club.md');
  return json('/api/ai/knowledge', { method: 'POST', body });
};
const doc = await upload();
assert.equal((await upload()).id, doc.id, 'Identical uploads must not create duplicate documents.');
assert((await json(`/api/ai/knowledge/${doc.id}`)).content.includes('星图社活动时间'));
const reply = await json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '星图社活动时间' }) });
assert.equal(reply.mode, 'LOCAL');
assert.equal(reply.retrieval, 'KEYWORD');
assert(reply.references.some(reference => reference.documentId === doc.id));
assert(reply.answer.includes('周六下午'));
const history = await json(`/api/ai/conversations/${reply.conversationId}`);
assert.equal(history.length, 1);
assert.equal(history[0].references[0].documentId, reply.references[0].documentId);
const invalid = await client.request('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '' }) });
assert.equal(invalid.status, 400);
const missing = await fetch(base + '/api/clubs/999999');
assert.equal(missing.status, 404);
const result = { checkedAt: new Date().toISOString(), base, clubs: clubs.length, activities: activities.length, knowledgeDocumentId: doc.id, conversationId: reply.conversationId, mode: reply.mode, result: 'PASS' };
await mkdir(path.join(root, '.run'), { recursive: true });
await writeFile(path.join(root, '.run', 'smoke-result.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));
