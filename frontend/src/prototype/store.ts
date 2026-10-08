import { reactive, watch } from 'vue';
import { clubs, initialActivities, type Activity } from './data';

export type ApplicationStatus = 'pending' | 'approved' | 'rejected' | 'withdrawn';
export interface Application { id: string; clubId: string; name: string; major: string; reason: string; date: string; status: ApplicationStatus; mine: boolean; feedback: string }
export interface Notice { id: string; title: string; text: string; path: string; read: boolean; date: string }
export interface PrototypeState {
  profile: { name: string; major: string; interests: string[] };
  favorites: string[]; applications: Application[]; registrations: string[];
  activities: Activity[]; notices: Notice[]; managedClub: string;
}
const storageKey = 'campus-prototype-v1';
function seed(): PrototypeState {
  return {
    profile: { name: '林同学', major: '计算机科学 · 2024级', interests: ['摄影', '编程', '户外'] },
    favorites: [], registrations: [], activities: structuredClone(initialActivities), managedClub: 'photo',
    applications: [
      { id: 'demo-request-1', clubId: 'photo', name: '陈同学', major: '数字媒体 · 2025级', reason: '喜欢用手机记录生活，希望和大家一起学习构图与后期。', date: '2026-10-01', status: 'pending', mine: false, feedback: '' },
      { id: 'demo-request-2', clubId: 'code', name: '周同学', major: '软件工程 · 2025级', reason: '希望参加入门工作坊，完成一个自己的网页。', date: '2026-10-01', status: 'pending', mine: false, feedback: '' }
    ],
    notices: [{ id: 'welcome', title: '欢迎来到社遇', text: '先发现一个感兴趣的社团，再开启你的校园生活。这是交互原型，所有人物和业务记录均为模拟。', path: '/clubs', date: '2026-10-01', read: false }]
  };
}
function load(): PrototypeState {
  try {
    const saved = localStorage.getItem(storageKey);
    if (saved) {
      const value = JSON.parse(saved) as PrototypeState;
      if (Array.isArray(value.activities) && Array.isArray(value.applications) && Array.isArray(value.notices) && Array.isArray(value.registrations) && Array.isArray(value.favorites) && value.profile && Array.isArray(value.profile.interests) && clubs.some(c => c.id === value.managedClub)) return value;
    }
  } catch { /* Disabled browser storage falls back to an in-memory prototype. */ }
  return seed();
}
export const state = reactive(load());
export const ui = reactive({ toast: '', storageUnavailable: false });
let toastTimer: ReturnType<typeof setTimeout>;
export function toast(message: string) {
  ui.toast = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { ui.toast = ''; }, 3500);
}
watch(state, value => {
  try { localStorage.setItem(storageKey, JSON.stringify(value)); }
  catch { ui.storageUnavailable = true; }
}, { deep: true });
export const statusLabels: Record<ApplicationStatus, string> = { pending: '待审核', approved: '已通过', rejected: '未通过', withdrawn: '已撤回' };
export function today() { return new Date().toLocaleDateString('sv-SE'); }
// LAN HTTP is not a secure context on phones. These are demo record IDs, not credentials.
export function prototypeId() { return globalThis.crypto?.randomUUID?.() ?? `demo-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`; }
function notify(title: string, text: string, path: string) {
  state.notices.unshift({ id: prototypeId(), title, text, path, read: false, date: today() });
}
export function toggleFavorite(id: string) {
  const index = state.favorites.indexOf(id);
  if (index < 0) state.favorites.push(id); else state.favorites.splice(index, 1);
  toast(index < 0 ? '已收藏社团（仅此浏览器）' : '已取消收藏');
}
export function apply(clubId: string, reason: string): boolean {
  const club = clubs.find(c => c.id === clubId);
  if (!reason.trim()) { toast('请填写加入理由'); return false; }
  if (!club?.recruiting || state.applications.some(a => a.mine && a.clubId === clubId && ['pending', 'approved'].includes(a.status))) { toast('当前不可重复申请'); return false; }
  state.applications.unshift({ id: prototypeId(), clubId, name: state.profile.name, major: state.profile.major, reason: reason.trim(), date: today(), status: 'pending', mine: true, feedback: '' });
  notify('入社申请已提交 · 演示', `${club.name}已收到你的模拟申请。可切换负责人工作台体验审核。`, '/me');
  toast('模拟申请已提交，等待负责人审核'); return true;
}
export function review(id: string, approved: boolean, feedback: string) {
  const item = state.applications.find(a => a.id === id && a.status === 'pending' && a.clubId === state.managedClub);
  if (!item) return;
  item.status = approved ? 'approved' : 'rejected'; item.feedback = feedback.trim();
  if (item.mine) notify(approved ? '你的入社申请已通过 · 演示' : '入社申请审核结果 · 演示', `${clubs.find(c => c.id === item.clubId)?.name}：${feedback || (approved ? '欢迎加入！' : '请查看招新条件后再尝试。')}`, '/me');
  toast(approved ? '已通过模拟审核，成员名单已更新' : '已记录模拟审核结果');
}
export function register(id: string): boolean {
  const activity = state.activities.find(a => a.id === id);
  if (!activity || activity.status !== 'published' || state.registrations.includes(id)) return false;
  if (activity.enrolled >= activity.capacity) { toast('活动名额已满'); return false; }
  if (new Date(`${activity.date}T${activity.time}`).getTime() <= Date.now()) { toast('活动已开始，无法报名'); return false; }
  state.registrations.push(id); activity.enrolled++;
  notify('活动报名成功 · 演示', `已加入「${activity.title}」模拟名单，请留意活动时间与地点。`, `/activities/${id}`);
  toast('模拟报名成功，可在个人中心查看'); return true;
}
export function cancelRegistration(id: string) {
  const index = state.registrations.indexOf(id);
  if (index < 0) return;
  state.registrations.splice(index, 1);
  const activity = state.activities.find(a => a.id === id);
  if (activity) activity.enrolled--;
  notify('活动报名已取消 · 演示', `已退出「${activity?.title}」模拟名单。`, '/me'); toast('已取消模拟报名');
}
export function resetPrototype() { Object.assign(state, seed()); toast('演示数据已恢复初始状态'); }
export function dateLabel(date: string) { return `${Number(date.slice(5, 7))}月${Number(date.slice(8, 10))}日`; }
