import { computed, reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode, refreshBusiness } from '../prototype/business';
import { clearRecommendations } from './recommendations';

export interface ClubInput { name: string; category: string; description: string; tags: string; campus: string; recruiting: boolean; requirements: string; schedule: string; place: string }
export interface ManagedClub extends ClubInput { id: number; slug: string | null; members: number }
type TextField = Exclude<keyof ClubInput, 'recruiting'>;
export const clubFields: { key: TextField; label: string; max: number; optional?: boolean; multiline?: boolean; group: 'profile' | 'recruitment' }[] = [
  { key: 'name', label: '社团名称', max: 100, group: 'profile' },
  { key: 'category', label: '社团类别', max: 40, group: 'profile' },
  { key: 'campus', label: '所属校区', max: 100, group: 'profile' },
  { key: 'tags', label: '兴趣标签（可选，英文逗号分隔）', max: 255, optional: true, group: 'profile' },
  { key: 'description', label: '社团介绍', max: 2000, multiline: true, group: 'profile' },
  { key: 'requirements', label: '招新条件', max: 500, multiline: true, group: 'recruitment' },
  { key: 'schedule', label: '日常活动时间说明', max: 200, group: 'recruitment' },
  { key: 'place', label: '日常活动地点说明', max: 200, group: 'recruitment' },
];
export function clubInput(club: ClubInput): ClubInput {
  const input = { recruiting: club.recruiting } as ClubInput;
  for (const field of clubFields) input[field.key] = club[field.key].trim();
  return input;
}
export function validateClub(input: ClubInput) {
  const errors: Partial<Record<TextField, string>> = {};
  for (const field of clubFields) {
    const text = input[field.key].trim();
    if (!field.optional && !text) errors[field.key] = `请填写${field.label}，不能只输入空格。`;
    else if (text.length > field.max) errors[field.key] = `${field.label}最多 ${field.max} 个字符。`;
  }
  return errors;
}
export const clubSettingsState = reactive({ loading: false, saving: false, error: '', writeError: '', notice: '', context: '', club: null as ManagedClub | null });
let loadVersion = 0, writeVersion = 0;
function owner() {
  const user = business.user, clubId = business.managedClubId;
  return businessMode && user?.role === 'MANAGER' && clubId !== null && user.managedClubIds.includes(clubId) ? `${user.id}:${clubId}` : '';
}
export const currentClubSettings = computed(() => owner() && owner() === clubSettingsState.context ? clubSettingsState.club : null);
watch(owner, () => {
  ++loadVersion; ++writeVersion;
  Object.assign(clubSettingsState, { loading: false, saving: false, error: '', writeError: '', notice: '', context: '', club: null });
}, { flush: 'sync' });
export async function loadClubSettings() {
  const context = owner(), clubId = business.managedClubId, ticket = ++loadVersion;
  clubSettingsState.club = null; clubSettingsState.context = ''; clubSettingsState.error = ''; clubSettingsState.writeError = ''; clubSettingsState.notice = '';
  if (!context) { clubSettingsState.loading = false; return; }
  clubSettingsState.loading = true;
  try {
    const clubs = await request<ManagedClub[]>('/manage/clubs');
    if (ticket !== loadVersion || context !== owner()) return;
    const club = clubs.find(item => item.id === clubId);
    if (!club) throw new Error('未找到你负责的社团，请重新登录核对权限。');
    clubSettingsState.club = club; clubSettingsState.context = context;
  } catch (error) { if (ticket === loadVersion && context === owner()) clubSettingsState.error = errorMessage(error); }
  finally { if (ticket === loadVersion) clubSettingsState.loading = false; }
}
export async function saveClubSettings(input: ClubInput) {
  const context = owner(), id = currentClubSettings.value?.id;
  if (!context || id === undefined || clubSettingsState.saving || clubSettingsState.loading) return false;
  const errors = validateClub(input);
  if (Object.keys(errors).length) { clubSettingsState.writeError = Object.values(errors)[0]!; return false; }
  const ticket = ++writeVersion;
  const current = () => ticket === writeVersion && context === owner();
  ++loadVersion; clubSettingsState.saving = true; clubSettingsState.writeError = ''; clubSettingsState.notice = '';
  try {
    const saved = await request<ManagedClub>(`/manage/clubs/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(clubInput(input)) });
    if (!current()) return false;
    clubSettingsState.club = saved; clubSettingsState.context = context;
    clearRecommendations();
    await refreshBusiness();
    if (!current()) return false;
    clubSettingsState.notice = business.error ? '资料已保存，但学生端资料刷新失败，请重新加载页面核对，不要重复提交。' : '社团资料与招新设置已保存，学生重新读取页面即可看到。';
    return true;
  } catch (error) {
    if (current()) clubSettingsState.writeError = `${errorMessage(error)}。如响应丢失，保存可能已生效，请重新读取核对后再保存。`;
    return false;
  } finally { if (current()) clubSettingsState.saving = false; }
}
