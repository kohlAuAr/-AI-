import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

// Retained, isolated fictional account and activity. Never edits old acceptance fixtures.
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const mode = process.argv[2] || 'flow'; assert(['flow', 'restored', 'offline'].includes(mode));
const fixtureFile = new URL('../.run/completion-fixture.json', import.meta.url);
const admin = createSessionClient(base, 360000), manager = createSessionClient(base, 360000), student = createSessionClient(base, 360000), anonymous = createSessionClient(base);
const password = 'CompletionFixture123!';
const login = (client, username, pass = 'CampusDemo123!') => client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: pass }) });
const send = (client, path, body, method = 'POST', expected = 200) => client.json(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, expected);
const upload = (client, name, content, clubId) => { const body = new FormData(); body.append('file', new Blob([content], { type: 'text/markdown' }), name); if (clubId) { body.append('visibility', 'CLUB'); body.append('clubId', clubId); } return client.json('/api/ai/knowledge', { method: 'POST', body }); };
for (let attempt = 0; ; attempt++) {
  let ready = false;
  try { ready = (await fetch(base + (mode === 'offline' ? '/api/system' : '/api/ai/status'), { signal: AbortSignal.timeout(2000) })).ok; } catch { /* Services may still be starting. */ }
  if (ready) break;
  assert(attempt < 30, 'Services not ready; inspect .run logs.');
  await new Promise(resolve => setTimeout(resolve, 1000));
}
let fixture;
if (mode !== 'flow') { fixture = JSON.parse(await readFile(fixtureFile, 'utf8')); await login(student, fixture.username, password); await login(manager, 'photo_manager'); }
if (mode === 'offline') {
  assert.equal((await student.request('/api/ai/status')).status, 503);
  assert.equal((await send(student, '/api/ai/assistant', { question: '我的报名' }, 'POST', 503)).status, 503);
  const profile = await student.json('/api/profile');
  await send(student, '/api/profile', { name: profile.name, major: profile.major, interests: profile.interests, interestDescription: profile.interestDescription, availableTime: profile.availableTime }, 'PUT');
  assert((await student.json('/api/clubs')).length > 0);
  assert((await manager.json(`/api/manage/clubs/${fixture.clubId}/activities`)).some(a => a.id === fixture.activityId));
  assert((await student.json('/api/favorites')).some(row => row.clubId === fixture.clubId));
  console.log('PASS completion offline: profile write, favorites and manager activity reads work with project AI stopped.');
} else if (mode === 'restored') {
  for (const row of fixture.conversations) assert.deepEqual(await student.json(`/api/ai/conversations/${row.id}`), row.history);
  assert.equal((await student.json(`/api/ai/knowledge/${fixture.documentId}`)).content, fixture.content);
  const restored = await manager.json(`/api/manage/clubs/${fixture.clubId}/activities`);
  assert.equal(restored.find(a => a.id === fixture.activityId).status, 'CANCELLED');
  assert.equal(restored.find(a => a.id === fixture.activityId).cancelReason, '整体验收虚构活动已结束验证');
  assert((await student.json('/api/favorites')).some(row => row.clubId === fixture.clubId));
  assert.equal((await anonymous.request(`/api/ai/conversations/${fixture.conversations[0].id}`)).status, 401);
  console.log('PASS completion restored: scoped documents, own conversations/tool observations, favorites, draft/cancellation audit survive restart. No new inference.');
} else {
  const status = await anonymous.json('/api/ai/status'); assert.equal(status.mode, 'OLLAMA', 'Use installed local models only.');
  await login(admin, 'platform_admin'); await login(manager, 'photo_manager');
  const club = (await manager.json('/api/clubs')).find(row => row.slug === 'photo'); assert(club);
  const suffix = Date.now().toString(36), username = `complete_${suffix}`;
  await send(student, '/api/auth/register', { username, password, name: '整体验收虚构学生', major: '测试专业' }); await login(student, username, password);
  const content = `# 光影摄影社内部体验说明\n本资料仅用于整体验收，编号${suffix}。\n光影摄影社本次内部构图交流使用手机即可参加，练习内容是取景、光线与作品分享。联络暗号为紫鹭风车。`;
  const privateDoc = await upload(manager, `completion-private-${suffix}.md`, content, club.id);
  assert.equal(privateDoc.scope, 'CLUB');
  assert.equal((await student.request(`/api/ai/knowledge/${privateDoc.id}`)).status, 404);
  assert(!(await student.json('/api/ai/knowledge')).some(row => row.id === privateDoc.id));
  assert.equal((await anonymous.request('/api/ai/knowledge', { headers: { 'X-Actor-Role': 'PLATFORM_ADMIN', 'X-Actor-Id': '1' } })).status, 401);
  const file = new FormData(); file.append('file', new Blob(['虚构资料']), 'forbidden.md');
  assert.equal((await student.request('/api/ai/knowledge', { method: 'POST', body: file })).status, 403);
  const application = await send(student, '/api/recruitment/applications', { clubId: club.id, reason: '独立虚构整体验收账号申请，仅测试资料权限与业务闭环。' });
  await send(manager, `/api/manage/applications/${application.id}/review`, { approved: true, feedback: '虚构整体验收通过' });
  assert.equal((await student.json(`/api/ai/knowledge/${privateDoc.id}`)).content, content);
  const rag = await send(student, '/api/ai/chat', { question: '光影摄影社内部构图交流使用什么设备？联络暗号是什么？' });
  assert.equal(rag.mode, 'OLLAMA'); assert(rag.references.some(ref => ref.documentId === privateDoc.id)); assert(rag.answer.trim());
  assert.equal((await admin.request(`/api/ai/conversations/${rag.conversationId}`)).status, 404);
  assert.equal((await send(admin, '/api/ai/chat', { question: '继续回答', conversationId: rag.conversationId }, 'POST', 404)).status, 404);
  const agent = await send(student, '/api/ai/assistant', { question: '请调用 search_clubs 查询摄影社，依据结果简要说明社团名称和招新条件。' });
  assert.equal(agent.retrieval, 'REACT_TOOLS'); assert(agent.steps.length >= 1 && agent.steps.length <= 3);
  assert(agent.steps.every(step => ['search_clubs', 'get_club', 'search_activities', 'get_activity', 'my_registrations', 'search_knowledge'].includes(step.tool)));
  const before = (await manager.json(`/api/manage/clubs/${club.id}/activities`)).length;
  const plan = await send(manager, '/api/ai/plans', { clubId: club.id, requirement: '生成一场手机摄影新手构图交流的简短介绍，100字左右，包含作品分享和安全提醒；不填写时间、地点和人数。' });
  assert.equal((await manager.json(`/api/manage/clubs/${club.id}/activities`)).length, before, 'planning is side-effect free');
  const body = { title: `验收-${suffix}`, description: plan.description, location: '虚构整体验收教室', startTime: '2099-10-20T16:00:00', registrationDeadline: '2099-10-19T16:00:00', capacity: 20 };
  const path = `/api/manage/clubs/${club.id}/drafts/${plan.requestId || randomUUID()}`;
  const draft = await send(manager, path, body, 'PUT'); assert.equal(draft.status, 'DRAFT');
  assert.equal((await send(manager, path, body, 'PUT')).id, draft.id);
  assert.equal((await manager.json(`/api/manage/clubs/${club.id}/activities`)).length, before + 1);
  assert.equal((await anonymous.request(`/api/activities/${draft.id}`)).status, 404);
  await send(manager, `/api/manage/activities/${draft.id}`, { ...body, description: `${plan.description}\n负责人已核对，虚构测试。` }, 'PUT');
  await send(manager, `/api/manage/activities/${draft.id}/publish`, {});
  await send(student, `/api/activities/${draft.id}/registrations`, {});
  await send(manager, `/api/manage/activities/${draft.id}/cancel`, { reason: '整体验收虚构活动已结束验证' });
  assert.equal((await student.json(`/api/activities/${draft.id}`)).status, 'CANCELLED');
  assert.equal((await student.json('/api/registrations/mine')).find(row => row.activityId === draft.id).status, 'CANCELLED');
  await send(student, `/api/favorites/${club.id}`, {}, 'PUT');
  const conversations = [];
  for (const id of [rag.conversationId, agent.conversationId]) conversations.push({ id, history: await student.json(`/api/ai/conversations/${id}`) });
  fixture = { username, clubId: club.id, documentId: privateDoc.id, content, activityId: draft.id, conversations, checkedAt: new Date().toISOString() };
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true }); await writeFile(fixtureFile, JSON.stringify(fixture, null, 2));
  console.log(JSON.stringify({ result: 'PASS', documentId: privateDoc.id, activityId: draft.id, account: username, actualModel: status.mode, toolSteps: agent.steps.map(step => step.tool), ragAnswer: rag.answer, planTitle: plan.title }, null, 2));
}
