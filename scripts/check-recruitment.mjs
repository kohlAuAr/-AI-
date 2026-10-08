import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(stage));
const student = createSessionClient(base), manager = createSessionClient(base), outsider = createSessionClient(base);
const password = 'CampusDemo123!';
const post = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
async function login(client, username) { await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) }); }
// Tomcat may listen before the demo runner commits accounts and clubs.
for (let attempt = 0; attempt < 30; attempt++) {
  try {
    const response = await fetch(base + '/api/clubs', { signal: AbortSignal.timeout(2000) });
    if (response.ok && (await response.json()).some(c => c.slug === 'photo')) break;
  } catch { /* Wait only for local startup. */ }
  if (attempt === 29) throw new Error('Campus service/demo data did not become ready.');
  await new Promise(resolve => setTimeout(resolve, 1000));
}
await login(student, 'student');
const clubs = await student.json('/api/clubs');
const photo = clubs.find(c => c.slug === 'photo');
assert(photo);
if (stage === 'restored') {
  const previous = JSON.parse(await readFile(new URL('../.run/recruitment-smoke.json', import.meta.url), 'utf8'));
  const applications = await student.json('/api/recruitment/my-applications');
  assert.equal(applications.find(a => a.id === previous.applicationId)?.status, 'approved');
  assert((await student.json('/api/memberships/mine')).some(m => m.clubId === photo.id));
  await login(manager, 'photo_manager');
  assert((await manager.json(`/api/manage/clubs/${photo.id}/members`)).some(m => m.userId === previous.studentId));
  console.log('PASS: the SAME application and member records survived a campus-service process restart and fresh login.');
} else {
  const anonymous = createSessionClient(base);
  await anonymous.json('/api/recruitment/my-applications', {}, 401);
  const invalidLogin = createSessionClient(base);
  await invalidLogin.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: 'student', password: 'wrong' }) }, 401);
  const who = (await student.json('/api/auth/session')).user;
  // This script only uses the documented synthetic demo accounts; it never deletes existing records.
  const existing = (await student.json('/api/recruitment/my-applications')).find(a => a.clubId === photo.id && ['pending', 'approved'].includes(a.status));
  const application = existing || await student.json('/api/recruitment/applications', post({ clubId: photo.id, reason: '自动化验收：希望学习校园摄影。' }));
  await student.json('/api/recruitment/applications', post({ clubId: photo.id, reason: '重复申请应被拦截' }), 409);
  const noCsrf = await fetch(base + '/api/recruitment/applications', post({ clubId: photo.id, reason: '无安全令牌请求' }));
  assert.equal(noCsrf.status, 403);
  await login(manager, 'photo_manager');
  await login(outsider, 'code_manager');
  await outsider.json(`/api/manage/clubs/${photo.id}/applications`, {}, 403);
  await outsider.json(`/api/manage/clubs/${photo.id}/members`, {}, 403);
  await outsider.json(`/api/manage/applications/${application.id}/review`, post({ approved: true }), 403);
  await student.json(`/api/manage/applications/${application.id}/review`, post({ approved: true }), 403);
  if (application.status === 'pending') {
    assert((await manager.json(`/api/manage/clubs/${photo.id}/applications`)).some(a => a.id === application.id));
    await manager.json(`/api/manage/applications/${application.id}/review`, post({ approved: true, feedback: '自动化验收：欢迎加入。' }));
  }
  await manager.json(`/api/manage/applications/${application.id}/review`, post({ approved: false }), 409);
  const mine = await student.json('/api/recruitment/my-applications');
  assert.equal(mine.find(a => a.id === application.id)?.status, 'approved');
  assert((await student.json('/api/memberships/mine')).some(m => m.clubId === photo.id));
  assert((await manager.json(`/api/manage/clubs/${photo.id}/members`)).some(m => m.userId === who.id));
  assert.equal((await student.json(`/api/clubs/${photo.id}`)).members, (await manager.json(`/api/manage/clubs/${photo.id}/members`)).length);
  await student.json('/api/auth/logout', { method: 'POST' });
  await student.json('/api/recruitment/my-applications', {}, 401);
  await login(student, 'student');
  assert.equal((await student.json('/api/recruitment/my-applications')).find(a => a.id === application.id)?.status, 'approved');
  const result = { checkedAt: new Date().toISOString(), base, applicationId: application.id, studentId: who.id, clubId: photo.id, result: 'PASS', data: 'SYNTHETIC_DEMO_ACCOUNTS' };
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(new URL('../.run/recruitment-smoke.json', import.meta.url), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
}
