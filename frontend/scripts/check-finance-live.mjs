import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';
import { createSessionClient } from '../../scripts/http-session.mjs';

// Runs the actual frontend finance client against the local business service, not browser clicks.
const base = process.env.SMOKE_BASE_URL || 'http://127.0.0.1:5178';
const stage = process.argv[2] || 'flow';
assert(['flow', 'restored'].includes(stage));
const fixturePath = new URL('../../.run/finance-page-fixture.json', import.meta.url);
const client = createSessionClient(base);
const realFetch = globalThis.fetch;
globalThis.fetch = (url, options) => String(url).startsWith('/api/') ? client.request(String(url), options) : realFetch(url, options);
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
const write = (method, body) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
try {
  const identity = await server.ssrLoadModule('/src/prototype/business.ts');
  const finance = await server.ssrLoadModule('/src/community/finance.ts');
  await identity.login('photo_manager', 'CampusDemo123!');
  const club = identity.business.clubs.find(c => c.id === 'photo');
  assert(club?.backendId);
  identity.business.managedClubId = club.backendId;
  await finance.refreshFinance();
  assert(finance.financeReport.value);
  let fixture;
  if (stage === 'flow') {
    const date = new Date(Date.now() + 7 * 86400000);
    const localTime = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T14:00:00`;
    const deadline = new Date(date.getTime() - 86400000);
    const activity = await client.json(`/api/manage/clubs/${club.backendId}/activities`, write('POST', {
      title: `经费页面联调（虚构）${Date.now().toString(36)}`, description: '仅用于预算和支出页面接口验收，不是真实活动，不发布。', location: '模拟活动室',
      startTime: localTime(date), registrationDeadline: localTime(deadline), capacity: 5,
    }));
    await finance.refreshFinance();
    assert(await finance.saveBudget(activity.id, '500.00'));
    const input = { requestId: randomUUID(), amount: '120.50', description: '虚构材料费（经费页面验收）', occurredOn: finance.localDate() };
    assert(await finance.addExpense(activity.id, input));
    assert(await finance.addExpense(activity.id, input));
    let row = finance.financeReport.value.activities.find(a => a.activityId === activity.id);
    assert.equal(row.expenses.length, 1); assert.equal(row.remaining, 379.5);
    const first = row.expenses[0];
    assert(await finance.addExpense(activity.id, { ...input, requestId: randomUUID(), amount: '400.25', description: '虚构补记（经费页面验收）' }));
    row = finance.financeReport.value.activities.find(a => a.activityId === activity.id);
    assert.equal(row.spent, 520.75); assert.equal(row.remaining, -20.75); assert(row.overBudget);
    assert(await finance.voidExpense(first.id, '虚构验收：误记作废，原记录保留'));
    row = finance.financeReport.value.activities.find(a => a.activityId === activity.id);
    assert.equal(row.spent, 400.25); assert.equal(row.remaining, 99.75); assert(!row.overBudget);
    assert.equal(row.expenses.length, 2); assert(row.expenses.some(e => e.id === first.id && e.status === 'VOID' && e.voidedAt));
    fixture = { checkedAt: new Date().toISOString(), clubId: club.backendId, activityId: activity.id, row };
    await mkdir(new URL('../../.run/', import.meta.url), { recursive: true });
    await writeFile(fixturePath, JSON.stringify(fixture, null, 2));
  } else {
    fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
    assert.deepEqual(finance.financeReport.value.activities.find(a => a.activityId === fixture.activityId), fixture.row);
  }
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  await router.push('/manage/finance'); await router.isReady();
  const html = await renderToString(createSSRApp(App).use(router));
  assert(html.includes(fixture.row.title) && html.includes('¥99.75') && html.includes('录入支出'));
  const outsider = createSessionClient(base);
  await outsider.json('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username: 'code_manager', password: 'CampusDemo123!' }) });
  await outsider.json(`/api/manage/activities/${fixture.activityId}/finance`, {}, 403);
  await outsider.json(`/api/manage/activities/${fixture.activityId}/budget`, write('PUT', { amount: '1.00' }), 403);
  await identity.logout(); await identity.login('student', 'CampusDemo123!'); await finance.refreshFinance();
  assert.equal(finance.financeReport.value, null);
  await client.json(`/api/manage/activities/${fixture.activityId}/finance`, {}, 403);
  assert.equal(await finance.saveBudget(fixture.activityId, '1.00'), false);
  const studentHtml = await renderToString(createSSRApp(App).use(router));
  assert(studentHtml.includes('请使用社团负责人账号') && !studentHtml.includes(fixture.row.title));
  console.log(`PASS ${stage}: actual Vue finance client + HTTP + SSR; activity ${fixture.activityId}; budget 500, spent 400.25, remaining 99.75, retained void audit and manager/student isolation. Not real browser/mobile acceptance.`);
} finally { await server.close(); globalThis.fetch = realFetch; }
