import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp, nextTick } from 'vue';
import { renderToString } from '@vue/server-renderer';

const storage = new Map();
Object.defineProperty(globalThis, 'crypto', { value: {}, configurable: true });
globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const server = await createServer({ configFile: false, define: { 'import.meta.env.VITE_BUSINESS_API': JSON.stringify('false') }, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
let assertions = 0;
function check(condition, label) { assert.ok(condition, label); assertions++; }
try {
  const store = await server.ssrLoadModule('/src/prototype/store.ts');
  const { state } = store;
  const activities = await server.ssrLoadModule('/src/community/activities.ts');
  let businessFetches = 0;
  globalThis.fetch = async () => { businessFetches++; throw new Error('Pure prototype must not fetch business APIs'); };
  await activities.refreshActivities();
  check(businessFetches === 0, 'pure prototype activity store never calls business APIs');
  check(activities.activityState.activities.length === 0, 'formal activity records stay separate from prototype data');
  const notifications = await server.ssrLoadModule('/src/community/notifications.ts');
  await notifications.refreshNotifications();
  check(businessFetches === 0, 'pure prototype notification store never calls business APIs');
  check(notifications.myNotices.value.length === 0, 'formal notices stay separate from browser mock notices');
  check(store.prototypeId().startsWith('demo-'), 'LAN HTTP ID fallback');
  check(!store.apply('photo', '  '), 'blank application rejected');
  check(store.apply('photo', '希望学习摄影'), 'application submitted');
  check(!store.apply('photo', '重复申请'), 'duplicate application rejected');
  const request = state.applications.find(a => a.mine && a.clubId === 'photo');
  check(request.status === 'pending', 'application pending');
  store.review(request.id, true, '欢迎加入');
  check(request.status === 'approved', 'membership approved');
  check(state.notices.some(n => n.title.includes('已通过')), 'approval notice created');
  check(!store.apply('read', '读书'), 'closed recruitment rejected');
  check(store.register('campus-photo'), 'registration succeeds');
  check(!store.register('campus-photo'), 'duplicate registration rejected');
  check(!store.register('green-campus'), 'full event rejected');
  check(state.activities.find(a => a.id === 'campus-photo').enrolled === 19, 'capacity incremented');
  store.cancelRegistration('campus-photo');
  check(state.activities.find(a => a.id === 'campus-photo').enrolled === 18, 'capacity restored');
  store.cancelRegistration('campus-photo');
  check(state.activities.find(a => a.id === 'campus-photo').enrolled === 18, 'repeat cancellation is a no-op');
  store.toggleFavorite('photo'); check(state.favorites.includes('photo'), 'favorite added');
  store.toggleFavorite('photo'); check(!state.favorites.includes('photo'), 'favorite removed');
  state.managedClub = 'code';
  const foreignRequest = state.applications.find(a => a.clubId === 'photo' && !a.mine);
  store.review(foreignRequest.id, true, 'not permitted');
  check(foreignRequest.status === 'pending', 'cannot review another managed club');
  state.activities.push({ ...state.activities[0], id: 'draft-test', status: 'draft' });
  check(!store.register('draft-test'), 'draft activity cannot be registered');
  state.activities.push({ ...state.activities[0], id: 'past-test', date: '2020-01-01', time: '09:00' });
  check(!store.register('past-test'), 'past activity cannot be registered');
  await nextTick();
  const persisted = JSON.parse(storage.get('campus-prototype-v1'));
  check(persisted.applications.some(a => a.mine && a.status === 'approved'), 'approved state persisted');
  store.resetPrototype(); await nextTick();
  check(!state.applications.some(a => a.mine) && state.registrations.length === 0, 'reset restores demo');
  check(JSON.parse(storage.get('campus-prototype-v1')).activities.length === 4, 'reset persisted');

  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  const pages = [
    ['/', '正在招新的社团'], ['/clubs', '全部社团'], ['/clubs/photo', '光影摄影社'],
    ['/clubs/read', '招新已结束'], ['/clubs/not-found', '没有找到'],
    ['/recruitment', '加入条件'], ['/activities', '查看社团近期活动'],
    ['/activities/campus-photo', '报名参加'], ['/activities/green-campus', '名额已满'],
    ['/activities/not-found', '没有找到'], ['/me', '你好'], ['/messages', '消息中心'], ['/login', '纯前端原型没有登录服务'],
    ['/manage', '工作概览'], ['/manage/recruitment', '查看并审核'],
    ['/manage/members', '新增成员'], ['/manage/activities', '新建活动'],
    ['/manage/finance', '计划预算合计'], ['/assistant', '共同兴趣'], ['/guide', '原型'],
    ['/chat', '资料问答'], ['/knowledge', '上传资料'], ['/system', '刷新状态'],
    ['/system/clubs', '社团接口'], ['/system/activities', '活动接口'], ['/roadmap', '后续开发']
  ];
  const covered = new Set();
  for (const [path, expected] of pages) {
    await router.push(path); await router.isReady();
    const html = await renderToString(createSSRApp(App).use(router));
    check(html.includes(expected), `${path}: expected content ${expected}`);
    router.currentRoute.value.matched.forEach(record => covered.add(record.path));
    if (path === '/clubs') {
      check(html.includes('community-search'), 'community directory has labeled search');
      check(html.includes('aria-pressed="true"'), 'directory exposes selected category');
      check(html.includes('周三') && html.includes('学生中心'), 'directory includes club schedule and place');
      check(html.includes('收藏仅保存在当前浏览器'), 'directory discloses local favorites');
      check(html.includes('community-bottom-nav') && !html.includes('p-header'), 'community uses a new navigation shell');
      check(!html.includes('找到同频') && !html.includes('directory-hero'), 'rejected marketing layout is absent');
      check((html.match(/class="community-club-row"/g) ?? []).length === 6, 'community displays six compact list rows');
    }
    check(html.includes('community-app') && !html.includes('p-header') && !html.includes('app-shell'), `${path}: consistent community shell`);
    check(!html.includes('p-kicker') && !html.includes('p-hero') && !html.includes('p-detail-hero') && !html.includes('eyebrow'), `${path}: no old marketing headings`);
    check(html.includes('community-bottom-nav'), `${path}: persistent mobile navigation`);
    if (path.startsWith('/manage')) check(html.includes('aria-label="负责人导航"') && html.includes('经费台账'), `${path}: all manager destinations stay reachable`);
    if (router.currentRoute.value.path.startsWith('/system')) check(html.includes('aria-label="联调导航"') && html.includes('尚未完成私有资料权限隔离'), `${path}: development navigation and privacy boundary`);
    if (path === '/recruitment') {
      check(html.includes('community-search'), 'recruitment keeps labeled search');
      check(html.includes('community-recruit-requirements'), 'recruitment shows joining requirements');
      check(!html.includes('纸间读书会'), 'closed club is excluded from recruitment');
    }
    if (path === '/activities') check(html.includes('community-search') && html.includes('只看有名额'), 'prototype activity filtering preserved in new list layout');
    if (!router.currentRoute.value.path.startsWith('/system')) check(html.includes('非真实报名'), `${path}: prototype disclosure`);
    if (path === '/assistant') {
      const mobileNav = html.match(/<nav class="community-bottom-nav"[\s\S]*?<\/nav>/)?.[0] ?? '';
      check((mobileNav.match(/<a /g) ?? []).length === 6, 'six mobile navigation items');
      check(/<a [^>]*href="\/assistant"[^>]*class="[^"]*current[^"]*"/.test(mobileNav), 'assistant navigation is selected');
      check(mobileNav.indexOf('活动</span>') < mobileNav.indexOf('助手</span>') && mobileNav.indexOf('助手</span>') < mobileNav.indexOf('我的</span>'), 'assistant appears between activities and profile');
      const desktopNav = html.match(/<nav aria-label="学生端导航"[\s\S]*?<\/nav>/)?.[0] ?? '';
      check(desktopNav.includes('href="/assistant"') && desktopNav.includes('助手</a>'), 'desktop navigation includes assistant');
    }
    console.log(`PAGE PASS ${path}`);
  }
  for (const record of router.getRoutes().filter(record => record.components)) check(covered.has(record.path), `all page route patterns covered: ${record.path}`);
  console.log(`PASS ${assertions} assertions; ${pages.length} rendered routes. No real browser, backend or model required.`);
} finally {
  await server.close();
}
