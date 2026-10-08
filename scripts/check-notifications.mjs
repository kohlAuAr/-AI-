import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createSessionClient } from './http-session.mjs';

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(stage));
const student = createSessionClient(base), second = createSessionClient(base), manager = createSessionClient(base);
const post = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
async function login(client, username) { await client.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password: 'CampusDemo123!' }) }); }
async function all(client) {
  const rows = []; let page = 0, inbox;
  do { inbox = await client.json(`/api/notifications?page=${page++}`); rows.push(...inbox.items); } while (inbox.hasMore);
  return rows;
}
const file = new URL('../.run/notification-smoke.json', import.meta.url);
await login(student, 'student'); await login(second, 'student2'); await login(manager, 'photo_manager');
if (stage === 'restored') {
  const saved = JSON.parse(await readFile(file, 'utf8')), rows = await all(student);
  assert.deepEqual(rows.filter(n => saved.noticeIds.includes(n.id)).map(n => n.id).sort((a, b) => a - b), saved.noticeIds);
  assert.equal(rows.find(n => n.id === saved.readId).readAt, saved.readAt);
  assert(rows.filter(n => saved.noticeIds.includes(n.id) && n.id !== saved.readId).every(n => n.readAt === null));
  assert(!(await all(second)).some(n => saved.noticeIds.includes(n.id)));
  console.log(`PASS: notices ${saved.noticeIds.join(',')} and exact read timestamp persisted after process restart; fresh accounts remain isolated.`);
} else {
  const anonymous = createSessionClient(base);
  await anonymous.json('/api/notifications', {}, 401);
  const photo = (await manager.json('/api/clubs')).find(c => c.slug === 'photo');
  const future = days => { const date = new Date(); date.setDate(date.getDate() + days); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T14:00:00`; };
  const draft = await manager.json(`/api/manage/clubs/${photo.id}/activities`, post({ title: '站内通知联调（虚构验收活动）', description: '仅用于本地通知链验收，不是真实校园活动。', location: '学生中心 204', startTime: future(7), registrationDeadline: future(6), capacity: 2 }));
  await manager.json(`/api/manage/activities/${draft.id}/publish`, post());
  const before = await all(student), beforeSecond = await all(second);
  const path = `/api/activities/${draft.id}/registrations`;
  await student.json(path, post()); await student.json(path, post(), 409);
  await student.json(path + '/cancel', post()); await student.json(path + '/cancel', post(), 409);
  await student.json(path, post()); await second.json(path, post());
  const newRows = (await all(student)).filter(n => !before.some(old => old.id === n.id));
  assert.equal(newRows.length, 3);
  assert.deepEqual(newRows.map(n => n.type), ['ACTIVITY_REGISTERED', 'ACTIVITY_CANCELLED', 'ACTIVITY_REGISTERED']);
  assert(newRows.every(n => n.sourceId === draft.id && n.targetPath === `/activities/${draft.id}`));
  const secondRows = (await all(second)).filter(n => !beforeSecond.some(old => old.id === n.id));
  assert.equal(secondRows.length, 1);
  await second.json(`/api/notifications/${newRows[0].id}/read`, post(), 404);
  const read = await student.json(`/api/notifications/${newRows[0].id}/read`, post());
  assert(read.readAt);
  assert.equal((await student.json(`/api/notifications/${read.id}/read`, post())).readAt, read.readAt);
  const studentUnread = (await student.json('/api/notifications')).unread;
  await second.json('/api/notifications/read-all', post());
  assert.equal((await second.json('/api/notifications')).unread, 0);
  assert.equal((await student.json('/api/notifications')).unread, studentUnread, 'reading all affects only the current account');
  await mkdir(new URL('../.run/', import.meta.url), { recursive: true });
  await writeFile(file, JSON.stringify({ activityId: draft.id, noticeIds: newRows.map(n => n.id).sort((a, b) => a - b), readId: read.id, readAt: read.readAt }, null, 2));
  console.log(`PASS: real HTTP notification transitions, duplicate prevention, single/all read, owner isolation; synthetic activity ${draft.id} retained for restart verification.`);
}
