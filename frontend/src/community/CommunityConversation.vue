<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { business, businessMode } from '../prototype/business';
import { displayTime } from '../api';
import { chatState, loadSessions, loadHistory, newConversation, sendQuestion } from './chat';
const props = defineProps<{ initialChannel?: 'chat' | 'assistant' }>();
const channel = ref<'chat' | 'assistant'>(props.initialChannel || 'assistant'), question = ref('');
watch(() => business.user?.id, () => { question.value = ''; loadSessions(true); }, { flush: 'sync' });
onMounted(() => loadSessions(true));
async function send() { const text = question.value; if (await sendQuestion(text, channel.value) && question.value === text) question.value = ''; }
const toolNames: Record<string, string> = { search_clubs: '查询社团', get_club: '读取社团详情', search_activities: '查询活动', get_activity: '读取活动详情', my_registrations: '查询本人报名', search_knowledge: '检索可见资料' };
</script>
<template><section class="community-conversation" aria-label="账号专属 AI 对话">
  <div class="community-section-heading"><h2>{{ initialChannel === 'chat' ? '资料问答' : '社团与活动助手' }}</h2><button type="button" class="community-text-button" :disabled="chatState.busy || chatState.loading" @click="newConversation">新建会话</button></div>
  <p v-if="!businessMode" class="community-section-description">纯前端原型不调用模型或读取真实会话。</p>
  <p v-else-if="!business.user" class="community-section-description">登录后可查询社团、活动和本人记录，会话只归当前账号。<RouterLink to="/login">前往登录</RouterLink></p>
  <template v-else>
    <div class="community-category-tabs" role="group" aria-label="助手查询方式"><button type="button" :class="{ active: channel === 'assistant' }" :aria-pressed="channel === 'assistant'" :disabled="chatState.busy" @click="channel = 'assistant'">社团与活动</button><button type="button" :class="{ active: channel === 'chat' }" :aria-pressed="channel === 'chat'" :disabled="chatState.busy" @click="channel = 'chat'">资料问答</button></div>
    <p class="community-section-description">业务查询使用实时只读工具，资料问答只检索你可见的资料。模型不会替你报名、审批或发布；回答仍需核对来源。</p>
    <label class="conversation-history">历史会话<select :value="chatState.conversationId" :disabled="chatState.busy || chatState.loading" @change="($event.target as HTMLSelectElement).value ? loadHistory(($event.target as HTMLSelectElement).value) : newConversation()"><option value="">新会话</option><option v-for="item in chatState.sessions" :key="item.id" :value="item.id">{{ displayTime(item.createdAt) }} · {{ item.id.slice(0, 8) }}</option></select></label>
    <p v-if="chatState.sessionError" class="community-error" role="alert">历史列表：{{ chatState.sessionError }} <button type="button" class="community-text-button" @click="loadSessions()">重试读取</button></p>
    <p v-if="chatState.loading" role="status">正在读取当前账号的会话…</p>
    <div class="conversation-turns" aria-live="polite"><article v-for="(turn, index) in chatState.turns" :key="index"><h3>你的问题</h3><p class="conversation-question">{{ turn.question }}</p><h3>助手回答</h3><p class="conversation-answer">{{ turn.answer }}</p><p class="conversation-method">{{ turn.retrieval === 'REACT_TOOLS' ? '模型选择只读工具 · 最多 3 次调用' : turn.retrieval === 'FIXED_TOOLS' ? '本地固定工具查询 · 未启用 ReAct 模型' : turn.mode === 'LOCAL' ? '本地资料摘录 · 未调用模型' : '检索资料后由模型生成' }}</p>
      <details v-if="turn.steps?.length"><summary>查看 {{ turn.steps.length }} 次工具查询及真实结果</summary><div v-for="(step, i) in turn.steps" :key="i"><h4>{{ toolNames[step.tool] || step.tool }}{{ step.query ? `：${step.query}` : '' }}{{ step.id ? `（ID ${step.id}）` : '' }}</h4><pre>{{ step.observation }}</pre></div></details>
      <details v-for="(source, i) in turn.references" :key="`${source.documentId}-${source.chunkNumber}`"><summary>[{{ i + 1 }}] {{ source.documentName }} · 片段 {{ source.chunkNumber + 1 }}</summary><pre>{{ source.excerpt }}</pre></details>
    </article></div>
    <p v-if="!chatState.turns.length && !chatState.loading" class="community-section-description">例如：“摄影社有什么招新条件？”“查一下近期活动和我的报名。”</p>
    <p v-if="chatState.error" class="community-error" role="alert">{{ chatState.error }}</p>
    <form @submit.prevent="send"><label for="assistant-question">输入问题<textarea id="assistant-question" v-model="question" required maxlength="2000" rows="3" :disabled="chatState.busy || chatState.loading" /></label><div class="community-dialog-actions"><button class="community-button" :disabled="chatState.busy || chatState.loading || !question.trim()">{{ chatState.busy ? '正在查询与整理…' : '发送问题' }}</button><button type="button" class="community-text-button" :disabled="chatState.busy || chatState.loading" @click="loadSessions(true)">刷新历史</button></div><p v-if="chatState.busy" role="status">本机模型可能需要一些时间，请勿重复提交。校园业务仍可独立使用。</p></form>
  </template>
</section></template>
<style scoped>
.community-conversation { padding: 24px 0; border-bottom: 1px solid var(--community-line); overflow-wrap: anywhere; }
.community-conversation label { display: grid; gap: 8px; font-size: 15px; }
.community-conversation :is(select, textarea) { width: 100%; min-width: 0; min-height: 44px; padding: 12px; border: 1px solid var(--community-line); border-radius: 4px; background: white; color: var(--community-text); font: inherit; font-size: 16px; }
.conversation-turns article { padding: 24px 0; border-bottom: 1px solid var(--community-line); }
.conversation-turns h3 { font-size: 14px; color: var(--community-muted); margin: 12px 0; }
.conversation-question, .conversation-answer { white-space: pre-wrap; line-height: 1.9; margin: 12px 0; }
.conversation-question { padding: 12px; background: var(--community-bg); }
.conversation-method { font-size: 12px; color: var(--community-muted); }
.community-conversation details { margin: 12px 0; }
.community-conversation summary { cursor: pointer; min-height: 44px; padding: 10px 0; }
.community-conversation pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 320px; overflow-y: auto; padding: 12px; background: var(--community-bg); font-size: 13px; line-height: 1.8; }
.community-conversation form { margin-top: 20px; }
</style>
