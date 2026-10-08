import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(stage));
const student = createSessionClient(base), second = createSessionClient(base), manager = createSessionClient(base), outsider = createSessionClient(base);
const post = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
async function login(client, username) { await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) }); }
const file = new URL('../.run/activity-smoke.json', import.meta.url);
await login(manager, 'photo_manager'); await login(student, 'student'); await login(second, 'student2'); await login(outsider, 'code_manager');
const clubs = await manager.json('/api/clubs'), photo = clubs.find(c => c.slug === 'photo'); assert(photo);
if (stage === 'restored') {
  const saved = JSON.parse(await readFile(file, 'utf8'));
  const activity = await student.json(`/api/activities/${saved.activityId}`);
  assert.equal(activity.status, 'PUBLISHED'); assert.equal(activity.enrolled, 1);
  const mine = await student.json('/api/registrations/mine');
  assert(mine.some(r => r.id === saved.registrationId && r.activityId === saved.activityId && r.status === 'REGISTERED'));
  assert((await manager.json(`/api/manage/activities/${saved.activityId}/registrations`)).some(r => r.id === saved.registrationId));
  console.log(`PASS: same activity ${saved.activityId} and registration ${saved.registrationId} retained after process restart and fresh sessions.`);
} else {
  const anonymous = createSessionClient(base);
  await anonymous.json('/api/registrations/mine', {}, 401);
  const future = days => { const date = new Date(); date.setDate(date.getDate() + days); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T14:00:00`; };
  let saved;
  try { saved = JSON.parse(await readFile(file, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  let activity = saved ? await student.json(`/api/activities/${saved.activityId}`) : null;
  if (!activity) {
    const draft = await manager.json(`/api/manage/clubs/${photo.id}/activities`, post({ title: '手机摄影交流（业务验收）', description: '虚构本地活动：校园构图与手机摄影交流。', location: '学生中心 204', startTime: future(7), registrationDeadline: future(6), capacity: 1 }));
    await anonymous.json(`/api/activities/${draft.id}`, {}, 404);
    await outsider.json(`/api/manage/activities/${draft.id}/publish`, post(), 403);
    activity = await manager.json(`/api/manage/activities/${draft.id}/publish`, post());
  }
  const id = activity.id;
  assert.equal(activity.capacity, 1);
  await manager.json(`/api/manage/activities/${id}/publish`, post(), 409);
  await outsider.json(`/api/manage/activities/${id}/registrations`, {}, 403);
  await student.json(`/api/manage/activities/${id}/registrations`, {}, 403);
  await outsider.json(`/api/manage/clubs/${photo.id}/activities`, {}, 403);
  if ((await second.json('/api/registrations/mine')).some(r => r.activityId === id && r.status === 'REGISTERED')) await second.json(`/api/activities/${id}/registrations/cancel`, post());
  let mine = (await student.json('/api/registrations/mine')).find(r => r.activityId === id && r.status === 'REGISTERED');
  if (!mine) mine = await student.json(`/api/activities/${id}/registrations`, post());
  await student.json(`/api/activities/${id}/registrations`, post(), 409);
  await second.json(`/api/activities/${id}/registrations`, post(), 409);
  assert.equal((await student.json(`/api/activities/${id}`)).enrolled, 1);
  await student.json(`/api/activities/${id}/registrations/cancel`, post());
  assert.equal((await student.json(`/api/activities/${id}`)).enrolled, 0);
  const secondRegistration = await second.json(`/api/activities/${id}/registrations`, post());
  assert.notEqual(secondRegistration.id, mine.id);
  assert(!(await second.json('/api/registrations/mine')).some(r => r.id === mine.id));
  await second.json(`/api/activities/${id}/registrations/cancel`, post());
  const registered = await student.json(`/api/activities/${id}/registrations`, post());
  assert.equal(registered.id, mine.id);
  const noCsrf = await fetch(base + `/api/activities/${id}/registrations`, post()); assert.equal(noCsrf.status, 403);
  await login(student, 'student');
  assert((await student.json('/api/registrations/mine')).some(r => r.id === registered.id && r.status === 'REGISTERED'));
  assert((await manager.json(`/api/manage/activities/${id}/registrations`)).some(r => r.id === registered.id));
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(file, JSON.stringify({ checkedAt: new Date().toISOString(), base, activityId: id, registrationId: registered.id, result: 'PASS', data: 'SYNTHETIC_DEMO_ACCOUNTS' }, null, 2));
  console.log(`PASS: activity ${id}: draft/publish, scoped roles, signup/duplicate/full/cancel/rejoin, real count, CSRF, fresh login. Synthetic local records retained.`);
}
