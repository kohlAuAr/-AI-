import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createSSRApp } from 'vue';
import { renderToString } from '@vue/server-renderer';

globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
const manager = { id: 2, username: 'manager', name: '测试负责人', role: 'MANAGER', managedClubIds: [10, 20] };
const student = { id: 1, name: '测试学生', role: 'STUDENT', managedClubIds: [] };
let business, failRead = false, failWrite = false, lostResponse = false, delayRead, delayWrite;
let calls = [], nextId = 1;
const reports = new Map([10, 20].map(id => [id, { clubId: id, budgetTotal: 0, spentTotal: 0, unbudgetedActivities: 1, activities: [{ activityId: id * 10, title: `测试活动 ${id}`, budget: null, spent: 0, remaining: null, overBudget: false, expenses: [] }] }]));
function recalculate(report) {
  for (const activity of report.activities) {
    activity.spent = Math.round(activity.expenses.filter(e => e.status === 'ACTIVE').reduce((sum, e) => sum + e.amount * 100, 0)) / 100;
    activity.remaining = activity.budget === null ? null : Math.round((activity.budget - activity.spent) * 100) / 100;
    activity.overBudget = activity.budget !== null && activity.spent > activity.budget;
  }
  report.budgetTotal = report.activities.reduce((sum, a) => sum + (a.budget ?? 0), 0);
  report.spentTotal = report.activities.reduce((sum, a) => sum + a.spent, 0);
  report.unbudgetedActivities = report.activities.filter(a => a.budget === null).length;
}
globalThis.fetch = async (url, options = {}) => {
  const path = String(url), method = options.method || 'GET';
  if (path === '/api/auth/session') return Response.json({ user: business.user, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'finance-csrf' });
  calls.push({ path, method, body: options.body });
  if (method === 'GET') {
    const clubId = Number(path.match(/\/clubs\/(\d+)\/finance$/)?.[1]);
    assert(clubId, `Unexpected read ${path}`);
    const response = failRead ? Response.json({ detail: '台账读取失败' }, { status: 503 }) : Response.json(reports.get(clubId));
    if (delayRead) { const pause = delayRead; delayRead = undefined; await pause; }
    return response;
  }
  assert.equal(new Headers(options.headers).get('X-CSRF-TOKEN'), 'finance-csrf');
  if (failWrite) return Response.json({ detail: '经费保存失败' }, { status: 503 });
  const body = JSON.parse(options.body);
  let response;
  if (path.endsWith('/void')) {
    const id = Number(path.split('/').at(-2));
    const expense = [...reports.values()].flatMap(r => r.activities.flatMap(a => a.expenses)).find(e => e.id === id);
    expense.status = 'VOID'; expense.voidReason = body.reason; expense.voidedBy = manager.id; expense.voidedAt = '2026-10-09T04:00:00Z';
    response = Response.json(expense);
  } else {
    const activityId = Number(path.match(/\/activities\/(\d+)\//)?.[1]);
    const activity = [...reports.values()].flatMap(r => r.activities).find(a => a.activityId === activityId);
    assert(activity);
    if (method === 'PUT') { activity.budget = Number(body.amount); response = Response.json(activity); }
    else {
      let expense = activity.expenses.find(e => e.requestId === body.requestId);
      if (!expense) {
        expense = { ...body, id: nextId++, activityId, amount: Number(body.amount), status: 'ACTIVE', createdBy: manager.id, createdAt: '2026-10-09T03:00:00Z', voidReason: null, voidedBy: null, voidedAt: null };
        activity.expenses.unshift(expense);
      } else assert.deepEqual([expense.amount, expense.description, expense.occurredOn], [Number(body.amount), body.description, body.occurredOn], 'idempotent retries preserve content');
      response = Response.json(expense);
    }
  }
  for (const report of reports.values()) recalculate(report);
  if (delayWrite) { const pause = delayWrite; delayWrite = undefined; await pause; }
  if (lostResponse) { lostResponse = false; throw new Error('响应丢失，请核对后重试'); }
  return response;
};
const server = await createServer({ configFile: false, plugins: [(await import('@vitejs/plugin-vue')).default()], server: { middlewareMode: true }, appType: 'custom' });
try {
  ({ business } = await server.ssrLoadModule('/src/prototype/business.ts'));
  const finance = await server.ssrLoadModule('/src/community/finance.ts');
  const { financeState, financeReport, refreshFinance, saveBudget, addExpense, voidExpense, validAmount, money } = finance;
  const router = (await server.ssrLoadModule('/src/router.ts')).default;
  const App = (await server.ssrLoadModule('/src/App.vue')).default;
  async function page() { await router.push('/manage/finance'); await router.isReady(); return renderToString(createSSRApp(App).use(router)); }
  for (const user of [null, student, { id: 3, name: '测试管理员', role: 'PLATFORM_ADMIN', managedClubIds: [] }]) {
    business.user = user; business.managedClubId = 10;
    await refreshFinance();
    assert.equal(financeReport.value, null);
    assert.equal(await saveBudget(100, '500'), false);
    assert((await page()).includes('请使用社团负责人账号'));
  }
  assert.equal(calls.length, 0, 'unauthorized roles never fetch financial records');
  business.user = manager; business.managedClubId = 10;
  business.clubs = [{ id: 'photo', backendId: 10, name: '测试摄影社' }, { id: 'code', backendId: 20, name: '测试编程社' }];
  await refreshFinance();
  assert.equal(financeReport.value.activities[0].remaining, null);
  assert.equal(money(null), '未设置'); assert.equal(money(-0.1), '¥-0.10');
  assert(validAmount('0')); assert(!validAmount('0', true)); assert(validAmount('0.01', true));
  for (const value of ['-1', '1.001', '1e3', 'NaN', '', '100000000.00']) assert(!validAmount(value));
  let html = await page();
  assert(html.includes('未设置') && html.includes('不是社团账户余额'));
  assert(html.includes('录入支出') && html.includes('设置预算'));
  assert.equal(await saveBudget(999, '500'), false, 'unknown activity is not submitted');
  assert(await saveBudget(100, '500.00'));
  assert.equal(financeReport.value.activities[0].remaining, 500);
  const input = { requestId: '11111111-1111-4111-8111-111111111111', amount: '120.50', description: '材料费', occurredOn: '2026-10-09' };
  lostResponse = true;
  assert.equal(await addExpense(100, input), false);
  assert(financeState.writeError.includes('响应丢失'));
  assert(await addExpense(100, input));
  assert.equal(financeReport.value.activities[0].expenses.length, 1, 'retry does not duplicate a committed expense');
  const expenseCalls = calls.filter(c => c.path.endsWith('/expenses'));
  assert.equal(expenseCalls[0].body, expenseCalls[1].body, 'retry sends identical UUID and body');
  let releaseWrite;
  delayWrite = new Promise(resolve => { releaseWrite = resolve; });
  const pending = addExpense(100, { ...input, requestId: '22222222-2222-4222-8222-222222222222', amount: '400.25', description: '场地材料' });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(await addExpense(100, input), false, 'double submit is blocked while saving');
  releaseWrite(); assert(await pending);
  assert.equal(financeReport.value.activities[0].spent, 520.75);
  assert.equal(financeReport.value.activities[0].remaining, -20.75);
  assert(financeReport.value.activities[0].overBudget);
  assert((await page()).includes('已超预算'));
  const firstExpense = financeReport.value.activities[0].expenses.find(e => e.requestId === input.requestId);
  assert(await voidExpense(firstExpense.id, '误记，保留原记录'));
  assert.equal(financeReport.value.activities[0].spent, 400.25);
  assert.equal(financeReport.value.activities[0].remaining, 99.75);
  assert.equal(financeReport.value.activities[0].expenses.find(e => e.id === firstExpense.id).voidReason, '误记，保留原记录');
  assert.equal(await voidExpense(firstExpense.id, '重复作废'), false);
  failWrite = true;
  assert.equal(await saveBudget(100, '100'), false);
  assert.equal(financeReport.value.activities[0].budget, 500, 'failed save does not replace confirmed state');
  failWrite = false; failRead = true;
  assert(await saveBudget(100, '600'), 'saved write is not misreported as failed when refresh fails');
  assert.equal(financeReport.value, null);
  assert(financeState.notice.includes('已保存') && financeState.error.includes('读取失败'));
  failRead = false; await refreshFinance();
  assert.equal(financeReport.value.activities[0].budget, 600);
  let releaseRead;
  delayRead = new Promise(resolve => { releaseRead = resolve; });
  const lateRead = refreshFinance();
  business.managedClubId = 20; await refreshFinance();
  releaseRead(); await lateRead;
  assert.equal(financeReport.value.clubId, 20, 'late response cannot overwrite a different club');
  delayWrite = new Promise(resolve => { releaseWrite = resolve; });
  const lateWrite = saveBudget(200, '90');
  await new Promise(resolve => setTimeout(resolve, 0));
  business.user = student;
  assert.equal(financeReport.value, null, 'switching account clears financial records synchronously');
  releaseWrite(); assert.equal(await lateWrite, false);
  assert.equal(financeState.notice, ''); assert.equal(financeState.report, null);
  assert(!(await page()).includes('材料费'));
  business.user = manager; business.managedClubId = 999;
  const count = calls.length; await refreshFinance(); assert.equal(calls.length, count, 'an unowned club cannot be read');
  console.log('PASS finance: CSRF, manager scope, amounts, null budgets, overspend, void audit, identical retry, double submit, failed refresh and late account/club isolation. Mock HTTP + SSR, not browser interaction.');
} finally { await server.close(); }
