import { computed, reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';

export interface Notice { id: number; type: string; sourceId: number; title: string; content: string; targetPath: string; createdAt: string; readAt: string | null }
interface Inbox { items: Notice[]; total: number; page: number; hasMore: boolean; unread: number }
export const notificationState = reactive({ userId: null as number | null, items: [] as Notice[], total: 0, page: 0, hasMore: false, unread: 0, loading: false, saving: false, error: '', writeError: '' });
export const myNotices = computed(() => notificationState.userId === business.user?.id ? notificationState.items : []);
export const unreadNotices = computed(() => notificationState.userId === business.user?.id ? notificationState.unread : 0);
let generation = 0;
export async function refreshNotifications(page = notificationState.page) {
  if (!businessMode) return;
  const ticket = ++generation, userId = business.user?.id ?? null;
  if (notificationState.userId !== userId) {
    notificationState.items = []; notificationState.total = 0; notificationState.unread = 0; notificationState.page = 0; notificationState.hasMore = false;
    notificationState.writeError = ''; page = 0;
  }
  notificationState.userId = userId; notificationState.error = '';
  if (userId === null) { notificationState.loading = false; return; }
  notificationState.loading = true;
  try {
    const inbox = await request<Inbox>(`/notifications?page=${page}`);
    if (ticket !== generation || userId !== business.user?.id) return;
    Object.assign(notificationState, inbox);
  } catch (error) {
    if (ticket === generation && userId === business.user?.id) {
      notificationState.error = errorMessage(error); notificationState.items = []; notificationState.unread = 0; notificationState.total = 0; notificationState.hasMore = false;
    }
  } finally { if (ticket === generation) notificationState.loading = false; }
}
// Both navigation shells use this watcher; no polling or real-time push is claimed.
export function useNotificationRefresh() {
  watch(() => [business.user?.id, business.loading], () => {
    if (!business.loading || notificationState.userId !== (business.user?.id ?? null)) void refreshNotifications();
  }, { flush: 'sync' });
}
async function mutate(path: string) {
  if (notificationState.saving) return false;
  const userId = business.user?.id;
  notificationState.saving = true; notificationState.writeError = '';
  try {
    await request(path, { method: 'POST' });
    await refreshNotifications();
    return userId === business.user?.id;
  } catch (error) {
    if (userId === business.user?.id) notificationState.writeError = errorMessage(error);
    return false;
  } finally { notificationState.saving = false; }
}
export function readNotice(id: number) { return mutate(`/notifications/${id}/read`); }
export function readAllNotices() { return mutate('/notifications/read-all'); }
