import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir, access } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Uses only native local Ollama; retains separate fictional PDF documents/account/history.
const stage = process.argv[2] || 'flow'; assert(['flow', 'restored'].includes(stage));
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const admin = createSessionClient(base, 180000), manager = createSessionClient(base, 180000), reader = createSessionClient(base, 180000);
const proofPath = new URL('../.run/pdf-knowledge-proof.json', import.meta.url);
const login = (client, username) => client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) });
await login(admin, 'platform_admin'); await login(manager, 'photo_manager');
if (stage === 'restored') {
  const proof = JSON.parse(await readFile(proofPath, 'utf8'));
  await login(reader, proof.username);
  for (const [client, doc] of [[admin, proof.publicDoc], [manager, proof.clubDoc]]) {
    assert.deepEqual(await client.json(`/api/ai/knowledge/${doc.document.id}`), doc);
  }
  await reader.json(`/api/ai/knowledge/${proof.clubDoc.document.id}`, {}, 404);
  assert.deepEqual(await reader.json(`/api/ai/conversations/${proof.conversationId}`), proof.history);
  console.log('PASS: same public/private PDF text, indexes, account-owned answer/references and club authorization retained; no inference requested.');
} else {
  let exists = false; try { await access(proofPath); exists = true; } catch {}
  assert(!exists, 'A retained PDF fixture exists; use restored, do not replace it.');
  const status = await admin.json('/api/ai/status');
  assert.equal(status.mode, 'OLLAMA', 'No paid cloud/model settings changes are permitted by this check.');
  assert.equal(status.embeddingProvider, 'OLLAMA');
  const folder = process.argv[3]; assert(folder, 'Usage: node scripts/check-pdf-knowledge.mjs flow <synthetic-pdf-folder>');
  async function upload(client, name, bytes, visibility = 'PUBLIC', clubId, expected = 200) {
    const form = new FormData(); form.append('file', new Blob([bytes], { type: 'application/pdf' }), name); form.append('visibility', visibility);
    if (clubId) form.append('clubId', String(clubId));
    return client.json('/api/ai/knowledge', { method: 'POST', body: form }, expected);
  }
  const publicBytes = await readFile(`${folder}/campus-guide.pdf`), privateBytes = await readFile(`${folder}/club-guide.pdf`);
  for (const [name, bytes, expected] of [
    ['image-only.pdf', await readFile(`${folder}/image-only.pdf`), 400],
    ['too-many-pages.pdf', await readFile(`${folder}/too-many-pages.pdf`), 400],
    ['forged.pdf', Buffer.from('not a PDF'), 400],
    ['large.pdf', Buffer.alloc(5 * 1024 * 1024 + 1), 413],
    ['large.md', Buffer.alloc(128 * 1024 + 1), 413],
  ]) await upload(admin, name, bytes, 'PUBLIC', undefined, expected);
  const username = `pdf_reader_${Date.now().toString(36)}`;
  await reader.json('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: 'CampusDemo123!', name: 'PDF验证同学（虚构）', major: '虚构测试专业' }) });
  await login(reader, username);
  const publicRow = await upload(admin, '青禾公开资料（虚构PDF验证）.pdf', publicBytes);
  assert.equal((await upload(admin, '青禾公开资料（虚构PDF验证）.pdf', publicBytes)).id, publicRow.id);
  const publicDoc = await reader.json(`/api/ai/knowledge/${publicRow.id}`); assert(publicDoc.content.includes('图书馆南门'));
  const club = (await manager.json('/api/manage/clubs')).find(c => c.slug === 'photo'); assert(club);
  const privateRow = await upload(manager, '青禾社团资料（虚构PDF验证）.pdf', privateBytes, 'CLUB', club.id);
  const clubDoc = await manager.json(`/api/ai/knowledge/${privateRow.id}`); assert(clubDoc.content.includes('琥珀暗房'));
  await reader.json(`/api/ai/knowledge/${privateRow.id}`, {}, 404); await admin.json(`/api/ai/knowledge/${privateRow.id}`, {}, 404);
  await upload(reader, '学生禁止上传.pdf', publicBytes, 'PUBLIC', undefined, 403);
  const reply = await reader.json('/api/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: '根据银杉摄影训练营报名说明，集合地点在哪里？' }) });
  assert(reply.references.some(r => r.documentId === publicRow.id && r.documentName.endsWith('.pdf')));
  assert(!reply.references.some(r => r.documentId === privateRow.id)); assert(reply.answer.includes('图书馆南门'));
  const history = await reader.json(`/api/ai/conversations/${reply.conversationId}`);
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(proofPath, JSON.stringify({ username, publicDoc: await admin.json(`/api/ai/knowledge/${publicRow.id}`), clubDoc, conversationId: reply.conversationId, history }, null, 2));
  console.log(`PASS: real Chinese PDF extraction/indexing, deduplication, native Ollama answer with PDF reference, public/club fences, scan/page/format/size rejection; retained docs ${publicRow.id}/${privateRow.id}.`);
}
