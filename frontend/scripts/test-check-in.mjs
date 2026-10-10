import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const manager = { id: 2, name: '负责人', role: 'MANAGER', managedClubIds: [10, 20] };
const student = { id: 1, name: '学生', role: 'STUDENT', managedClubIds: [] };
const event = { id: 100, clubId: 10, title: '签到测试活动', description: '虚构活动', location: '测试活动室', startTime: '2099-10-10T12:00:00', registrationDeadline: '2099-10-10T11:00:00', capacity: 10, enrolled: 1, status: 'PUBLISHED', checkInOpen: false };
const registration = { id: 1, activityId: 100, clubId: 10, title: event.title, startTime: event.startTime, location: event.location, status: 'REGISTERED', registeredAt: '2026-10-10T10:00:00', checkedInAt: null };
const report = { activityId: 100, open: false, code: null, registered: 1, checkedIn: 0 };
let business, calls = [], early = false, failRead = false, failWrite = false, lostResponse = false, delayRead, delayWrite;
globalThis.fetch = async (url, options = {}) => {
  const path = String(url), method = options.method || 'GET';
  if (path === '/api/auth/session') return Response.json({ user: business.user, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'check-in-csrf' });
  calls.push({ path, method, body: options.body });
  if (method === 'GET') {
    let response;
    if (path.endsWith('/check-in')) response = failRead ? Response.json({ detail: '签到读取失败' }, { status: 503 }) : Response.json(report);
    else if (path.endsWith('/registrations/mine')) response = Response.json(business.user?.id === student.id ? [registration] : []);
    else if (path.endsWith('/registrations')) response = Response.json([{ id: 1, name: '现场学生', major: '计算机', registeredAt: registration.registeredAt, checkedInAt: registration.checkedInAt }]);
    else if (path.endsWith('/activities')) response = Response.json([event]);
    else if (path.startsWith('/api/notifications?')) response = Response.json({ items: [], total: 0, unread: 0, page: 0, hasMore: false });
    else throw new Error(`Unexpected read ${path}`);
    if (delayRead && path.endsWith('/check-in')) { const pause = delayRead; delayRead = undefined; await pause; }
    return response;
  }
  assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'check-in-csrf');
  if (failWrite) return Response.json({ detail: '签到保存失败' }, { status: 503 });
  if (path.endsWith('/open')) {
    if (early) return Response.json({ detail: '活动开始前 30 分钟才能开启签到' }, { status: 409 });
    if (!report.open) { report.open = true; report.code = 'new-field-code'; event.checkInOpen = true; }
  } else if (path.endsWith('/close')) { report.open = false; report.code = null; event.checkInOpen = false; }
  else if (path.endsWith('/check-in')) {
    if (!report.open) return Response.json({ detail: '签到未开放或已关闭' }, { status: 409 });
    if (JSON.parse(options.body).code !== report.code) return Response.json({ detail: '签到码不正确' }, { status: 400 });
    registration.checkedInAt ||= '2026-10-10T11:50:00'; report.checkedIn = 1;
  } else throw new Error(`Unexpected write ${path}`);
  const response = Response.json(path.endsWith('/check-in') ? registration : report);
  if (delayWrite) { const pause = delayWrite; delayWrite = undefined; await pause; }
  if (lostResponse) { lostResponse = false; throw new Error('响应丢失'); }
  return response;
};
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
try {
  ({ business } = await server.ssrLoadModule('/src/prototype/business.ts'));
  const checkIn = await server.ssrLoadModule('/src/community/checkIn.ts');
  const activities = await server.ssrLoadModule('/src/community/activities.ts');
  const { checkInState, loadCheckIn, changeCheckIn, clearCheckIn } = checkIn;
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  const Panel = (await server.ssrLoadModule('/src/community/CommunityCheckInPanel.vue')).default;
  async function page(path) { await router.push(path); await router.isReady(); return renderToString(createSSRApp(App).use(router)); }
  for (const user of [null, student, { id: 3, role: 'PLATFORM_ADMIN', managedClubIds: [] }]) {
    business.user = user; business.managedClubId = 10;
    await loadCheckIn(100); assert.equal(checkInState.report, null); assert.equal(await changeCheckIn('open'), false);
  }
  assert.equal(calls.length, 0, 'no manager code requests for other roles');
  business.user = manager; business.managedClubId = 10;
  await activities.refreshActivities(); await loadCheckIn(100);
  early = true; assert.equal(await changeCheckIn('open'), false); assert(checkInState.writeError.includes('30 分钟')); early = false;
  assert(await changeCheckIn('open')); assert.equal(checkInState.report.code, 'new-field-code');
  assert((await renderToString(createSSRApp(Panel, { activityId: 100 }))).includes('现场签到码'));
  failWrite = true; assert.equal(await changeCheckIn('close'), false); assert(checkInState.report.open); failWrite = false;
  lostResponse = true; assert.equal(await changeCheckIn('close'), false); assert.equal(checkInState.report.code, null, 'lost close response clears obsolete code');
  assert(await changeCheckIn('open'));
  failRead = true; assert(await changeCheckIn('close')); assert.equal(checkInState.report, null); assert(checkInState.notice.includes('已保存')); failRead = false;
  await loadCheckIn(100); assert(await changeCheckIn('open'));
  let release;
  delayRead = new Promise(resolve => { release = resolve; });
  const lateRead = loadCheckIn(100); business.managedClubId = 20;
  assert.equal(checkInState.report, null); assert.equal(checkInState.participants.length, 0);
  release(); await lateRead; assert.equal(checkInState.report, null, 'late club read cannot restore code');
  business.managedClubId = 10; await loadCheckIn(100);
  delayWrite = new Promise(resolve => { release = resolve; });
  const lateWrite = changeCheckIn('close'); await new Promise(resolve => setTimeout(resolve, 0));
  business.user = student; assert.equal(checkInState.report, null); release(); assert.equal(await lateWrite, false);
  assert.equal(checkInState.notice, '');
  await activities.refreshActivities();
  assert.equal(await activities.checkInActivity(100, ''), false);
  assert.equal(await activities.checkInActivity(100, 'wrong'), false); assert(activities.activityState.writeError.includes('已关闭'));
  report.open = true; report.code = 'student-code'; event.checkInOpen = true;
  await activities.refreshActivities();
  assert((await page('/activities/100')).includes('现场签到码'));
  assert.equal(await activities.checkInActivity(100, 'wrong'), false); assert(activities.activityState.writeError.includes('不正确'));
  failWrite = true; assert.equal(await activities.checkInActivity(100, 'student-code'), false); assert.equal(activities.myRegistrations.value[0].checkedInAt, null); failWrite = false;
  lostResponse = true; assert.equal(await activities.checkInActivity(100, ' student-code '), false);
  assert(activities.myRegistrations.value[0].checkedInAt, 'lost response is reconciled by reading own registrations');
  assert(await activities.checkInActivity(100, 'student-code')); assert.equal(activities.myRegistrations.value[0].checkedInAt, '2026-10-10T11:50:00');
  assert((await page('/activities/100')).includes('已签到 · 不可取消报名'));
  assert((await page('/me?tab=registrations')).includes('签到时间：2026-10-10 11:50'));
  delayWrite = new Promise(resolve => { release = resolve; });
  const lateStudent = activities.checkInActivity(100, 'student-code'); await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(await activities.checkInActivity(100, 'student-code'), false, 'double submit blocked');
  business.user = { ...student, id: 9 }; assert.equal(activities.myRegistrations.value.length, 0);
  release(); assert.equal(await lateStudent, false); assert.equal(activities.myRegistrations.value.length, 0); assert.equal(activities.activityState.writeError, '');
  business.user = manager; business.managedClubId = 999; const count = calls.length; await loadCheckIn(100); assert.equal(calls.length, count);
  clearCheckIn();
  console.log('PASS check-in: manager scope, CSRF, early opening, wrong/closed code, response loss, failed writes/reads, duplicate submit, attendance rendering and late account/club isolation. Mock HTTP + SSR, not browser clicks.');
} finally { await server.close(); }
