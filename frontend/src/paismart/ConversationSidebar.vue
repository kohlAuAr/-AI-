<!-- Adapted from PaiSmart views/chat/modules/conversation-sidebar.vue; only existing campus history actions. -->
<script setup lang="ts">
import { ref } from 'vue';
import { NButton } from 'naive-ui';
import { chatState, deleteSession, loadHistory, loadSessions, newConversation } from '../community/chat';
import CommunityDialog from '../community/CommunityDialog.vue';
import CommunityIcon from '../community/CommunityIcon.vue';
import { displayTime } from '../api';
defineProps<{ open: boolean }>();
type Session = { id: string; createdAt: string };
const pending = ref<Session | null>(null), deleting = ref(false);
function askDelete(item: Session) { if (!chatState.busy && !chatState.loading) pending.value = item; }
function closeDialog() { if (!deleting.value) pending.value = null; }
async function confirmDelete() {
  if (!pending.value || deleting.value) return;
  deleting.value = true;
  const removed = await deleteSession(pending.value.id);
  deleting.value = false;
  if (removed) pending.value = null;
}
</script>
<template><aside class="paismart-conversation-sidebar" :class="{ open }" aria-label="本人历史会话"><div class="paismart-conversation-title"><h3>对话列表</h3><NButton secondary type="primary" size="small" :disabled="chatState.busy || chatState.loading" @click="newConversation">新对话</NButton></div><p class="paismart-conversation-scope">仅当前登录账号可见，可单独删除</p><div class="paismart-history-list"><div v-for="item in chatState.sessions" :key="item.id" class="paismart-history-item" :class="{ selected: chatState.conversationId === item.id }"><button type="button" class="paismart-history-select" :disabled="chatState.busy || chatState.loading" :aria-pressed="chatState.conversationId === item.id" @click="loadHistory(item.id)"><span class="paismart-history-icon"><CommunityIcon name="assistant" /></span><span><strong>{{ displayTime(item.createdAt) }}</strong><small>会话 {{ item.id.slice(0, 8) }}</small></span></button><button type="button" class="paismart-history-delete" :aria-label="`删除会话 ${item.id.slice(0, 8)}`" :title="`删除会话 ${item.id.slice(0, 8)}`" :disabled="chatState.busy || chatState.loading" @click="askDelete(item)"><CommunityIcon name="trash" /></button></div><p v-if="!chatState.sessions.length">暂无对话记录</p></div><p v-if="chatState.sessionError" class="community-error" role="alert">{{ chatState.sessionError }}</p><NButton quaternary size="small" :disabled="chatState.busy || chatState.loading" @click="loadSessions(true)">刷新历史</NButton><CommunityDialog v-if="pending" title="删除这段对话？" :busy="deleting" @close="closeDialog"><div class="paismart-history-confirm"><p>会话 {{ pending.id.slice(0, 8) }} 的全部问答、工具结果与引用会被永久删除，其他账号的对话不受影响。</p><div class="paismart-history-confirm-actions"><NButton quaternary size="small" :disabled="deleting" @click="closeDialog">取消</NButton><NButton type="error" size="small" :loading="deleting" @click="confirmDelete">永久删除</NButton></div></div></CommunityDialog></aside></template>
<style scoped>
.paismart-conversation-sidebar { display: flex; flex-direction: column; flex-shrink: 0; width: 228px; min-width: 0; padding: 18px 12px 12px; background: #f8f9fb; border-right: 1px solid #eef0f4; }
.paismart-conversation-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding-inline: 4px; }
.paismart-conversation-title h3 { font-size: 14px; font-weight: 600; }
.paismart-conversation-scope { color: #8d93a1; font-size: 11px; margin: 12px 4px 16px; }
.paismart-history-list { flex: 1; max-height: 460px; overflow-y: auto; }
.paismart-history-item { display: flex; align-items: center; gap: 2px; border-radius: 8px; margin-bottom: 3px; }
.paismart-history-item:hover { background: #f0f1f6; }
.paismart-history-item.selected { background: #646cff14; color: #555cd6; }
.paismart-history-select { display: flex; flex: 1; min-width: 0; align-items: center; gap: 10px; padding: 12px 4px 12px 8px; border-radius: 8px; text-align: left; }
.paismart-history-delete { display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; border-radius: 7px; color: #c2c7d1; }
.paismart-history-delete:hover:not(:disabled) { color: #d9534f; background: #fdecec; }
.paismart-history-delete:disabled { opacity: .45; }
.paismart-history-delete .community-icon { width: 15px; height: 15px; }
.paismart-history-icon { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 7px; background: #e9ebf2; color: #9297a4; flex-shrink: 0; }
.paismart-history-icon .community-icon { width: 17px; height: 17px; }
.paismart-history-list strong { display: block; font-size: 11px; font-weight: 500; }
.paismart-history-list small { display: block; font-size: 11px; color: #a0a5b0; margin-top: 4px; }
.paismart-history-list > p { text-align: center; padding: 40px 4px; color: #a0a5b0; font-size: 12px; }
.paismart-history-confirm { display: grid; gap: 18px; padding: 4px 4px 8px; }
.paismart-history-confirm p { font-size: 13px; line-height: 1.8; color: #5b6070; }
.paismart-history-confirm-actions { display: flex; justify-content: flex-end; gap: 10px; }
@media (max-width: 980px) { .paismart-conversation-sidebar { display: none; } .paismart-conversation-sidebar.open { display: flex; width: 100%; max-height: 300px; border-right: 0; border-bottom: 1px solid #eef0f4; } }
</style>
