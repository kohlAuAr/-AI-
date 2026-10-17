import { reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';
import { refreshActivities, type Draft } from './activities';

interface Proposal { requestId: string; title: string; description: string; mode: string; notice: string }
export const planningState = reactive({ loading: false, saving: false, error: '', notice: '', proposal: null as Proposal | null, pending: null as { requestId: string; body: Draft; attempted: boolean } | null });
let version = 0;
function scope() { const user = business.user, club = business.managedClubId; return businessMode && user?.role === 'MANAGER' && club !== null && user.managedClubIds.includes(club) ? `${user.id}:${club}` : ''; }
watch(scope, () => { ++version; Object.assign(planningState, { loading: false, saving: false, error: '', notice: '', proposal: null, pending: null }); }, { flush: 'sync' });
export async function generatePlan(requirement: string) {
  const owner = scope(), clubId = business.managedClubId;
  if (!owner || planningState.loading || planningState.saving || planningState.pending || !requirement.trim()) return null;
  const ticket = ++version; planningState.loading = true; planningState.error = ''; planningState.notice = '';
  try {
    const proposal = await request<Proposal>('/ai/plans', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ clubId, requirement: requirement.trim() }) });
    if (ticket !== version || owner !== scope()) return null;
    planningState.proposal = proposal; return proposal;
  } catch (error) { if (ticket === version && owner === scope()) planningState.error = errorMessage(error); return null; }
  finally { if (ticket === version) planningState.loading = false; }
}
export function preparePlanDraft(body: Draft) {
  if (!scope() || !planningState.proposal || planningState.pending) return false;
  planningState.pending = { requestId: planningState.proposal.requestId, body: { ...body }, attempted: false }; planningState.error = ''; return true;
}
export function discardUnsentDraft() { if (!planningState.pending?.attempted && !planningState.saving) planningState.pending = null; }
export async function confirmPlanDraft() {
  const owner = scope(), club = business.managedClubId, pending = planningState.pending;
  if (!owner || !pending || planningState.saving) return false;
  const ticket = ++version; planningState.saving = true; planningState.error = ''; pending.attempted = true;
  try {
    await request(`/manage/clubs/${club}/drafts/${pending.requestId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pending.body) });
    if (ticket !== version || owner !== scope()) return false;
    planningState.pending = null; planningState.proposal = null; planningState.notice = '已确认保存草稿，尚未发布。请在活动列表单独确认发布。';
    await refreshActivities(); return ticket === version && owner === scope();
  } catch (error) {
    if (ticket === version && owner === scope()) planningState.error = `${errorMessage(error)}。本次编号和内容已保留，可按相同内容重试，不会重复创建；不要另建同名草稿。刷新页面前请先核对活动列表。`;
    return false;
  } finally { if (ticket === version) planningState.saving = false; }
}
