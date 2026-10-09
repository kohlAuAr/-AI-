import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Real local-model smoke, not a recommendation benchmark. Only edits a separate synthetic account.
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const mode = process.argv[2] || 'flow';
assert(['flow', 'restored', 'offline'].includes(mode));
const fixturePath = new URL('../.run/ollama-recommendation-fixture.json', import.meta.url);
const client = createSessionClient(base, 120000);
const headers = { 'Content-Type': 'application/json' };
const cases = [
  { interest: '喜欢用手机记录校园生活，想学拍照和剪视频，零基础', slug: 'photo' },
  { interest: '对算法和写代码感兴趣，希望有人一起刷编程题和做小项目', slug: 'code' },
  { interest: '喜欢在山野里走路探索自然，周末想和大家一起去户外徒步', slug: 'hike' },
  { interest: '喜欢编程和摄影', slugs: ['code', 'photo'] },
];
if (mode !== 'offline') {
  const status = await client.json('/api/ai/status');
  assert.equal(status.mode, 'LOCAL', 'Keep chat in local keyword mode for this check.');
  assert.equal(status.embeddingProvider, 'OLLAMA');
  assert.equal(status.recommendation, 'SEMANTIC_CONFIGURED_NOT_HEALTH_CHECKED');
} else {
  assert.equal((await client.request('/api/ai/status')).status, 503, 'Stop only the project AI service before offline checks.');
}
let fixture;
if (mode === 'flow') {
  fixture = { username: `ollama_${Date.now().toString(36)}`, password: 'CampusDemo123!', name: '本地语义验收学生', major: '测试专业', interests: [], availableTime: '周末', interestDescription: '' };
  await client.json('/api/auth/register', { method: 'POST', headers, body: JSON.stringify(fixture) });
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
} else fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: fixture.username, password: fixture.password }) });
const clubs = await client.json('/api/clubs');
if (mode === 'offline') {
  await client.json('/api/profile', { method: 'PUT', headers, body: JSON.stringify(fixture) });
  assert.equal((await client.json('/api/profile')).interestDescription, fixture.interestDescription);
  const response = await client.request('/api/ai/recommendations', { method: 'POST' });
  assert.equal(response.status, 503);
  assert.match((await response.json()).detail, /AI 服务暂不可用/);
  assert(clubs.length > 0);
  console.log('PASS offline: AI stopped; profile saved/read, clubs queried, recommendation explicitly unavailable.');
} else {
  const report = [];
  for (const sample of mode === 'flow' ? cases : [cases[3]]) {
    const expected = sample.slugs || [sample.slug];
    for (const slug of expected) assert(clubs.some(club => club.slug === slug && club.recruiting), `Expected demo club ${slug} must be recruiting.`);
    if (mode === 'flow') {
      fixture.interestDescription = sample.interest;
      await client.json('/api/profile', { method: 'PUT', headers, body: JSON.stringify(fixture) });
      await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
    } else assert.equal((await client.json('/api/profile')).interestDescription, fixture.interestDescription);
    const started = performance.now();
    const result = await client.json('/api/ai/recommendations', { method: 'POST' });
    assert.equal(result.method, 'HYBRID_BM25_VECTOR_RRF');
    assert(result.items.length > 0 && result.items.length <= 3);
    assert(result.items.every(item => Number.isFinite(item.score) && item.score >= -1 && item.score <= 1 && Number.isFinite(item.bm25Score) && item.bm25Score >= 0 && item.fusionScore > 0));
    assert(result.items.every(item => !item.name.startsWith('校园后端联调社（虚构）backend_')), 'Retained verification clubs must not be recommendation candidates.');
    if (sample.slugs) {
      for (const slug of sample.slugs) assert(result.items.some(item => item.slug === slug), `Multi-interest smoke must include ${slug}.`);
    } else assert.equal(result.items[0].slug, sample.slug, 'Measured top result must match this narrow smoke case.');
    const repeated = await client.json('/api/ai/recommendations', { method: 'POST' });
    assert.deepEqual(repeated, result, 'Repeated recommendation must remain consistent.');
    report.push({ interest: sample.interest, elapsedMs: Math.round(performance.now() - started), results: result.items.map(({ name, slug, score, bm25Score, fusionScore }) => ({ name, slug, score, bm25Score, fusionScore })) });
  }
  await writeFile(new URL(`../.run/ollama-recommendation-${mode}.json`, import.meta.url), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  console.log(`PASS ${mode}: real Ollama + BM25/RRF via authenticated campus/Vite HTTP; fixture isolation and multi-interest coverage on these smoke cases, not an accuracy guarantee. Cache-call reuse is separately tested with protocol fixtures.`);
}
