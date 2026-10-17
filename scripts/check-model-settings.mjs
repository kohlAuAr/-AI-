import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Only native Ollama on this developer machine. No keys, downloads or paid provider requests.
const stage = process.argv[2] || 'restored'; assert(['flow', 'restored', 'offline'].includes(stage));
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const admin = createSessionClient(base, 180000), student = createSessionClient(base, 180000), manager = createSessionClient(base), anonymous = createSessionClient(base);
const proofPath = new URL('../.run/model-settings-proof.json', import.meta.url);
const post = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
async function login(client, username) { await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) }); }
await login(admin, 'platform_admin'); await login(student, 'student'); await login(manager, 'photo_manager');
await anonymous.json('/api/platform/models', {}, 401);
for (const client of [student, manager]) await client.json('/api/platform/models', {}, 403);
if (stage === 'offline') {
  await admin.json('/api/platform/models', {}, 503);
  assert((await student.json('/api/clubs')).length > 0);
  console.log('PASS offline: model settings unavailable while campus queries and role boundaries remain usable.');
} else if (stage === 'restored') {
  const proof = JSON.parse(await readFile(proofPath, 'utf8'));
  const current = await admin.json('/api/platform/models');
  for (const expected of proof.profiles) {
    const actual = current.profiles.find(p => p.purpose === expected.purpose);
    assert.deepEqual(actual.active, expected.active); assert.equal(actual.activatedAt, expected.activatedAt); assert.equal(actual.activatedBy, expected.activatedBy);
  }
  const status = await student.json('/api/ai/status'); assert.equal(status.mode, 'OLLAMA'); assert.equal(status.embeddingProvider, 'OLLAMA');
  console.log('PASS restored: same active chat/embedding profiles and activation audit survived AI restart; no inference or business writes.');
} else {
  const before = await admin.json('/api/platform/models');
  for (const profile of before.profiles) {
    assert.equal(profile.active.protocol, 'OLLAMA', 'flow preserves only existing native local configuration');
    assert.equal(profile.active.baseUrl, 'http://127.0.0.1:11434');
    assert.equal(profile.active.keyConfigured, false, 'never copy or request credentials');
    const discovered = await admin.json('/api/platform/models/discover', post({ baseUrl: profile.active.baseUrl }));
    assert(discovered.models.includes(profile.active.model));
    const saved = await admin.json(`/api/platform/models/${profile.purpose}/save`, post({ ...profile.active, vendor: 'Ollama', apiKey: '', revision: profile.revision }));
    assert.deepEqual(saved.active, profile.active, 'save does not activate');
    await admin.json(`/api/platform/models/${profile.purpose}/test`, post({ revision: saved.revision }));
    const activated = await admin.json(`/api/platform/models/${profile.purpose}/activate`, post({ revision: saved.revision, acknowledgeReindex: true }));
    assert.equal(activated.active.model, profile.active.model);
    assert.equal(activated.active.baseUrl, profile.active.baseUrl);
  }
  const reply = await student.json('/api/ai/chat', post({ question: '程序设计社适合编程新手吗？请根据可见资料回答。' }));
  assert.equal(reply.mode, 'OLLAMA'); assert(reply.answer.length > 0);
  const after = await admin.json('/api/platform/models');
  await writeFile(proofPath, JSON.stringify({ profiles: after.profiles.map(({ purpose, active, activatedAt, activatedBy }) => ({ purpose, active, activatedAt, activatedBy })), conversationId: reply.conversationId }, null, 2));
  console.log('PASS flow: detected installed Ollama models; saved/tested/activated the SAME original chat+embedding models; real student RAG reply returned. Retained only model settings/audit and one synthetic conversation; no cloud request or existing campus record edits.');
}
