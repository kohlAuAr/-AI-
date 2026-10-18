import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Synthetic fixtures only. Empty questions validate the gateway without model inference or fees.
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored', 'offline'].includes(stage));
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const student = createSessionClient(base), manager = createSessionClient(base), publicClient = createSessionClient(base);
const proofPath = new URL('../.run/redis-proof.json', import.meta.url);
const body = (method, data) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
const login = (client, username) => client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) });
if (stage === 'flow') {
  const username = `redis_${Date.now().toString(36)}`;
  const account = await student.json('/api/auth/register', body('POST', { username, password: 'CampusDemo123!', name: 'Redis验收学生（虚构）', major: '软件工程' }));
  await login(student, username); await login(manager, 'code_manager');
  const input = { name: `Redis验收社（虚构）${username}`, category: '科技', description: '独立缓存验收资料，不是真实社团。', tags: '测试', campus: '测试校区', recruiting: true, requirements: '仅测试', schedule: '周末', place: '测试地点' };
  const club = await manager.json('/api/manage/clubs', body('POST', input));
  for (let i = 0; i < 2; i++) assert.equal((await publicClient.json(`/api/clubs/${club.id}`)).description, input.description);
  const updated = { ...input, description: '缓存失效后的新介绍（虚构）。', recruiting: false };
  await manager.json(`/api/manage/clubs/${club.id}`, body('PUT', updated));
  const fresh = await publicClient.json(`/api/clubs/${club.id}`);
  assert.equal(fresh.description, updated.description); assert.equal(fresh.recruiting, false);
  // All five operations below are rejected by AI input validation, before inference.
  for (let i = 0; i < 5; i++) await student.json(`/api/ai/${i % 2 ? 'assistant' : 'chat'}`, body('POST', { question: '' }), 400);
  const blocked = await student.request('/api/ai/chat', body('POST', { question: '' }));
  assert.equal(blocked.status, 429); assert(Number(blocked.headers.get('Retry-After')) > 0);
  await student.json('/api/profile'); await publicClient.json('/api/activities');
  await student.json('/api/ai/conversations');
  await writeFile(proofPath, JSON.stringify({ username, accountId: account.id, clubId: club.id, description: updated.description }, null, 2));
  console.log('PASS real HTTP: cache update visible; shared account limit returns 429 + Retry-After; business/history reads remain usable. Retained one synthetic account and club; no model inference.');
} else {
  const proof = JSON.parse(await readFile(proofPath, 'utf8')); await login(student, proof.username);
  const club = await publicClient.json(`/api/clubs/${proof.clubId}`); assert.equal(club.description, proof.description);
  await publicClient.json('/api/activities'); await student.json('/api/profile'); await student.json('/api/ai/conversations');
  if (stage === 'offline') {
    const error = await student.json('/api/ai/chat', body('POST', { question: '' }), 503);
    assert.match(error.detail, /访问控制/);
    const profile = await student.json('/api/profile');
    await student.json('/api/profile', body('PUT', { name: profile.name, major: profile.major, interests: profile.interests, availableTime: profile.availableTime, interestDescription: profile.interestDescription }));
    console.log('PASS Redis-offline real HTTP: public data and profile save available; new AI calls fail closed; conversation history still available.');
  } else console.log('PASS restored: the same synthetic club/account data remains available after restart.');
}
