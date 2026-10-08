import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const stage = process.argv[2];
assert(['down', 'restored'].includes(stage), 'Usage: node scripts/check-resilience.mjs down|restored');
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
if (stage === 'down') {
  assert.equal((await fetch(base + '/api/clubs')).status, 200);
  assert.equal((await fetch(base + '/api/activities')).status, 200);
  const ai = await fetch(base + '/api/ai/status');
  assert.equal(ai.status, 503);
  assert((await ai.json()).detail.includes('AI 服务暂不可用'));
  console.log('PASS: campus reads survive AI service downtime; AI calls return 503.');
} else {
  for (let attempt = 0; attempt < 40; attempt++) {
    try { if ((await fetch(base + '/api/ai/status', { signal: AbortSignal.timeout(2000) })).ok) break; }
    catch { /* Wait for startup only. */ }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  const previous = JSON.parse(await readFile(new URL('../.run/smoke-result.json', import.meta.url), 'utf8'));
  const historyResponse = await fetch(base + '/api/ai/conversations/' + previous.conversationId);
  assert.equal(historyResponse.status, 200);
  const history = await historyResponse.json();
  assert.equal(history.length, 1);
  assert(history[0].references.some(reference => reference.documentId === previous.knowledgeDocumentId));
  const documentResponse = await fetch(base + '/api/ai/knowledge/' + previous.knowledgeDocumentId);
  assert.equal(documentResponse.status, 200);
  assert((await documentResponse.json()).content.includes('星图社活动时间'));
  console.log('PASS: uploaded text, conversation and references survived a process restart.');
}
