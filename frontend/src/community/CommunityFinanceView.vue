<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { business, managedClubs, refreshBusiness } from '../prototype/business';
import { displayTime } from '../api';
import { financeState, financeReport, refreshFinance, saveBudget, addExpense, voidExpense, money, validAmount, localDate, type Expense, type ExpenseInput } from './finance';
import CommunityDialog from './CommunityDialog.vue';

const club = computed(() => managedClubs.value.find(item => item.backendId === business.managedClubId));
const selectedId = ref<number>();
const selected = computed(() => financeReport.value?.activities.find(item => item.activityId === selectedId.value));
const modal = ref<'budget' | 'expense' | 'void' | ''>('');
const amount = ref(''), description = ref(''), occurredOn = ref(localDate()), reason = ref('');
const voiding = ref<Expense>(), pendingExpense = ref<ExpenseInput>();
const formError = ref(''), errorElement = ref<HTMLElement>(), ledger = ref<HTMLElement>();
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], () => {
  modal.value = ''; selectedId.value = undefined; pendingExpense.value = undefined;
  voiding.value = undefined; amount.value = ''; description.value = ''; reason.value = ''; formError.value = '';
  refreshFinance();
}, { flush: 'sync' });
onMounted(refreshFinance);
async function viewLedger(id: number) { selectedId.value = id; await nextTick(); ledger.value?.focus(); }
function open(kind: 'budget' | 'expense', id: number) {
  selectedId.value = id; formError.value = ''; financeState.writeError = ''; financeState.notice = '';
  amount.value = kind === 'budget' && selected.value?.budget !== null ? String(selected.value?.budget ?? '') : '';
  description.value = ''; occurredOn.value = localDate(); pendingExpense.value = undefined; modal.value = kind;
}
function askVoid(expense: Expense) { voiding.value = expense; reason.value = ''; formError.value = ''; financeState.writeError = ''; modal.value = 'void'; }
async function checkPendingExpense() {
  if (!pendingExpense.value) return;
  await refreshFinance();
  const recorded = selected.value?.expenses.find(expense => expense.requestId === pendingExpense.value?.requestId);
  if (recorded) { financeState.notice = '已在台账中找到这笔记录，无需再次录入。'; modal.value = ''; }
  else if (financeState.error) formError.value = financeState.error;
}
async function fail(message: string) { formError.value = message; await nextTick(); errorElement.value?.focus(); }
async function save() {
  if (financeState.saving || !selected.value) return;
  formError.value = '';
  if (modal.value === 'void') {
    if (!reason.value.trim()) { await fail('请填写作废原因，原始记录将保留。'); return; }
    if (voiding.value && await voidExpense(voiding.value.id, reason.value.trim())) modal.value = '';
  } else {
    if (!validAmount(amount.value, modal.value === 'expense')) { await fail(modal.value === 'budget' ? '预算须为非负金额，最多 8 位整数、2 位小数，例如 500.00。' : '支出须大于 0，最多 8 位整数、2 位小数，例如 120.50。'); return; }
    if (modal.value === 'budget') {
      if (await saveBudget(selected.value.activityId, amount.value.trim())) modal.value = '';
    } else {
      if (!description.value.trim() || !/^\d{4}-\d{2}-\d{2}$/.test(occurredOn.value) || occurredOn.value > localDate()) { await fail('请填写支出说明和发生日期，日期不能晚于今天。'); return; }
      pendingExpense.value ??= { requestId: crypto.randomUUID(), amount: amount.value.trim(), description: description.value.trim(), occurredOn: occurredOn.value };
      if (await addExpense(selected.value.activityId, pendingExpense.value)) modal.value = '';
    }
  }
  if (financeState.writeError) { await nextTick(); errorElement.value?.focus(); }
}
</script>

