import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from './render.mjs';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const manager = { id: 2, username: 'manager', name: '负责人', major: '测试', role: 'MANAGER', managedClubIds: [10, 20] };
const student = { id: 1, username: 'student', name: '学生', major: '测试', role: 'STUDENT', managedClubIds: [] };
const clubs = [10, 20].map(id => ({ id, slug: id === 10 ? 'photo' : 'code', name: `测试社团${id}`, category: '文化艺术', description: `介绍${id}`, tags: '摄影,编程', campus: '保留原校区', recruiting: true, requirements: '欢迎新手', schedule: '周三晚上', place: '学生中心', members: 1 }));
let business, failRead = false, failWrite = false, failCatalog = false, loseResponse = false, delayRead, delayWrite;
const calls = [];
globalThis.fetch = async (url, options = {}) => {
  const path = String(url), method = options.method || 'GET';
  if (path === '/api/auth/session') return Response.json({ user: business.user, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'club-settings-csrf', demoAccounts: true });
  calls.push({ path, method, body: options.body });
  if (method === 'PUT') {
    assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'club-settings-csrf');
    assert.equal(business.user.role, 'MANAGER');
    const id = Number(path.split('/').at(-1)); assert(business.user.managedClubIds.includes(id));
    if (failWrite) return Response.json({ detail: '社团名称已存在或保存失败' }, { status: 409 });
    const club = clubs.find(c => c.id === id); Object.assign(club, JSON.parse(options.body));
    const response = Response.json(club);
    if (delayWrite) { const pause = delayWrite; delayWrite = undefined; await pause; }
    if (loseResponse) { loseResponse = false; throw new Error('响应丢失'); }
    return response;
  }
  if (path === '/api/manage/clubs') {
    assert.equal(business.user.role, 'MANAGER');
    const response = failRead ? Response.json({ detail: '资料读取失败' }, { status: 503 }) : Response.json(clubs.filter(c => business.user.managedClubIds.includes(c.id)));
    if (delayRead) { const pause = delayRead; delayRead = undefined; await pause; }
    return response;
  }
  if (path === '/api/clubs') return failCatalog ? Response.json({ detail: '目录读取失败' }, { status: 503 }) : Response.json(clubs);
  if (path === '/api/favorites') return Response.json([]);
  if (path === '/api/profile') return Response.json({ ...business.user, interests: [], availableTime: '', interestDescription: '' });
  if (path === '/api/activities' || path.endsWith('/registrations/mine') || path.endsWith('/my-applications') || path.endsWith('/memberships/mine') || path.endsWith('/applications') || path.endsWith('/members')) return Response.json([]);
  throw new Error(`Unexpected ${method} ${path}; settings saves must never call AI`);
};
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
try {
  const identity = await server.ssrLoadModule('/src/prototype/business.ts'); ({ business } = identity);
  const settings = await server.ssrLoadModule('/src/community/clubSettings.ts');
  const { clubSettingsState: state, currentClubSettings: current, clubInput, validateClub, loadClubSettings, saveClubSettings } = settings;
  const recommendations = await server.ssrLoadModule('/src/community/recommendations.ts');
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  async function page(path = '/manage/club') { await router.push(path); await router.isReady(); return renderToString(createSSRApp(App).use(router)); }
  for (const user of [null, student, { id: 3, name: '平台管理员', role: 'PLATFORM_ADMIN', managedClubIds: [] }]) {
    business.user = user; business.managedClubId = 10; await loadClubSettings();
    assert.equal(current.value, null); assert.equal(await saveClubSettings(clubInput(clubs[0])), false);
    assert((await page()).includes('请使用社团负责人账号'));
  }
  assert.equal(calls.length, 0, 'unauthorized roles never fetch or write settings');
  business.user = manager; business.managedClubId = 10; await identity.refreshBusiness(); await loadClubSettings();
  assert.equal(current.value.campus, '保留原校区');
  const original = clubInput(current.value);
  for (const key of ['name', 'category', 'description', 'campus', 'requirements', 'schedule', 'place']) assert(validateClub({ ...original, [key]: '  ' })[key]);
  assert.equal(Object.keys(validateClub({ ...original, tags: '' })).length, 0);
  assert(validateClub({ ...original, description: '长'.repeat(2001) }).description);
  const beforeInvalid = calls.length; assert.equal(await saveClubSettings({ ...original, requirements: ' ' }), false); assert.equal(calls.length, beforeInvalid);
  let html = await page(); assert(html.includes('社团介绍') && html.includes('开放招新') && html.includes('所属校区'));
  recommendations.recommendationState.items = [{ name: '旧推荐' }]; recommendations.recommendationState.requested = true;
  const edited = { ...original, name: ' 修改后的摄影社 ', description: ' 手机摄影与新手教学 ', tags: '摄影,校园采风', recruiting: false };
  assert(await saveClubSettings(edited));
  assert.equal(current.value.name, '修改后的摄影社'); assert.equal(current.value.description, '手机摄影与新手教学'); assert.equal(current.value.recruiting, false);
  assert.equal(current.value.campus, original.campus, 'preserve campus even though old public catalog did not map it');
  assert.equal(business.clubs.find(c => c.backendId === 10).recruiting, false);
  assert.equal(recommendations.recommendationState.items.length, 0, 'saved edits clear stale recommendation cards');
  assert((await page('/clubs/photo')).includes('手机摄影与新手教学'));
  assert((await page('/clubs/photo')).includes('保留原校区'));
  assert(!(await page('/clubs/photo')).includes('全校学生 · 不限专业'), 'eligibility copy does not override manager-written requirements');
  failWrite = true; assert.equal(await saveClubSettings({ ...original, name: '未保存名称' }), false);
  assert.equal(current.value.name, '修改后的摄影社'); assert(state.writeError.includes('保存失败')); failWrite = false;
  loseResponse = true; assert.equal(await saveClubSettings({ ...clubInput(current.value), description: '响应丢失但已保存' }), false);
  assert(state.writeError.includes('响应丢失')); await loadClubSettings(); assert.equal(current.value.description, '响应丢失但已保存');
  failCatalog = true; assert(await saveClubSettings({ ...clubInput(current.value), recruiting: true }));
  assert(state.notice.includes('已保存') && state.notice.includes('刷新失败')); assert.equal(current.value.recruiting, true); failCatalog = false;
  await identity.refreshBusiness(); await loadClubSettings();
  let release;
  delayRead = new Promise(resolve => { release = resolve; }); const lateRead = loadClubSettings();
  business.managedClubId = 20; assert.equal(current.value, null); await loadClubSettings(); release(); await lateRead;
  assert.equal(current.value.id, 20, 'late old-club read cannot replace current settings');
  delayWrite = new Promise(resolve => { release = resolve; }); const lateWrite = saveClubSettings({ ...clubInput(current.value), description: '迟到保存' });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(await saveClubSettings(original), false, 'duplicate submit blocked');
  business.user = student; assert.equal(current.value, null); assert.equal(state.club, null); release(); assert.equal(await lateWrite, false);
  assert.equal(state.notice, ''); assert(!(await page()).includes('迟到保存'));
  business.user = manager; business.managedClubId = 999; const count = calls.length; await loadClubSettings(); assert.equal(calls.length, count);
  business.managedClubId = 10; failRead = true; await loadClubSettings(); assert.equal(current.value, null); assert(state.error.includes('读取失败'));
  console.log('PASS club settings: manager scope, complete fields, validation, CSRF, student catalog refresh, stale recommendation clearing, failed/lost writes, refresh failures, duplicate submit and late account/club isolation. Mock HTTP + SSR, not browser clicks.');
} finally { await server.close(); }
