import { reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';
import type { Participant } from './activities';

export interface CheckIn { activityId: number; open: boolean; code: string | null; registered: number; checkedIn: number }
export const checkInState = reactive({ activityId: null as number | null, loading: false, saving: false, error: '', writeError: '', notice: '', report: null as CheckIn | null, participants: [] as Participant[] });
let loadVersion = 0, writeVersion = 0;
function owner() {
  const user = business.user, clubId = business.managedClubId;
  return businessMode && user?.role === 'MANAGER' && clubId !== null && user.managedClubIds.includes(clubId) ? `${user.id}:${clubId}` : '';
}
export function clearCheckIn() {
  ++loadVersion; ++writeVersion;
  Object.assign(checkInState, { activityId: null, loading: false, saving: false, error: '', writeError: '', notice: '', report: null, participants: [] });
}
watch(owner, clearCheckIn, { flush: 'sync' });
export async function loadCheckIn(id: number) {
  if (checkInState.activityId !== id) { clearCheckIn(); checkInState.activityId = id; }
  const context = owner();
  if (!context) { clearCheckIn(); return; }
  const ticket = ++loadVersion;
  checkInState.loading = true; checkInState.error = ''; checkInState.report = null; checkInState.participants = [];
  try {
    const [report, participants] = await Promise.all([request<CheckIn>(`/manage/activities/${id}/check-in`), request<Participant[]>(`/manage/activities/${id}/registrations`)]);
    if (ticket !== loadVersion || context !== owner()) return;
    checkInState.report = report; checkInState.participants = participants;
  } catch (error) { if (ticket === loadVersion && context === owner()) checkInState.error = errorMessage(error); }
  finally { if (ticket === loadVersion) checkInState.loading = false; }
}
export async function changeCheckIn(action: 'open' | 'close') {
  const context = owner(), id = checkInState.activityId;
  if (!context || id === null || checkInState.saving || !checkInState.report) return false;
  const ticket = ++writeVersion;
  const current = () => ticket === writeVersion && context === owner() && id === checkInState.activityId;
  checkInState.saving = true; checkInState.writeError = ''; checkInState.notice = '';
  try {
    await request<CheckIn>(`/manage/activities/${id}/check-in/${action}`, { method: 'POST' });
    if (!current()) return false;
    // Clear the previous code and re-read authoritative state, including a possibly lost response.
    await loadCheckIn(id);
    if (!current()) return false;
    checkInState.notice = checkInState.error ? '操作已保存，但读取失败，请刷新签到与名单。' : action === 'open' ? '签到已开启，请将签到码告知现场学生。' : '签到已关闭，已有签到记录仍然保留。';
    return true;
  } catch (error) {
    if (current()) { checkInState.writeError = `${errorMessage(error)}。请刷新核对签到状态后再操作。`; await loadCheckIn(id); }
    return false;
  } finally { if (current()) checkInState.saving = false; }
}