<template>
  <div class="community-finance">
    <div class="community-page-title"><h1>经费台账</h1><RouterLink to="/manage">返回工作台</RouterLink></div>
    <div v-if="business.user?.role !== 'MANAGER'" class="community-list-message"><h2>请使用社团负责人账号</h2><p>只有所属社团负责人可以查看和维护经费。</p><RouterLink to="/login" class="community-button">前往登录</RouterLink></div>
    <template v-else>
      <div class="community-manager-toolbar"><label>管理社团<select v-model="business.managedClubId" :disabled="financeState.saving || business.loading" @change="refreshBusiness"><option v-for="item in managedClubs" :key="item.id" :value="item.backendId">{{ item.name }}</option></select></label><button class="community-text-button" :disabled="financeState.loading || financeState.saving || !club" @click="refreshFinance">{{ financeState.loading ? '读取中…' : '刷新台账' }}</button></div>
      <p v-if="business.error" class="community-error" role="alert">{{ business.error }}</p>
      <p v-if="!club && !business.loading" class="community-list-message">当前账号尚未分配管理社团。</p>
      <p v-if="financeState.notice" class="finance-notice" role="status">{{ financeState.notice }}</p>
      <p v-if="financeState.error" class="community-error" role="alert">{{ financeState.error }}。请重试刷新台账。</p>
      <p v-if="financeState.loading" class="community-list-loading" role="status">正在读取经费记录…</p>
      <template v-else-if="financeReport">
        <dl class="finance-summary"><div><dt>已设置预算合计</dt><dd>{{ money(financeReport.budgetTotal) }}</dd></div><div><dt>有效支出合计</dt><dd>{{ money(financeReport.spentTotal) }}</dd></div></dl>
        <p class="finance-help">{{ financeReport.unbudgetedActivities }} 项活动未设置预算。这里是活动记账汇总，不是社团账户余额。</p>
        <section class="finance-activities" aria-labelledby="finance-activities-title"><h2 id="finance-activities-title">{{ club?.name }}的活动台账</h2>
          <article v-for="activity in financeReport.activities" :key="activity.activityId" class="finance-activity"><h3>{{ activity.title }}</h3><dl class="finance-amounts"><div><dt>预算</dt><dd>{{ money(activity.budget) }}</dd></div><div><dt>已支出</dt><dd>{{ money(activity.spent) }}</dd></div><div><dt>剩余预算</dt><dd :class="{ 'finance-danger': activity.overBudget }">{{ money(activity.remaining) }}</dd></div></dl><p v-if="activity.overBudget" class="finance-danger">已超预算，请核对支出或调整预算。</p><div class="community-dialog-actions"><button class="community-text-button" :aria-expanded="selectedId === activity.activityId" aria-controls="finance-ledger" :disabled="financeState.saving" @click="viewLedger(activity.activityId)">查看支出明细</button><button class="community-text-button" :disabled="financeState.saving" @click="open('budget', activity.activityId)">设置预算</button><button class="community-button secondary" :disabled="financeState.saving" @click="open('expense', activity.activityId)">录入支出</button></div></article>
          <div v-if="!financeReport.activities.length" class="community-list-message"><h3>还没有可记账的活动</h3><p>先保存活动草稿，再设置预算或录入支出。</p><RouterLink to="/manage/activities" class="community-text-link">前往活动管理</RouterLink></div>
        </section>
        <section v-if="selected" id="finance-ledger" ref="ledger" class="finance-ledger" tabindex="-1" aria-labelledby="finance-ledger-title"><h2 id="finance-ledger-title">{{ selected.title }}的支出明细</h2><p class="finance-help">只统计有效支出；作废记录仍保留，不能删除。</p>
          <article v-for="expense in selected.expenses" :key="expense.id" class="finance-expense"><div class="finance-expense-heading"><h3>{{ expense.description }}</h3><strong>{{ money(expense.amount) }}</strong></div><p>{{ expense.occurredOn }} 发生 · {{ expense.status === 'VOID' ? '已作废，不计入支出' : '有效支出' }}</p><p class="finance-help">记录人 #{{ expense.createdBy }} · {{ displayTime(expense.createdAt) }}</p><p v-if="expense.status === 'VOID'" class="finance-void-reason">作废原因：{{ expense.voidReason }}<br />作废人 #{{ expense.voidedBy }} · {{ expense.voidedAt ? displayTime(expense.voidedAt) : '' }}</p><button v-else class="community-text-button finance-danger" :disabled="financeState.saving" @click="askVoid(expense)">作废这笔记录</button></article><p v-if="!selected.expenses.length" class="community-list-message">暂无支出，可以从上方活动行录入。</p>
        </section>
      </template>
      <p class="finance-help">预算与支出保存到校园数据库，不依赖 AI。当前不包含付款、报销审批或学校级经费审批。</p>
    </template>
    <CommunityDialog v-if="modal" :title="modal === 'budget' ? '设置活动预算' : modal === 'expense' ? '录入活动支出' : '确认作废支出'" :busy="financeState.saving || financeState.loading" @close="modal = ''">
      <form class="community-event-form finance-form" @submit.prevent="save"><h3>{{ selected?.title }}</h3>
        <template v-if="modal === 'void'"><p>{{ voiding?.description }} · {{ money(voiding?.amount ?? null) }}</p><p>作废后不再计入支出，金额、原始记录和操作信息仍会保留。</p><label>作废原因<textarea v-model="reason" required maxlength="300" rows="3" :disabled="financeState.saving" :aria-invalid="!!formError || !!financeState.writeError" aria-describedby="finance-form-error"></textarea></label></template>
        <template v-else><label>{{ modal === 'budget' ? '预算金额（元）' : '支出金额（元）' }}<input v-model="amount" required inputmode="decimal" maxlength="11" placeholder="例如 500.00" :disabled="financeState.saving || !!pendingExpense" :aria-invalid="!!formError || !!financeState.writeError" aria-describedby="finance-form-error" /></label><template v-if="modal === 'expense'"><label>支出说明<textarea v-model="description" required maxlength="200" rows="3" :disabled="financeState.saving || !!pendingExpense" aria-describedby="finance-form-error"></textarea></label><label>发生日期<input v-model="occurredOn" type="date" required :max="localDate()" :disabled="financeState.saving || !!pendingExpense" aria-describedby="finance-form-error" /></label><p v-if="pendingExpense" class="finance-help">重试将使用相同编号和内容。失败不一定代表未入账，请先刷新核对，勿重复新建。</p></template></template>
        <p id="finance-form-error" ref="errorElement" tabindex="-1" class="community-error" role="alert">{{ formError || financeState.writeError }}</p>
        <div class="community-dialog-actions"><button type="button" class="community-button secondary" :disabled="financeState.saving || financeState.loading" @click="modal = ''">返回</button><button v-if="pendingExpense && financeState.writeError" type="button" class="community-text-button" :disabled="financeState.saving || financeState.loading" @click="checkPendingExpense">刷新核对本笔支出</button><button class="community-button" :disabled="financeState.saving || financeState.loading || !selected">{{ financeState.saving ? '正在保存…' : modal === 'void' ? '确认作废并保留记录' : pendingExpense ? '重试保存同一笔支出' : '保存到台账' }}</button></div>
      </form>
    </CommunityDialog>
  </div>
