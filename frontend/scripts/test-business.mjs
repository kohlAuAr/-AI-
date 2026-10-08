import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
let user = null; let failure = false; let writes = 0;
const student = { id: 1, username: 'student', name: '林同学', major: '计算机科学', role: 'STUDENT', managedClubIds: [] };
const manager = { id: 2, username: 'photo_manager', name: '摄影社负责人', major: '测试', role: 'MANAGER', managedClubIds: [10] };
const applications = [];
const members = [];
globalThis.fetch = async (url, options = {}) => {
  if (failure) throw new Error('校园服务不可用');
  const path = String(url); const method = options.method || 'GET';
  if (method === 'POST') {
    assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'test-csrf');
    writes++;
    if (path.endsWith('/auth/login')) user = new URLSearchParams(options.body).get('username') === 'photo_manager' ? manager : student;
    else if (path.endsWith('/auth/logout')) user = null;
    else if (path.endsWith('/recruitment/applications')) applications.unshift({ id: 20, clubId: 10, userId: 1, name: '林同学', major: '计算机科学', reason: JSON.parse(options.body).reason, status: 'pending', feedback: '', createdAt: '2026-10-04T10:00:00Z' });
    else if (path.endsWith('/review')) { applications[0].status = 'approved'; members.push({ id: 30, clubId: 10, userId: 1, name: '林同学', major: '计算机科学', role: 'MEMBER', joinedAt: '2026-10-04T10:01:00Z' }); }
    else throw new Error(`Unexpected POST ${path}`);
    return Response.json({ status: 'OK' });
  }
  if (path.endsWith('/auth/session')) return Response.json({ user, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'test-csrf', demoAccounts: true });
  if (path === '/api/clubs') return Response.json([{ id: 10, slug: 'photo', name: '光影摄影社', category: '文化艺术', description: '后端社团介绍', tags: '摄影,户外', members: members.length, recruiting: true, requirements: '后端招新条件', schedule: '周三', place: '学生中心' }]);
  if (path.endsWith('/my-applications')) return Response.json(applications.filter(a => a.userId === user?.id));
  if (path.endsWith('/memberships/mine')) return Response.json(members.filter(m => m.userId === user?.id));
  if (path.endsWith('/applications')) return Response.json(applications);
  if (path.endsWith('/members')) return Response.json(members);
  throw new Error(`Unexpected GET ${path}`);
};
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
try {
  const client = await server.ssrLoadModule('/src/prototype/business.ts');
  const { business } = client;
  assert.equal(client.businessMode, true);
  await client.refreshBusiness();
  assert.equal(business.user, null);
  assert.equal(business.applications.length, 0, 'no prototype requests leak into live data');
  assert.equal(business.clubs[0].description, '后端社团介绍');
  await client.login('student', 'test');
  assert.equal(business.user.id, 1);
  await client.submitApplication(business.clubs[0], '我想学习摄影');
  assert.equal(business.applications[0].status, 'pending');
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  async function page(path) { await router.push(path); await router.isReady(); return renderToString(createSSRApp(App).use(router)); }
  const directory = await page('/clubs');
  assert(directory.includes('community-search'));
  assert(directory.includes('后端社团介绍') && directory.includes('学生中心'));
  assert(directory.includes('共 1 个'), 'directory counts backend clubs rather than prototype clubs');
  assert(!directory.includes('86 位成员'), 'directory does not substitute prototype member counts');
  assert((await page('/me')).includes('待审核'));
  assert((await page('/manage/recruitment')).includes('普通学生不能通过切换页面获得审核权限'));
  assert((await page('/clubs/photo')).includes('后端招新条件'));
  assert((await page('/clubs/photo')).includes('待审核'));
  await client.logout();
  assert.equal(business.applications.length, 0);
  assert.equal(business.memberships.length, 0);
  await client.login('photo_manager', 'test');
  assert.equal(client.managedClubs.value.length, 1);
  assert.equal(business.managedApplications.length, 1);
  assert((await page('/manage/recruitment')).includes('查看并审核'));
  await client.reviewApplication('20', true, '欢迎');
  assert.equal(business.managedMembers.length, 1);
  assert((await page('/manage/members')).includes('林同学'));
  assert((await page('/manage/activities')).includes('尚未接入数据库'));
  await client.logout(); await client.login('student', 'test');
  assert.equal(business.applications[0].status, 'approved');
  assert.equal(business.memberships[0].clubId, 'photo');
  assert((await page('/clubs/photo')).includes('已加入社团'));
  failure = true;
  await client.refreshBusiness();
  assert(business.error.includes('校园服务不可用'));
  assert.equal(business.applications.length, 0, 'failure does not fall back to fake requests');
  assert((await page('/me')).includes('不会自动切换成模拟申请'));
  assert.equal(writes, 7);
  console.log('PASS: live frontend data, CSRF, login/logout isolation, application, manager review, memberships and failure disclosure. Mock HTTP + SSR, not a real browser.');
} finally { await server.close(); }
