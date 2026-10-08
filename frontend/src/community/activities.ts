import { computed, reactive } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';
import { toast } from '../prototype/store';

export interface Activity { id: number; clubId: number; title: string; description: string; location: string; startTime: string; registrationDeadline: string; capacity: number; enrolled: number; status: 'DRAFT' | 'PUBLISHED'; demo: boolean }
export interface Registration { id: number; activityId: number; clubId: number; title: string; startTime: string; location: string; status: 'REGISTERED' | 'CANCELLED'; registeredAt: string; cancelledAt: string | null }
export interface Participant { id: number; name: string; major: string; registeredAt: string }
export type Draft = Pick<Activity, 'title' | 'description' | 'location' | 'startTime' | 'registrationDeadline' | 'capacity'>;
export const activityState = reactive({ loading: false, saving: false, error: '', writeError: '', userId: null as number | null,
  activities: [] as Activity[], registrations: [] as Registration[], managed: [] as Activity[] });
export const myRegistrations = computed(() => activityState.userId === business.user?.id ? activityState.registrations : []);
export const managedActivities = computed(() => activityState.userId === business.user?.id && business.user?.role === 'MANAGER' ? activityState.managed.filter(a => a.clubId === business.managedClubId) : []);
let generation = 0;
export async function refreshActivities() {
  if (!businessMode) return;
  const ticket = ++generation, userId = business.user?.id ?? null, clubId = business.managedClubId;
  activityState.loading = true; activityState.error = '';
  if (activityState.userId !== userId) { activityState.registrations = []; activityState.managed = []; }
  try {
    const [activities, registrations, managed] = await Promise.all([
      request<Activity[]>('/activities'), userId === null ? Promise.resolve([]) : request<Registration[]>('/registrations/mine'),
      userId === null || clubId === null ? Promise.resolve([]) : request<Activity[]>(`/manage/clubs/${clubId}/activities`)
    ]);
    if (ticket !== generation || userId !== (business.user?.id ?? null) || clubId !== business.managedClubId) return;
    activityState.activities = activities; activityState.registrations = registrations; activityState.managed = managed; activityState.userId = userId;
  } catch (error) {
    if (ticket === generation) { activityState.error = errorMessage(error); activityState.activities = []; activityState.registrations = []; activityState.managed = []; }
  } finally { if (ticket === generation) activityState.loading = false; }
}
async function mutate(path: string, body?: Draft) {
  if (activityState.saving) return false;
  activityState.saving = true; activityState.writeError = '';
  try {
    await request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    await refreshActivities();
    toast(activityState.error ? '操作已保存，列表刷新失败，请重新加载' : '操作已保存到校园数据库');
    return true;
  } catch (error) { activityState.writeError = errorMessage(error); await refreshActivities(); return false; }
  finally { activityState.saving = false; }
}
export function saveDraft(clubId: number, body: Draft) { return mutate(`/manage/clubs/${clubId}/activities`, body); }
export function publishActivity(id: number) { return mutate(`/manage/activities/${id}/publish`); }
export function registerActivity(id: number) { return mutate(`/activities/${id}/registrations`); }
export function cancelActivityRegistration(id: number) { return mutate(`/activities/${id}/registrations/cancel`); }
export function activityTime(time: string) { return time.replace('T', ' ').slice(0, 16); }
export function registrationClosed(activity: Activity) { return Date.now() >= new Date(activity.registrationDeadline).getTime(); }
