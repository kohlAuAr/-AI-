import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp, nextTick } from 'vue';
import { renderToString } from '@vue/server-renderer';

const storage = new Map();
Object.defineProperty(globalThis, 'crypto', { value: {}, configurable: true });
globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
let assertions = 0;
function check(condition, label) { assert.ok(condition, label); assertions++; }
try {
  const store = await server.ssrLoadModule('/src/prototype/store.ts');
  const { state } = store;
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
    ['/', '你的热爱'], ['/clubs', '找到同频'], ['/clubs/photo', '光影摄影社'],
    ['/clubs/read', '招新已结束'], ['/clubs/not-found', '没有找到'],
    ['/recruitment', '新故事'], ['/activities', '课余时间'],
    ['/activities/campus-photo', '报名参加'], ['/activities/green-campus', '名额已满'],
    ['/activities/not-found', '没有找到'], ['/me', '你好'], ['/messages', '重要的消息'],
    ['/manage', '工作概览'], ['/manage/recruitment', '查看并审核'],
    ['/manage/members', '新增成员'], ['/manage/activities', '新建活动'],
    ['/manage/finance', '计划预算合计'], ['/assistant', '共同兴趣'], ['/guide', '原型'],
    ['/chat', '资料问答'], ['/knowledge', '上传资料'], ['/system', '刷新状态']
  ];
  for (const [path, expected] of pages) {
    await router.push(path); await router.isReady();
    const html = await renderToString(createSSRApp(App).use(router));
    check(html.includes(expected), `${path}: expected content ${expected}`);
    if (!router.currentRoute.value.path.startsWith('/system')) check(html.includes('非真实报名'), `${path}: prototype disclosure`);
    if (path === '/assistant') {
      const mobileNav = html.match(/<nav class="p-mobile-nav"[\s\S]*?<\/nav>/)?.[0] ?? '';
      check((mobileNav.match(/<a /g) ?? []).length === 6, 'six mobile navigation items');
      check(/<a [^>]*href="\/assistant"[^>]*class="[^"]*selected[^"]*"/.test(mobileNav), 'assistant navigation is selected');
      check(mobileNav.indexOf('活动</span>') < mobileNav.indexOf('助手</span>') && mobileNav.indexOf('助手</span>') < mobileNav.indexOf('我的</span>'), 'assistant appears between activities and profile');
      const desktopNav = html.match(/<nav class="p-desktop-nav"[\s\S]*?<\/nav>/)?.[0] ?? '';
      check(desktopNav.includes('href="/assistant"') && desktopNav.includes('助手</a>'), 'desktop navigation includes assistant');
    }
    console.log(`PAGE PASS ${path}`);
  }
  console.log(`PASS ${assertions} assertions; ${pages.length} rendered routes. No real browser, backend or model required.`);
} finally {
  await server.close();
}
