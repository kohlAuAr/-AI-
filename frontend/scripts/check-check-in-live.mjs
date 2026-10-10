import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';
import { createSessionClient } from '../../scripts/http-session.mjs';

const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(stage));
const fixturePath = new URL('../../.run/check-in-page-fixture.json', import.meta.url);
const client = createSessionClient(base), realFetch = globalThis.fetch;
globalThis.fetch = (url, options) => String(url).startsWith('/api/') ? client.request(String(url), options) : realFetch(url, options);
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
const write = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
const localTime = offset => {
  const date = new Date(Date.now() + offset * 60000);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:00`;
};
try {
  const identity = await server.ssrLoadModule('/src/prototype/business.ts');
  const activities = await server.ssrLoadModule('/src/community/activities.ts');
  const checkIn = await server.ssrLoadModule('/src/community/checkIn.ts');
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  const Panel = (await server.ssrLoadModule('/src/community/CommunityCheckInPanel.vue')).default;
  async function page(path) { await router.push(path); await router.isReady(); return renderToString(createSSRApp(App).use(router)); }
  async function login(username) { await identity.logout(); await identity.login(username, 'CampusDemo123!'); await activities.refreshActivities(); }
  await identity.login('photo_manager', 'CampusDemo123!');
  const club = identity.business.clubs.find(c => c.id === 'photo');
  assert(club?.backendId); identity.business.managedClubId = club.backendId;
  await activities.refreshActivities();
  let fixture;
  if (stage === 'flow') {
    const draft = await client.json(`/api/manage/clubs/${club.backendId}/activities`, write({
      title: `签到页面联调（虚构）${Date.now().toString(36)}`, description: '仅用于现场签到页面验收，不是真实校园活动。', location: '虚构签到验收活动室',
      startTime: localTime(20), registrationDeadline: localTime(10), capacity: 5,
    }));
    await activities.refreshActivities(); assert(await activities.publishActivity(draft.id));
    await checkIn.loadCheckIn(draft.id); assert(await checkIn.changeCheckIn('open'));
    const code = checkIn.checkInState.report.code; assert(code);
    const publicEvent = await client.json(`/api/activities/${draft.id}`);
    assert(!JSON.stringify(publicEvent).includes(code), 'public detail never exposes code');
    await login('student2');
    await client.json(`/api/activities/${draft.id}/check-in`, write({ code }), 409);
    await login('student'); assert(await activities.registerActivity(draft.id));
    assert((await page(`/activities/${draft.id}`)).includes('现场签到码'));
    assert.equal(await activities.checkInActivity(draft.id, 'wrong'), false);
    assert(activities.activityState.writeError.includes('不正确'));
    assert(await activities.checkInActivity(draft.id, code));
    const saved = activities.myRegistrations.value.find(r => r.activityId === draft.id);
    assert(saved.checkedInAt);
    assert(await activities.checkInActivity(draft.id, code));
    assert.equal(activities.myRegistrations.value.find(r => r.activityId === draft.id).checkedInAt, saved.checkedInAt);
    await client.json(`/api/activities/${draft.id}/registrations/cancel`, write(), 409);
    assert((await page(`/activities/${draft.id}`)).includes('已签到 · 不可取消报名'));
    assert((await page('/me?tab=registrations')).includes(`签到时间：${activities.activityTime(saved.checkedInAt)}`));
    fixture = { activityId: draft.id, clubId: club.backendId, registrationId: saved.id, checkedInAt: saved.checkedInAt, title: draft.title };
    await login('photo_manager'); identity.business.managedClubId = club.backendId;
    await checkIn.loadCheckIn(draft.id); assert.equal(checkIn.checkInState.report.checkedIn, 1);
    assert(await checkIn.changeCheckIn('close')); assert.equal(checkIn.checkInState.report.code, null);
    assert(await checkIn.changeCheckIn('open')); const newCode = checkIn.checkInState.report.code; assert.notEqual(newCode, code);
    await login('student'); assert.equal(await activities.checkInActivity(draft.id, code), false);
    assert(await activities.checkInActivity(draft.id, newCode));
    assert.equal(activities.myRegistrations.value.find(r => r.activityId === draft.id).checkedInAt, saved.checkedInAt);
    await login('photo_manager'); identity.business.managedClubId = club.backendId;
    await checkIn.loadCheckIn(draft.id); assert(await checkIn.changeCheckIn('close'));
    await mkdir(new URL('../../.run/', import.meta.url), { recursive: true });
    await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
  } else fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
  await checkIn.loadCheckIn(fixture.activityId);
  assert.equal(checkIn.checkInState.report.open, false); assert.equal(checkIn.checkInState.report.code, null);
  assert.equal(checkIn.checkInState.report.registered, 1); assert.equal(checkIn.checkInState.report.checkedIn, 1);
  assert(checkIn.checkInState.participants.some(p => p.id === fixture.registrationId && p.checkedInAt === fixture.checkedInAt));
  assert((await renderToString(createSSRApp(Panel, { activityId: fixture.activityId }))).includes('已签到'));
  assert((await page('/manage/activities')).includes('签到与报名名单'));
  const outsider = createSessionClient(base);
  await outsider.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: 'code_manager', password: 'CampusDemo123!' }) });
  for (const suffix of ['', '/open', '/close']) await outsider.json(`/api/manage/activities/${fixture.activityId}/check-in${suffix}`, suffix ? write() : {}, 403);
  await login('student'); assert.equal(checkIn.checkInState.report, null); assert.equal(checkIn.checkInState.participants.length, 0);
  await client.json(`/api/manage/activities/${fixture.activityId}/check-in`, {}, 403);
  assert.equal(await activities.checkInActivity(fixture.activityId, 'any-code'), false);
  const row = activities.myRegistrations.value.find(r => r.activityId === fixture.activityId);
  assert.equal(row.checkedInAt, fixture.checkedInAt); assert.equal(row.id, fixture.registrationId);
  assert((await page('/me?tab=registrations')).includes(`签到时间：${activities.activityTime(fixture.checkedInAt)}`));
  console.log(`PASS ${stage}: actual Vue clients + HTTP + SSR; activity ${fixture.activityId}; signup, wrong code, duplicate check-in, rotation, retained timestamp, closed state and scoped manager/student permissions. Not browser/mobile acceptance.`);
} finally { await server.close(); globalThis.fetch = realFetch; }
