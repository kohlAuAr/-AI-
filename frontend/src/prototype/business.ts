import { computed, reactive } from 'vue';
import { request, errorMessage } from '../api';
import { clubs as demoClubs, type Club } from './data';
import { publicPreview } from './public';
import { toast, type Application } from './store';

export const businessMode = !publicPreview && import.meta.env.VITE_BUSINESS_API !== 'false';
interface User { id: number; username: string; name: string; major: string; role: 'STUDENT' | 'MANAGER' | 'PLATFORM_ADMIN'; managedClubIds: number[] }
interface Session { user: User | null; demoAccounts: boolean }
export interface Profile { id: number; username: string; name: string; major: string; role: User['role']; interests: string[]; availableTime: string; interestDescription: string }
export type ProfileInput = Pick<Profile, 'name' | 'major' | 'interests' | 'availableTime' | 'interestDescription'>;
interface ApiClub { id: number; slug: string | null; name: string; category: string; description: string; tags: string; recruiting: boolean; requirements: string; schedule: string; place: string; members: number }
interface ApiApplication { id: number; clubId: number; userId: number; name: string; major: string; reason: string; status: Application['status']; feedback: string; createdAt: string }
interface ApiMember { id: number; clubId: number; userId: number; name: string; major: string; role: string; joinedAt: string }
export interface Member { id: string; clubId: string; name: string; major: string; role: string; date: string }
export const business = reactive({
  loading: false, saving: false, ready: false, error: '', demoAccounts: false,
  user: null as User | null, profile: null as Profile | null, clubs: [] as Club[], applications: [] as Application[],
  memberships: [] as Member[], managedApplications: [] as Application[], managedMembers: [] as Member[], managedClubId: null as number | null
});
export const clubCatalog = computed(() => businessMode ? business.clubs : demoClubs);
export const managedClubs = computed(() => business.clubs.filter(c => business.user?.managedClubIds.includes(c.backendId!)));
let generation = 0;
function clearPrivateRecords() {
  business.profile = null;
  business.applications = []; business.memberships = []; business.managedApplications = []; business.managedMembers = [];
}
function clubKey(id: number) { return business.clubs.find(c => c.backendId === id)?.id ?? String(id); }
function application(row: ApiApplication): Application {
  return { ...row, id: String(row.id), clubId: clubKey(row.clubId), date: row.createdAt.slice(0, 10), mine: row.userId === business.user?.id };
}
function member(row: ApiMember): Member {
  return { ...row, id: String(row.id), clubId: clubKey(row.clubId), date: row.joinedAt.slice(0, 10) };
}
export async function refreshBusiness(): Promise<void> {
  if (!businessMode) return;
  const ticket = ++generation;
  business.loading = true; business.error = '';
  try {
    const [rows, session] = await Promise.all([request<ApiClub[]>('/clubs'), request<Session>('/auth/session')]);
    if (ticket !== generation) return;
    business.clubs = rows.map(row => {
      const decoration = demoClubs.find(c => c.id === row.slug);
      return {
        id: row.slug || String(row.id), backendId: row.id, name: row.name, category: row.category,
        description: row.description, tags: row.tags?.split(',').filter(Boolean) ?? [],
        members: row.members, recruiting: row.recruiting, requirements: row.requirements || '请联系社团负责人了解条件。',
        schedule: row.schedule || '待安排', place: row.place || '待安排',
        mark: decoration?.mark || row.name.slice(0, 1), color: decoration?.color || 'peach', slogan: decoration?.slogan || row.description
      };
    });
    if (business.user?.id !== session.user?.id) { clearPrivateRecords(); business.managedClubId = null; }
    business.user = session.user; business.demoAccounts = session.demoAccounts;
    if (!session.user) { clearPrivateRecords(); business.ready = true; return; }
    const allowed = session.user.managedClubIds;
    if (business.managedClubId === null || !allowed.includes(business.managedClubId)) business.managedClubId = allowed[0] ?? null;
    const clubId = business.managedClubId;
    const [applications, memberships, managedApplications, managedMembers, profile] = await Promise.all([
      request<ApiApplication[]>('/recruitment/my-applications'), request<ApiMember[]>('/memberships/mine'),
      clubId === null ? Promise.resolve([]) : request<ApiApplication[]>(`/manage/clubs/${clubId}/applications`),
      clubId === null ? Promise.resolve([]) : request<ApiMember[]>(`/manage/clubs/${clubId}/members`),
      request<Profile>('/profile')
    ]);
    if (ticket !== generation) return;
    business.applications = applications.map(application); business.memberships = memberships.map(member);
    business.managedApplications = managedApplications.map(application); business.managedMembers = managedMembers.map(member);
    business.profile = profile;
    business.ready = true;
  } catch (error) {
    if (ticket === generation) { business.error = errorMessage(error); clearPrivateRecords(); }
  } finally { if (ticket === generation) business.loading = false; }
}
async function mutate(path: string, body?: unknown): Promise<boolean> {
  if (business.saving) return false;
  business.saving = true;
  try {
    await request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    await refreshBusiness();
    toast(business.error ? '操作已保存，但列表刷新失败，请重试刷新' : '操作已保存到校园数据库');
    return true;
  } catch (error) {
    await refreshBusiness(); toast(errorMessage(error)); return false;
  } finally { business.saving = false; }
}
export function submitApplication(club: Club, reason: string) {
  return mutate('/recruitment/applications', { clubId: club.backendId, reason });
}
export function reviewApplication(id: string, approved: boolean, feedback: string) {
  return mutate(`/manage/applications/${id}/review`, { approved, feedback });
}
export function withdrawApplication(id: string) { return mutate(`/recruitment/applications/${id}/withdraw`); }
export async function login(username: string, password: string) {
  await request('/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) });
  business.user = null; clearPrivateRecords();
  await refreshBusiness();
  if (!business.user || business.error) throw new Error(business.error || '登录状态读取失败，请重试');
}
export async function register(input: { username: string; password: string; name: string; major: string }) {
  return request<Profile>('/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
}
export async function saveProfile(input: ProfileInput) {
  const userId = business.user?.id;
  if (!userId) throw new Error('请先登录再编辑资料');
  const ticket = ++generation;
  business.loading = false;
  const profile = await request<Profile>('/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
  if (ticket !== generation || business.user?.id !== userId) throw new Error('账号状态已变化，请重新加载资料');
  business.profile = profile;
  business.user.name = profile.name; business.user.major = profile.major;
}
export async function logout() {
  await request('/auth/logout', { method: 'POST' });
  ++generation; business.user = null; business.managedClubId = null; clearPrivateRecords();
  await refreshBusiness();
}
