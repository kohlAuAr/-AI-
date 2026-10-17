<!-- Adapted from PaiSmart views/chat/modules/conversation-sidebar.vue; only existing campus history actions. -->
<script setup lang="ts">
import { NButton } from 'naive-ui';
import { chatState, loadHistory, loadSessions, newConversation } from '../community/chat';
import CommunityIcon from '../community/CommunityIcon.vue';
import { displayTime } from '../api';
defineProps<{ open: boolean }>();
</script>
<template><aside class="paismart-conversation-sidebar" :class="{ open }" aria-label="本人历史会话"><div class="paismart-conversation-title"><h3>对话列表</h3><NButton secondary type="primary" size="small" :disabled="chatState.busy || chatState.loading" @click="newConversation">新对话</NButton></div><p class="paismart-conversation-scope">仅当前登录账号可见</p><div class="paismart-history-list"><button v-for="item in chatState.sessions" :key="item.id" type="button" :disabled="chatState.busy || chatState.loading" :aria-pressed="chatState.conversationId === item.id" :class="{ selected: chatState.conversationId === item.id }" @click="loadHistory(item.id)"><span class="paismart-history-icon"><CommunityIcon name="assistant" /></span><span><strong>{{ displayTime(item.createdAt) }}</strong><small>会话 {{ item.id.slice(0, 8) }}</small></span></button><p v-if="!chatState.sessions.length">暂无对话记录</p></div><p v-if="chatState.sessionError" class="community-error" role="alert">{{ chatState.sessionError }}</p><NButton quaternary size="small" :disabled="chatState.busy || chatState.loading" @click="loadSessions(true)">刷新历史</NButton></aside></template>
<style scoped>
.paismart-conversation-sidebar { display: flex; flex-direction: column; flex-shrink: 0; width: 228px; min-width: 0; padding: 18px 12px 12px; background: #f8f9fb; border-right: 1px solid #eef0f4; }
.paismart-conversation-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding-inline: 4px; }
.paismart-conversation-title h3 { font-size: 14px; font-weight: 600; }
.paismart-conversation-scope { color: #8d93a1; font-size: 11px; margin: 12px 4px 16px; }
.paismart-history-list { flex: 1; max-height: 460px; overflow-y: auto; }
.paismart-history-list > button { display: flex; width: 100%; align-items: center; gap: 10px; padding: 12px 8px !important; border-radius: 8px; text-align: left; margin-bottom: 3px; }
.paismart-history-list > button:hover { background: #f0f1f6; }
.paismart-history-list > button.selected { background: #646cff14; color: #555cd6; }
.paismart-history-icon { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 7px; background: #e9ebf2; color: #9297a4; flex-shrink: 0; }
.paismart-history-icon .community-icon { width: 17px; height: 17px; }
.paismart-history-list strong { display: block; font-size: 11px; font-weight: 500; }
.paismart-history-list small { display: block; font-size: 11px; color: #a0a5b0; margin-top: 4px; }
.paismart-history-list > p { text-align: center; padding: 40px 4px; color: #a0a5b0; font-size: 12px; }
@media (max-width: 980px) { .paismart-conversation-sidebar { display: none; } .paismart-conversation-sidebar.open { display: flex; width: 100%; max-height: 300px; border-right: 0; border-bottom: 1px solid #eef0f4; } }
</style>
