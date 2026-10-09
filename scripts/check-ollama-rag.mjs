import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Installed native local models only. Retains public synthetic documents/conversations; no paid APIs.
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const mode = process.argv[2] || 'flow';
assert(['flow', 'restored', 'offline'].includes(mode));
const fixturePath = new URL('../.run/ollama-rag-fixture.json', import.meta.url);
const client = createSessionClient(base, 120000);
const headers = { 'Content-Type': 'application/json' };

if (mode === 'offline') {
  assert.equal((await client.request('/api/ai/status')).status, 503);
  assert.equal((await client.request('/api/ai/chat', { method: 'POST', headers, body: JSON.stringify({ question: '摄影社欢迎新手吗' }) })).status, 503);
  assert((await client.json('/api/clubs')).length > 0);
  assert.equal((await client.json('/api/system')).security, 'LOCAL_DEMO_ONLY');
  console.log('PASS: project AI offline; chat explicitly unavailable, campus queries still work.');
} else {
  const status = await client.json('/api/ai/status');
  assert.equal(status.mode, 'OLLAMA', 'This script only accepts native local Ollama chat.');
  assert.equal(status.embeddingProvider, 'OLLAMA');
  let fixture;
  if (mode === 'restored') {
    fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
    for (const doc of fixture.documents) {
      const detail = await client.json(`/api/ai/knowledge/${doc.id}`);
      assert.equal(detail.document.embeddingVersion, doc.embeddingVersion);
      assert.equal(detail.content, doc.content);
    }
    for (const conversation of fixture.conversations) {
      assert.deepEqual(await client.json(`/api/ai/conversations/${conversation.id}`), conversation.history);
    }
    console.log('PASS: indexed documents, generated answers and original references retained after restart; no inference requested.');
  } else {
    fixture = { documents: [], conversations: [] };
    const files = [
      new URL('../ai-service/src/main/resources/samples/programming-club.md', import.meta.url),
      new URL('../ai-service/src/main/resources/samples/activity-guide.md', import.meta.url),
      new URL('./fixtures/ollama-photo-guide.md', import.meta.url),
    ];
    for (const file of files) {
      const content = await readFile(file, 'utf8');
      const name = file.pathname.split('/').at(-1);
      const upload = () => {
        const body = new FormData();
        body.append('file', new Blob([content], { type: 'text/markdown' }), name);
        return client.json('/api/ai/knowledge', { method: 'POST', body });
      };
      const doc = await upload();
      assert(doc.embeddingVersion.includes('/OLLAMA/'));
      assert.equal((await upload()).id, doc.id, 'Same content/model version must deduplicate.');
      assert.equal((await client.json(`/api/ai/knowledge/${doc.id}`)).content, content);
      fixture.documents.push({ ...doc, content });
    }
    const ask = (question, conversationId = null) => client.json('/api/ai/chat', { method: 'POST', headers, body: JSON.stringify({ question, conversationId }) });
    const started = Date.now();
    const first = await ask('光影摄影社零基础同学可以参加吗？需要自己买相机吗？');
    assert.equal(first.mode, 'OLLAMA');
    assert.equal(first.retrieval, 'KEYWORD_VECTOR');
    assert(first.references.some(ref => ref.documentId === fixture.documents[2].id));
    assert.match(first.answer, /手机/);
    assert.match(first.answer, /\[\d+\]/);
    assert(!first.answer.includes('本地检索演示'));
    const followUp = await ask('光影摄影社手机拍摄入门具体学习什么？', first.conversationId);
    assert.match(followUp.answer, /构图/);
    const unknown = await ask('光影摄影社2028年的报名费具体是多少？');
    assert.match(unknown.answer, /未说明|没有|未提供|不确定|无法|未包含|未提及/);
    for (const reply of [first, followUp, unknown]) {
      for (const match of reply.answer.matchAll(/\[(\d+)\]/g)) {
        assert(Number(match[1]) >= 1 && Number(match[1]) <= reply.references.length, 'Generated reference numbers must map to retrieved sources.');
      }
    }
    for (const id of new Set([first.conversationId, unknown.conversationId])) {
      const history = await client.json(`/api/ai/conversations/${id}`);
      fixture.conversations.push({ id, history });
    }
    assert.equal(fixture.conversations[0].history.length, 2);
    assert.equal(fixture.conversations[0].history[0].answer, first.answer);
    assert.deepEqual(fixture.conversations[0].history[0].references, first.references);
    fixture.checkedAt = new Date().toISOString();
    fixture.elapsedMs = Date.now() - started;
    await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
    await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
    console.log(JSON.stringify({ result: 'PASS', mode: first.mode, retrieval: first.retrieval, documentIds: fixture.documents.map(doc => doc.id), elapsedMs: fixture.elapsedMs, answers: [first.answer, followUp.answer, unknown.answer] }, null, 2));
  }
}
