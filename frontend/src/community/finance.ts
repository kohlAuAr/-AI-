import { computed, reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';

export interface Expense { id: number; activityId: number; requestId: string; amount: number; description: string; occurredOn: string; createdBy: number; createdAt: string; status: 'ACTIVE' | 'VOID'; voidReason: string | null; voidedAt: string | null; voidedBy: number | null }
export interface ActivityFinance { activityId: number; title: string; budget: number | null; spent: number; remaining: number | null; overBudget: boolean; expenses: Expense[] }
export interface ClubFinance { clubId: number; budgetTotal: number; spentTotal: number; unbudgetedActivities: number; activities: ActivityFinance[] }
export interface ExpenseInput { requestId: string; amount: string; description: string; occurredOn: string }
export const financeState = reactive({ loading: false, saving: false, error: '', writeError: '', notice: '', context: '', report: null as ClubFinance | null });
let loadVersion = 0, writeVersion = 0;
function owner() {
  const user = business.user, clubId = business.managedClubId;
  return businessMode && user?.role === 'MANAGER' && clubId !== null && user.managedClubIds.includes(clubId) ? `${user.id}:${clubId}` : '';
}
export const financeReport = computed(() => owner() && owner() === financeState.context ? financeState.report : null);
watch(owner, () => {
  ++loadVersion; ++writeVersion; financeState.context = '';
  financeState.report = null; financeState.loading = false; financeState.saving = false;
  financeState.error = ''; financeState.writeError = ''; financeState.notice = '';
}, { flush: 'sync' });

export async function refreshFinance() {
  const context = owner(), clubId = business.managedClubId, ticket = ++loadVersion;
  financeState.report = null; financeState.context = ''; financeState.error = '';
  if (!context) { financeState.loading = false; return; }
  financeState.loading = true;
  try {
    const report = await request<ClubFinance>(`/manage/clubs/${clubId}/finance`);
    if (ticket !== loadVersion || context !== owner()) return;
    financeState.report = report; financeState.context = context;
  } catch (error) {
    if (ticket === loadVersion && context === owner()) financeState.error = errorMessage(error);
  } finally { if (ticket === loadVersion) financeState.loading = false; }
}

async function write(path: string, method: 'POST' | 'PUT', body: unknown) {
  if (financeState.saving) return false;
  const context = owner();
  if (!context || !financeReport.value) { financeState.writeError = '请重新加载你负责社团的台账'; return false; }
  const ticket = ++writeVersion;
  financeState.saving = true; financeState.writeError = ''; financeState.notice = '';
  try {
    await request(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (ticket !== writeVersion || context !== owner()) return false;
    await refreshFinance();
    if (ticket !== writeVersion || context !== owner()) return false;
    financeState.notice = financeState.error ? '已保存到数据库，但台账刷新失败，请点击刷新。不要重复新建同一笔支出。' : '已保存到校园数据库';
    return true;
  } catch (error) {
    if (ticket === writeVersion && context === owner()) financeState.writeError = errorMessage(error);
    return false;
  } finally { if (ticket === writeVersion) financeState.saving = false; }
}
function ownsActivity(id: number) { return financeReport.value?.activities.some(activity => activity.activityId === id); }
export function saveBudget(id: number, amount: string) {
  return ownsActivity(id) ? write(`/manage/activities/${id}/budget`, 'PUT', { amount }) : Promise.resolve(false);
}
export function addExpense(id: number, input: ExpenseInput) {
  return ownsActivity(id) ? write(`/manage/activities/${id}/expenses`, 'POST', input) : Promise.resolve(false);
}
export function voidExpense(id: number, reason: string) {
  return financeReport.value?.activities.some(activity => activity.expenses.some(expense => expense.id === id && expense.status === 'ACTIVE'))
    ? write(`/manage/expenses/${id}/void`, 'POST', { reason }) : Promise.resolve(false);
}
export function money(value: number | null) { return value === null ? '未设置' : `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`; }
export function validAmount(value: string, positive = false) {
  return /^\d{1,8}(\.\d{1,2})?$/.test(value.trim()) && (!positive || /[1-9]/.test(value));
}
export function localDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
