import { computed, reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';
import { toast } from '../prototype/store';
import { refreshNotifications } from './notifications';

export interface Activity { id: number; clubId: number; title: string; description: string; location: string; startTime: string; registrationDeadline: string; capacity: number; enrolled: number; status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED'; demo: boolean; checkInOpen: boolean }
export interface Registration { id: number; activityId: number; clubId: number; title: string; startTime: string; location: string; status: 'REGISTERED' | 'CANCELLED'; registeredAt: string; cancelledAt: string | null; checkedInAt: string | null }
export interface Participant { id: number; name: string; major: string; registeredAt: string; checkedInAt: string | null }
export type Draft = Pick<Activity, 'title' | 'description' | 'location' | 'startTime' | 'registrationDeadline' | 'capacity'>;
export const activityState = reactive({ loading: false, saving: false, error: '', writeError: '', userId: null as number | null,
  activities: [] as Activity[], registrations: [] as Registration[], managed: [] as Activity[] });
export const myRegistrations = computed(() => activityState.userId === business.user?.id ? activityState.registrations : []);
export const managedActivities = computed(() => activityState.userId === business.user?.id && business.user?.role === 'MANAGER' ? activityState.managed.filter(a => a.clubId === business.managedClubId) : []);
let generation = 0, writeGeneration = 0;
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], () => {
  ++generation; ++writeGeneration;
  activityState.registrations = []; activityState.managed = []; activityState.userId = null;
  activityState.loading = false; activityState.saving = false; activityState.error = ''; activityState.writeError = '';
}, { flush: 'sync' });
export async function refreshActivities() {
  if (!businessMode) return;
  const ticket = ++generation, userId = business.user?.id ?? null, clubId = business.managedClubId;
  activityState.loading = true; activityState.error = '';
  if (activityState.userId !== userId) { activityState.registrations = []; activityState.managed = []; }
  try {
    const [activities, registrations, managed] = await Promise.all([
      request<Activity[]>('/activities'), userId === null ? Promise.resolve([]) : request<Registration[]>('/registrations/mine'),
      userId === null || clubId === null || business.user?.role !== 'MANAGER' ? Promise.resolve([]) : request<Activity[]>(`/manage/clubs/${clubId}/activities`)
    ]);
    if (ticket !== generation || userId !== (business.user?.id ?? null) || clubId !== business.managedClubId) return;
    activityState.activities = activities; activityState.registrations = registrations; activityState.managed = managed; activityState.userId = userId;
  } catch (error) {
    if (ticket === generation) { activityState.error = errorMessage(error); activityState.activities = []; activityState.registrations = []; activityState.managed = []; }
  } finally { if (ticket === generation) activityState.loading = false; }
}
async function mutate(path: string, body?: Draft | { code: string }) {
  if (activityState.saving) return false;
  const ticket = ++writeGeneration, userId = business.user?.id, clubId = business.managedClubId;
  const current = () => ticket === writeGeneration && userId === business.user?.id && clubId === business.managedClubId;
  activityState.saving = true; activityState.writeError = '';
  try {
    await request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    if (!current()) return false;
    await refreshActivities();
    if (!current()) return false;
    await refreshNotifications(0);
    if (!current()) return false;
    toast(activityState.error ? '操作已保存，列表刷新失败，请重新加载' : '操作已保存到校园数据库');
    return true;
  } catch (error) { if (current()) { activityState.writeError = errorMessage(error); await refreshActivities(); } return false; }
  finally { if (current()) activityState.saving = false; }
}
export function saveDraft(clubId: number, body: Draft) { return mutate(`/manage/clubs/${clubId}/activities`, body); }
export function publishActivity(id: number) { return mutate(`/manage/activities/${id}/publish`); }
export function registerActivity(id: number) { return mutate(`/activities/${id}/registrations`); }
export function cancelActivityRegistration(id: number) { return mutate(`/activities/${id}/registrations/cancel`); }
export function checkInActivity(id: number, code: string) {
  if (!businessMode || business.user?.role !== 'STUDENT' || !myRegistrations.value.some(r => r.activityId === id && r.status === 'REGISTERED')) return Promise.resolve(false);
  if (!code.trim() || code.trim().length > 64) { activityState.writeError = '请输入负责人提供的签到码（最多 64 个字符）。'; return Promise.resolve(false); }
  return mutate(`/activities/${id}/check-in`, { code: code.trim() });
}
export function activityTime(time: string) { return time.replace('T', ' ').slice(0, 16); }
export function registrationClosed(activity: Activity) { return Date.now() >= new Date(activity.registrationDeadline).getTime(); }
