import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
let user = null; let failure = false; let writes = 0; let notificationWriteFailure = false; let delayInbox = null;
let registered = null, profileWriteFailure = false, delayProfileWrite = null;
let recommendationFailure = false, delayRecommendation = null;
const profiles = new Map();
function profileFor(account) {
  if (!profiles.has(account.id)) profiles.set(account.id, { ...account, interests: [], availableTime: '', interestDescription: '' });
  return profiles.get(account.id);
}
const student = { id: 1, username: 'student', name: '林同学', major: '计算机科学', role: 'STUDENT', managedClubIds: [] };
const manager = { id: 2, username: 'photo_manager', name: '摄影社负责人', major: '测试', role: 'MANAGER', managedClubIds: [10] };
const platformAdmin = { id: 3, username: 'platform_admin', name: '首页管理员', major: '测试', role: 'PLATFORM_ADMIN', managedClubIds: [] };
const applications = [];
const members = [];
const activities = [], registrations = [];
const notices = [];
function notify(type, title, targetPath) { notices.unshift({ id: notices.length + 1, userId: 1, type, sourceId: 40, title, content: '数据库通知内容', targetPath, createdAt: '2026-10-05T10:00:00Z', readAt: null }); }
globalThis.fetch = async (url, options = {}) => {
  if (failure) throw new Error('校园服务不可用');
  const path = String(url); const method = options.method || 'GET';
  if (method === 'PUT' && path === '/api/profile') {
    assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'test-csrf');
    if (profileWriteFailure) return Response.json({ detail: '资料保存失败，请重试' }, { status: 503 });
    const profile = { ...profileFor(user), ...JSON.parse(options.body) };
    profiles.set(user.id, profile); user.name = profile.name; user.major = profile.major;
    if (delayProfileWrite) { const pause = delayProfileWrite; delayProfileWrite = null; await pause; }
    return Response.json(profile);
  }
  if (method === 'POST') {
    assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'test-csrf');
    writes++;
    if (path === '/api/ai/recommendations') {
      if (recommendationFailure) return Response.json({ detail: '语义推荐尚未配置 Embedding 模型' }, { status: 503 });
      const result = { method: 'SEMANTIC_COSINE', items: [{ clubId: 10, slug: 'photo', name: '光影摄影社', description: '摄影入门与校园采风', requirements: '欢迎新手', schedule: '周三', place: '学生中心', score: 0.876 }] };
      if (delayRecommendation) { const pause = delayRecommendation; delayRecommendation = null; await pause; }
      return Response.json(result);
    }
    if (notificationWriteFailure && path.includes('/notifications/')) return Response.json({ detail: '消息写入失败' }, { status: 409 });
    if (path.endsWith('/auth/register')) {
      const input = JSON.parse(options.body);
      if (registered?.username === input.username) return Response.json({ detail: '账号已存在' }, { status: 409 });
      registered = { id: 4, username: input.username, name: input.name, major: input.major, role: 'STUDENT', managedClubIds: [] };
      return Response.json(profileFor(registered));
    }
    if (path.endsWith('/auth/login')) { const name = new URLSearchParams(options.body).get('username'); user = name === registered?.username ? registered : name === 'platform_admin' ? platformAdmin : name === 'photo_manager' ? manager : student; }
    else if (path.endsWith('/auth/logout')) user = null;
    else if (path.endsWith('/recruitment/applications')) applications.unshift({ id: 20, clubId: 10, userId: 1, name: '林同学', major: '计算机科学', reason: JSON.parse(options.body).reason, status: 'pending', feedback: '', createdAt: '2026-10-04T10:00:00Z' });
    else if (path.endsWith('/review')) { applications[0].status = 'approved'; members.push({ id: 30, clubId: 10, userId: 1, name: '林同学', major: '计算机科学', role: 'MEMBER', joinedAt: '2026-10-04T10:01:00Z' }); notify('APPLICATION_APPROVED', '入社申请已通过', '/me?tab=applications'); }
    else if (path.endsWith('/activities')) activities.push({ ...JSON.parse(options.body), id: 40, clubId: 10, enrolled: 0, status: 'DRAFT', demo: true });
    else if (path.endsWith('/publish')) activities[0].status = 'PUBLISHED';
    else if (path.endsWith('/registrations/cancel')) { registrations[0].status = 'CANCELLED'; activities[0].enrolled = 0; notify('ACTIVITY_CANCELLED', '活动报名已取消', '/activities/40'); }
    else if (path.endsWith('/registrations')) { if (registrations.length) registrations[0].status = 'REGISTERED'; else registrations.push({ ...activities[0], id: 50, activityId: 40, userId: user.id, status: 'REGISTERED' }); activities[0].enrolled = 1; notify('ACTIVITY_REGISTERED', '活动报名成功', '/activities/40'); }
    else if (path.endsWith('/notifications/read-all')) notices.filter(n => n.userId === user?.id).forEach(n => { n.readAt ||= '2026-10-05T10:01:00Z'; });
    else if (/\/notifications\/\d+\/read$/.test(path)) notices.find(n => n.id === Number(path.split('/').at(-2)) && n.userId === user?.id).readAt ||= '2026-10-05T10:01:00Z';
    else throw new Error(`Unexpected POST ${path}`);
    return Response.json({ status: 'OK' });
  }
  if (path.endsWith('/auth/session')) return Response.json({ user, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'test-csrf', demoAccounts: true });
  if (path === '/api/profile') { assert(user, 'anonymous sessions must not request a profile'); return Response.json(profileFor(user)); }
  if (path === '/api/platform/banners' || path === '/api/platform/banner-targets') { assert.equal(user?.role, 'PLATFORM_ADMIN', 'non-admin page must not fetch platform management data'); return Response.json([]); }
  if (path.startsWith('/api/notifications?')) { const mine = notices.filter(n => n.userId === user?.id); const response = Response.json({ items: mine, total: mine.length, unread: mine.filter(n => !n.readAt).length, page: 0, hasMore: false }); if (delayInbox) { const pause = delayInbox; delayInbox = null; await pause; } return response; }
  if (path === '/api/clubs') return Response.json([{ id: 10, slug: 'photo', name: '光影摄影社', category: '文化艺术', description: '后端社团介绍', tags: '摄影,户外', members: members.length, recruiting: true, requirements: '后端招新条件', schedule: '周三', place: '学生中心' }]);
  if (path.endsWith('/my-applications')) return Response.json(applications.filter(a => a.userId === user?.id));
  if (path.endsWith('/memberships/mine')) return Response.json(members.filter(m => m.userId === user?.id));
  if (path.endsWith('/applications')) return Response.json(applications);
  if (path.endsWith('/members')) return Response.json(members);
  if (path === '/api/activities') return Response.json(activities.filter(a => a.status === 'PUBLISHED'));
  if (path.endsWith('/activities')) return Response.json(activities);
  if (path.endsWith('/registrations/mine')) return Response.json(registrations.filter(r => r.userId === user?.id));
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
  assert(directory.includes('只看招新中'), 'club directory exposes recruitment filtering');
  assert(directory.includes('后端社团介绍') && directory.includes('学生中心'));
  assert(directory.includes('共 1 个'), 'directory counts backend clubs rather than prototype clubs');
  assert(!directory.includes('86 位成员'), 'directory does not substitute prototype member counts');
  for (const path of ['/', '/recruitment', '/assistant', '/me', '/login', '/clubs/photo', '/guide', '/manage', '/manage/recruitment', '/manage/members', '/manage/finance', '/manage/activities', '/activities', '/activities/40', '/messages', '/system', '/system/clubs', '/system/activities', '/system/knowledge', '/system/chat', '/system/roadmap']) {
    const html = await page(path);
    assert(html.includes('community-app') && !html.includes('p-header') && !html.includes('app-shell'), `${path} uses the accepted community shell`);
    assert(!html.includes('p-hero') && !html.includes('p-kicker') && !html.includes('p-detail-hero') && !html.includes('eyebrow'), `${path} has no old marketing layout`);
    assert(html.includes('community-bottom-nav'), `${path} preserves mobile navigation`);
  }
  const loginPage = await page('/login');
  assert(loginPage.includes('autocomplete="username"') && loginPage.includes('autocomplete="current-password"'), 'login preserves password manager support');
  assert(loginPage.includes('community-demo-accounts') && !loginPage.includes('CAMPUS ACCOUNT'), 'demo accounts use a disclosure rather than a marketing panel');
  assert(loginPage.includes('注册学生账号'), 'login exposes student registration');
  const recruitment = await page('/recruitment');
  assert.equal(router.currentRoute.value.fullPath, '/clubs?recruiting=true', 'old recruitment route remains compatible');
  assert(recruitment.includes('<h1>社团</h1>'), 'recruitment is a club directory filter, not a separate page');
  assert(recruitment.includes('后端招新条件') && recruitment.includes('community-recruit-requirements'));
  assert((await page('/me')).includes('待审核'));
  assert((await page('/manage/recruitment')).includes('普通学生不能通过切换页面获得审核权限'));
  assert((await page('/platform/banners')).includes('请使用平台管理员账号'), 'students cannot use platform management');
  assert(!(await page('/me')).includes('首页内容管理'), 'student profile has no platform action');
  assert((await page('/clubs/photo')).includes('后端招新条件'));
  assert((await page('/clubs/photo')).includes('待审核'));
  await client.logout();
  assert.equal(business.applications.length, 0);
  assert.equal(business.memberships.length, 0);
  assert.equal(business.profile, null, 'logout clears private profile');
  await client.login('photo_manager', 'test');
  assert.equal(client.managedClubs.value.length, 1);
  assert.equal(business.managedApplications.length, 1);
  assert((await page('/manage/recruitment')).includes('查看并审核'));
  for (const path of ['/manage', '/manage/recruitment', '/manage/members', '/manage/finance']) {
    const html = await page(path);
    assert(html.includes('community-secondary-page') && html.includes('负责人导航'));
    assert(!html.includes('CLUB WORKSPACE') && !html.includes('每一份热爱'));
  }
  await client.reviewApplication('20', true, '欢迎');
  assert.equal(business.managedMembers.length, 1);
  assert((await page('/manage/members')).includes('林同学'));
  const activityClient = await server.ssrLoadModule('/src/community/activities.ts');
  await activityClient.refreshActivities();
  assert((await page('/manage/activities')).includes('新建活动'));
  await activityClient.saveDraft(10, { title: '数据库摄影活动', description: '数据库活动说明', location: '学生中心', startTime: '2099-10-08T14:00', registrationDeadline: '2099-10-07T14:00', capacity: 2 });
  assert.equal(activityClient.managedActivities.value[0].status, 'DRAFT');
  assert.equal(activityClient.activityState.activities.length, 0, 'draft is not visible to students');
  await activityClient.publishActivity(40);
  assert((await page('/activities')).includes('数据库摄影活动'));
  assert(!(await page('/activities')).includes('光影漫游'), 'normal mode does not use prototype activities');
  await client.logout(); await client.login('student', 'test');
  assert.equal(business.applications[0].status, 'approved');
  assert.equal(business.memberships[0].clubId, 'photo');
  assert((await page('/clubs/photo')).includes('已加入社团'));
  await activityClient.refreshActivities();
  await activityClient.registerActivity(40);
  assert((await page('/activities/40')).includes('取消报名'));
  assert.equal(activityClient.myRegistrations.value.length, 1);
  assert((await page('/me?tab=registrations')).includes('数据库摄影活动'), 'my signup link opens the registration tab');
  await activityClient.cancelActivityRegistration(40);
  assert.equal(activityClient.myRegistrations.value[0].status, 'CANCELLED');
  assert.equal(activityClient.activityState.activities[0].enrolled, 0);
  await activityClient.registerActivity(40);
  const notificationClient = await server.ssrLoadModule('/src/community/notifications.ts');
  await notificationClient.refreshNotifications();
  assert.equal(notificationClient.unreadNotices.value, 4);
  const messages = await page('/messages');
  assert(messages.includes('活动报名成功') && messages.includes('入社申请已通过'));
  assert(messages.includes('数据库通知内容') && !messages.includes('重要的消息'), 'live messages do not use prototype notices');
  assert(messages.includes('community-message-row') && messages.includes('全部已读'));
  assert(messages.includes('消息中心，4 条未读消息'), 'bell label uses the actual account unread count');
  notificationWriteFailure = true;
  assert.equal(await notificationClient.readNotice(4), false);
  assert.equal(notificationClient.unreadNotices.value, 4, 'failed read is not optimistically marked as success');
  assert(notificationClient.notificationState.writeError.includes('消息写入失败'));
  notificationWriteFailure = false;
  await notificationClient.readNotice(4);
  assert.equal(notificationClient.unreadNotices.value, 3);
  await notificationClient.readAllNotices();
  assert.equal(notificationClient.unreadNotices.value, 0);
  let releaseInbox;
  delayInbox = new Promise(resolve => { releaseInbox = resolve; });
  const lateResponse = notificationClient.refreshNotifications();
  user = manager; business.user = manager;
  await notificationClient.refreshNotifications();
  releaseInbox(); await lateResponse;
  assert.equal(notificationClient.myNotices.value.length, 0, 'a late previous-account response cannot overwrite the new inbox');
  user = student; business.user = student; await notificationClient.refreshNotifications();
  business.user = null;
  assert.equal(activityClient.myRegistrations.value.length, 0, 'old account registrations are hidden immediately');
  assert.equal(notificationClient.myNotices.value.length, 0, 'old account notifications are hidden immediately');
  assert((await page('/messages')).includes('登录后查看消息'));
  business.user = student;
  failure = true;
  await client.refreshBusiness();
  assert(business.error.includes('校园服务不可用'));
  assert.equal(business.applications.length, 0, 'failure does not fall back to fake requests');
  assert((await page('/me')).includes('不会自动切换成模拟申请'));
  await activityClient.refreshActivities();
  assert.equal(activityClient.activityState.activities.length, 0, 'failure never displays mock activity data');
  assert(activityClient.activityState.error.includes('校园服务不可用'));
  await notificationClient.refreshNotifications();
  assert.equal(notificationClient.myNotices.value.length, 0, 'unavailable service never falls back to demo notices');
  assert((await page('/messages')).includes('消息暂时无法读取'));
  assert.equal(writes, 15);
  failure = false; await client.login('platform_admin', 'test');
  const platformPage = await page('/platform/banners');
  assert(platformPage.includes('新增海报') && !platformPage.includes('请使用平台管理员账号'), 'platform role has a separate homepage workspace');
  assert((await page('/me')).includes('平台管理员') && (await page('/me')).includes('首页内容管理'), 'mobile profile exposes the platform entry and correct role');
  assert(!(await page('/me')).includes('负责人工作台'), 'platform role does not imply a club manager role');
  await client.logout();
  assert((await page('/platform/banners')).includes('请使用平台管理员账号'), 'logout removes the platform workspace');
  const newAccount = { username: 'profile_test', password: 'TestPassword123!', name: '资料测试学生', major: '软件工程' };
  const created = await client.register(newAccount);
  assert.equal(created.role, 'STUDENT');
  assert.equal(business.user, null, 'registration does not pretend the user is logged in');
  await assert.rejects(client.register(newAccount), /账号已存在/);
  await client.login(newAccount.username, newAccount.password);
  const updated = { name: '已更新的学生', major: '数字媒体', interests: ['摄影', '户外'], availableTime: '周三晚上', interestDescription: '喜欢用手机记录校园生活，想学拍照和剪视频' };
  await client.saveProfile(updated);
  assert.equal(business.user.name, updated.name, 'header name updates without re-login');
  const ProfileEditor = (await server.ssrLoadModule('/src/community/CommunityProfileEditor.vue')).default;
  const editor = await renderToString(createSSRApp(ProfileEditor, { profile: business.profile }));
  assert(editor.includes('profile-name') && editor.includes('profile-major') && editor.includes('profile-time'));
  assert(editor.includes('保存资料') && editor.includes('周三晚上') && editor.includes('aria-pressed="true"'));
  assert(editor.includes('readonly') && editor.includes('maxlength="200"'), 'editor preserves immutable account and time limits');
  assert(editor.includes('profile-interest-description') && editor.includes('maxlength="1000"') && editor.includes(updated.interestDescription), 'free-text interests use a labeled bounded textarea');
  const me = await page('/me');
  assert(me.includes('编辑资料') && me.includes('周三晚上') && me.includes('摄影'));
  const assistant = await page('/assistant');
  assert(assistant.includes('辅助标签：摄影、户外') && assistant.includes(updated.interestDescription));
  assert(assistant.includes('按我的兴趣推荐') && !assistant.includes('共同兴趣：'), 'tag matches are not shown as semantic recommendations');
  const recommendationClient = await server.ssrLoadModule('/src/community/recommendations.ts');
  recommendationFailure = true;
  await recommendationClient.loadRecommendations();
  assert.equal(recommendationClient.recommendationState.items.length, 0, 'unconfigured model never falls back to mock clubs');
  assert((await page('/assistant')).includes('尚未配置 Embedding 模型'));
  recommendationFailure = false;
  await recommendationClient.loadRecommendations();
  assert((await page('/assistant')).includes('摄影入门与校园采风') && (await page('/assistant')).includes('不是录取概率'));
  await client.saveProfile({ ...updated, interestDescription: '想学习编程' });
  assert.equal(recommendationClient.recommendationState.items.length, 0, 'editing interests clears obsolete results');
  let releaseRecommendation;
  delayRecommendation = new Promise(resolve => { releaseRecommendation = resolve; });
  const lateRecommendation = recommendationClient.loadRecommendations();
  await new Promise(resolve => setTimeout(resolve, 0));
  await client.logout(); await client.login('photo_manager', 'test');
  releaseRecommendation(); await lateRecommendation;
  assert.equal(recommendationClient.recommendationState.items.length, 0, 'previous-account recommendation response is ignored');
  await client.logout(); await client.login(newAccount.username, newAccount.password); await client.saveProfile(updated);
  profileWriteFailure = true;
  await assert.rejects(client.saveProfile({ ...updated, name: '未成功保存' }), /资料保存失败/);
  assert.equal(business.profile.name, updated.name, 'failed save leaves confirmed data unchanged');
  profileWriteFailure = false;
  await client.refreshBusiness();
  assert.equal(business.profile.availableTime, '周三晚上');
  await client.logout();
  assert.equal(business.profile, null);
  assert(!(await page('/assistant')).includes('共同兴趣：'), 'logout never uses local demo interests in live mode');
  await client.login(newAccount.username, newAccount.password);
  assert.deepEqual(business.profile.interests, ['摄影', '户外'], 'profile survives a fresh session');
  let releaseProfile;
  delayProfileWrite = new Promise(resolve => { releaseProfile = resolve; });
  const lateSave = client.saveProfile(updated);
  await client.logout(); await client.login('photo_manager', 'test');
  releaseProfile(); await assert.rejects(lateSave, /账号状态已变化/);
  assert.equal(business.profile.id, manager.id, 'late save cannot overwrite another account profile');
  await client.logout();
  console.log('PASS: live frontend data, CSRF, identity isolation, recruitment, activities, notifications, registration/profile persistence, failed save and late-response isolation. Mock HTTP + SSR, not a real browser.');
} finally { await server.close(); }