</template>

<style scoped>
.community-finance { min-width: 0; }
.community-finance .community-page-title { padding: 0 0 16px; }
.community-finance .community-manager-toolbar { padding-inline: 0; }
.community-finance button:disabled { opacity: .6; cursor: not-allowed; }
.community-finance :is(input, select, textarea) { font-size: 16px; }
.community-finance :is(button, select, textarea):focus-visible { outline: 2px solid var(--community-green); outline-offset: 3px; }
.community-finance :is(h2, h3, p, dd) { overflow-wrap: anywhere; }
.finance-summary, .finance-amounts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin: 0; padding: 20px 0; border-block: 1px solid var(--community-line); }
.finance-summary dt, .finance-amounts dt { color: var(--community-muted); font-size: 13px; }
.finance-summary dd { margin: 8px 0 0; font-size: 22px; font-variant-numeric: tabular-nums; }
.finance-help { font-size: 13px; color: var(--community-muted); margin-block: 12px !important; }
.finance-activities > h2, .finance-ledger > h2 { font-size: 17px; margin-top: 24px; }
.finance-activity, .finance-expense { padding: 20px 0; border-bottom: 1px solid var(--community-line); }
.finance-activity h3, .finance-expense h3 { font-size: 16px; font-weight: 500; }
.finance-amounts { grid-template-columns: repeat(3, minmax(0, 1fr)); border: 0; padding-block: 12px; gap: 8px; }
.finance-amounts dd { margin: 6px 0 0; font-variant-numeric: tabular-nums; }
.community-finance .finance-danger { color: #a3312a !important; }
.finance-expense-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; }
.finance-expense p { margin-top: 8px; white-space: pre-wrap; }
.finance-void-reason { padding: 12px; background: var(--community-bg); }
.finance-notice { margin-block: 12px !important; color: var(--community-green); }
.finance-form { padding: 0; margin: 0; }
.finance-form .community-error { color: #a3312a; }
.finance-form .community-error:empty { display: none; }
@media (max-width: 480px) { .finance-summary dd { font-size: 19px; } .finance-amounts { grid-template-columns: 1fr; gap: 8px; } .finance-amounts > div { display: flex; justify-content: space-between; gap: 12px; } .finance-amounts dd { margin: 0; } }
</style>
