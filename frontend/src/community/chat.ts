import { reactive, watch } from 'vue';
import { business, businessMode } from '../prototype/business';
import { request, errorMessage, type Reply, type Turn } from '../api';

export const chatState = reactive({ conversationId: '', turns: [] as Turn[], sessions: [] as { id: string; createdAt: string }[], busy: false, loading: false, error: '', sessionError: '' });
let version = 0, listVersion = 0;
function key() { return `campus-conversation-v2:${business.user?.id}`; }
function remember(id: string) { try { if (id) sessionStorage.setItem(key(), id); else sessionStorage.removeItem(key()); } catch { /* Durable history remains available. */ } }
watch([() => business.user?.id, () => business.user?.role], () => {
  ++version; ++listVersion; Object.assign(chatState, { conversationId: '', turns: [], sessions: [], busy: false, loading: false, error: '', sessionError: '' });
}, { flush: 'sync' });
export function newConversation() { ++version; chatState.conversationId = ''; chatState.turns = []; chatState.error = ''; chatState.busy = false; chatState.loading = false; remember(''); }
export async function loadSessions(restore = false) {
  if (!businessMode || !business.user) return;
  const owner = business.user.id, ticket = ++listVersion; chatState.sessionError = '';
  try {
    const rows = await request<{ id: string; createdAt: string }[]>('/ai/conversations');
    if (ticket !== listVersion || owner !== business.user?.id) return;
    chatState.sessions = rows;
    let saved = ''; try { saved = sessionStorage.getItem(key()) || ''; } catch { /* Optional local selection. */ }
    if (restore && !chatState.busy && rows.some(row => row.id === saved)) await loadHistory(saved);
  } catch (error) { if (ticket === listVersion && owner === business.user?.id) chatState.sessionError = errorMessage(error); }
}
export async function loadHistory(id: string) {
  if (!businessMode || !business.user || chatState.busy) return;
  const owner = business.user.id, ticket = ++version;
  chatState.loading = true; chatState.turns = []; chatState.error = ''; chatState.conversationId = '';
  try {
    const turns = await request<Turn[]>(`/ai/conversations/${id}`);
    if (ticket !== version || owner !== business.user?.id) return;
    chatState.turns = turns; chatState.conversationId = id; remember(id);
  } catch (error) { if (ticket === version && owner === business.user?.id) { chatState.error = `${errorMessage(error)}。可新建会话继续。`; remember(''); } }
  finally { if (ticket === version) chatState.loading = false; }
}
export async function sendQuestion(question: string, channel: 'chat' | 'assistant') {
  if (!businessMode || !business.user || chatState.busy || chatState.loading || !question.trim()) return false;
  const owner = business.user.id, ticket = ++version; chatState.busy = true; chatState.error = '';
  try {
    const reply = await request<Reply>(`/ai/${channel}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: question.trim(), conversationId: chatState.conversationId || null }) });
    if (ticket !== version || owner !== business.user?.id) return false;
    chatState.conversationId = reply.conversationId; chatState.turns.push({ ...reply, question: question.trim() }); remember(reply.conversationId);
    await loadSessions(); return ticket === version && owner === business.user?.id;
  } catch (error) { if (ticket === version && owner === business.user?.id) chatState.error = `${errorMessage(error)}。如响应丢失，可刷新历史核对，或新建会话重试。`; return false; }
  finally { if (ticket === version) chatState.busy = false; }
}
