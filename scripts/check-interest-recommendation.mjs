import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// No paid model calls; only a separate retained synthetic account is edited.
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const fixturePath = new URL('../.run/interest-recommendation-fixture.json', import.meta.url);
const mode = process.argv[2] || 'flow';
assert(['flow', 'restored', 'offline'].includes(mode));
const client = createSessionClient(base);
const headers = { 'Content-Type': 'application/json' };
const send = (path, body) => client.request(path, { method: 'POST', headers, ...(body ? { body: JSON.stringify(body) } : {}) });
assert.equal((await send('/api/ai/recommendations')).status, 401);
let fixture;
if (mode === 'flow') {
  assert.equal((await client.json('/api/ai/status')).mode, 'LOCAL', 'This check must not call an external model.');
  fixture = { username: `interest_${Date.now().toString(36)}`, password: 'CampusDemo123!', name: '兴趣描述验收学生', major: '数字媒体', interestDescription: '喜欢用手机记录校园生活，想学拍照和剪视频，零基础', interests: ['摄影'], availableTime: '周三晚上' };
  await client.json('/api/auth/register', { method: 'POST', headers, body: JSON.stringify(fixture) });
} else fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: fixture.username, password: fixture.password }) });
if (mode === 'flow') {
  assert.equal((await send('/api/ai/recommendations')).status, 400, 'Tags are not a substitute for missing free-text interest.');
}
if (mode !== 'restored') {
  await client.json('/api/profile', { method: 'PUT', headers, body: JSON.stringify(fixture) });
  const invalid = await client.request('/api/profile', { method: 'PUT', headers, body: JSON.stringify({ ...fixture, interestDescription: '字'.repeat(1001) }) });
  assert.equal(invalid.status, 400);
}
assert.equal((await client.json('/api/profile')).interestDescription, fixture.interestDescription);
const recommendation = await send('/api/ai/recommendations');
assert.equal(recommendation.status, 503);
const error = await recommendation.json();
assert.match(error.detail, mode === 'offline' ? /AI 服务暂不可用/ : /尚未配置 Embedding 模型/);
assert((await client.json('/api/clubs')).length > 0);
await client.json('/api/auth/logout', { method: 'POST' });
assert.equal((await client.request('/api/profile')).status, 401);
await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: fixture.username, password: fixture.password }) });
assert.equal((await client.json('/api/profile')).interestDescription, fixture.interestDescription);
if (mode === 'flow') {
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
}
console.log(`PASS ${mode}: free-text persistence, current-account access, validation, recommendation unavailable without fake fallback, and independent campus queries. No actual Embedding quality claim.`);
