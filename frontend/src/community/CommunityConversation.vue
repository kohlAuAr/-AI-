<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import { business, businessMode } from '../prototype/business';
import { chatState, loadSessions, newConversation, sendQuestion } from './chat';
import InputBox from '../paismart/InputBox.vue';
import ConversationSidebar from '../paismart/ConversationSidebar.vue';
import CommunityIcon from './CommunityIcon.vue';
const props = defineProps<{ initialChannel?: 'chat' | 'assistant' }>();
const channel = ref<'chat' | 'assistant'>(props.initialChannel || 'assistant'), question = ref(''), historyOpen = ref(false);
const messageList = ref<HTMLElement>();
watch(() => chatState.turns.length, async () => { await nextTick(); messageList.value?.scrollTo({ top: messageList.value.scrollHeight }); });
watch(() => business.user?.id, () => { question.value = ''; historyOpen.value = false; loadSessions(true); }, { flush: 'sync' });
onMounted(() => loadSessions(true));
async function send() { const text = question.value; if (await sendQuestion(text, channel.value) && question.value === text) question.value = ''; }
const toolNames: Record<string, string> = { search_clubs: '查询社团', get_club: '读取社团详情', search_activities: '查询活动', get_activity: '读取活动详情', my_registrations: '查询本人报名', search_knowledge: '检索可见资料' };
</script>
<template>
  <section class="community-conversation paismart-chat-window" aria-label="账号专属 AI 对话">
    <div v-if="!businessMode" class="paismart-chat-login"><h2>社团与活动助手</h2><p>纯前端原型不调用模型或读取真实会话。</p></div>
    <div v-else-if="!business.user" class="paismart-chat-login"><span class="paismart-chat-mark"><CommunityIcon name="assistant" /></span><h2>社团与活动助手</h2><p>登录后可查询社团、活动和本人记录，会话只归当前账号。</p><RouterLink to="/login" class="community-button">登录后开始对话</RouterLink></div>
    <template v-else>
      <ConversationSidebar :open="historyOpen" />
      <div class="paismart-chat-main">
        <header class="paismart-chat-toolbar"><div class="paismart-chat-modes" role="group" aria-label="助手查询方式"><button type="button" :class="{ active: channel === 'assistant' }" :aria-pressed="channel === 'assistant'" :disabled="chatState.busy" @click="channel = 'assistant'">社团与活动</button><button type="button" :class="{ active: channel === 'chat' }" :aria-pressed="channel === 'chat'" :disabled="chatState.busy" @click="channel = 'chat'">资料问答</button></div><div class="paismart-chat-actions"><button type="button" class="paismart-show-history" :aria-expanded="historyOpen" @click="historyOpen = !historyOpen">历史</button><button type="button" :disabled="chatState.busy || chatState.loading" @click="newConversation">新建会话</button></div></header>
        <div ref="messageList" class="paismart-message-list" aria-live="polite" :aria-busy="chatState.busy || chatState.loading">
          <p v-if="chatState.loading" role="status">正在读取当前账号的会话…</p>
          <div v-else-if="!chatState.turns.length" class="paismart-chat-empty"><span class="paismart-chat-mark"><CommunityIcon name="assistant" /></span><h2>有什么想了解的？</h2><p>查询社团、了解活动，或从资料里寻找答案。</p><button type="button" @click="question = '摄影社有什么招新条件？'">摄影社有什么招新条件？</button><button type="button" @click="question = '查询我报名过的活动'">查询我报名过的活动</button></div>
          <article v-for="(turn, index) in chatState.turns" :key="index" class="paismart-chat-turn"><div class="paismart-question-row"><p>{{ turn.question }}</p><span class="paismart-person-icon"><CommunityIcon name="user" /></span></div><div class="paismart-answer-row"><span class="paismart-answer-icon"><CommunityIcon name="assistant" /></span><div><h3>社团助手</h3><p class="paismart-answer-content">{{ turn.answer }}</p><small class="paismart-answer-mode">{{ turn.retrieval === 'REACT_TOOLS' ? '模型选择只读工具 · 最多 3 次调用' : turn.retrieval === 'FIXED_TOOLS' ? '本地固定工具查询 · 未启用 ReAct 模型' : turn.mode === 'LOCAL' ? '本地资料摘录 · 未调用模型' : '检索资料后由模型生成' }}</small><details v-if="turn.steps?.length"><summary>查看 {{ turn.steps.length }} 次工具查询及真实结果</summary><div v-for="(step, i) in turn.steps" :key="i"><h4>{{ toolNames[step.tool] || step.tool }}{{ step.query ? '：' + step.query : '' }}{{ step.id ? '（ID ' + step.id + '）' : '' }}</h4><pre>{{ step.observation }}</pre></div></details><details v-for="(source, i) in turn.references" :key="source.documentId + '-' + source.chunkNumber"><summary>[{{ i + 1 }}] {{ source.documentName }} · 片段 {{ source.chunkNumber + 1 }}</summary><pre>{{ source.excerpt }}</pre></details></div></div></article>
          <p v-if="chatState.busy" class="paismart-thinking" role="status">正在查询与整理…</p><p v-if="chatState.error" class="community-error" role="alert">{{ chatState.error }}</p>
        </div>
        <InputBox v-model="question" :busy="chatState.busy" :disabled="chatState.loading" @send="send" />
        <p class="paismart-chat-notice">业务查询使用实时只读工具，资料问答只检索你可见的资料。模型不会替你报名、审批或发布；回答仍需核对来源。</p>
      </div>
    </template>
  </section>
</template>
<style scoped>
.paismart-chat-window { display: flex; min-height: 560px; overflow: hidden; border-radius: 16px; background: #fff; box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 8px 28px rgba(15,23,42,.06); }
.paismart-chat-main { min-width: 0; flex: 1; display: flex; flex-direction: column; }
.paismart-chat-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid #f4f4f8; }
.paismart-chat-modes, .paismart-chat-actions { display: flex; gap: 8px; }
.paismart-chat-toolbar button { font-size: 12px; min-height: 36px; padding: 8px 10px; border-radius: 6px; color: #818797; }
.paismart-chat-modes button.active { color: #555cd6; background: #efefff; }
.paismart-show-history { display: none; }
.paismart-message-list { flex: 1; min-height: 320px; max-height: 600px; overflow-y: auto; padding: 24px; overflow-wrap: anywhere; }
.paismart-chat-empty, .paismart-chat-login { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; text-align: center; gap: 14px; margin: auto; }
.paismart-chat-empty h2, .paismart-chat-login h2 { font-size: 20px; font-weight: 500; }
.paismart-chat-empty > p, .paismart-chat-login > p { font-size: 13px; color: #949aa7; max-width: 360px; line-height: 1.8; }
.paismart-chat-empty > button { border: 1px solid #e9eaf3; border-radius: 8px; min-height: 40px; padding: 8px 14px; color: #747b8e; font-size: 12px; }
.paismart-chat-mark { display: grid; place-items: center; width: 60px; height: 60px; border-radius: 16px; background: #646cff12; color: #646cff; margin-bottom: 8px; }
.paismart-chat-mark > .community-icon { width: 30px; height: 30px; }
.paismart-question-row { display: flex; justify-content: flex-end; gap: 12px; margin-bottom: 28px; }
.paismart-question-row p { padding: 12px 18px; border-radius: 14px 0 14px 14px; background: #f0f1ff; font-size: 14px; max-width: 86%; white-space: pre-wrap; }
.paismart-person-icon, .paismart-answer-icon { display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; border-radius: 7px; background: #f4f5f8; color: #9c9fad; }
.paismart-answer-icon { color: #646cff; background: #efefff; }
.paismart-answer-row { display: flex; gap: 12px; margin-bottom: 32px; }
.paismart-answer-row > div { min-width: 0; flex: 1; }
.paismart-answer-row h3 { font-size: 12px; color: #969ca9; margin: 0 0 10px; font-weight: 400; }
.paismart-answer-content { white-space: pre-wrap; font-size: 14px; line-height: 1.9; }
.paismart-answer-mode { display: block; color: #959ba7; font-size: 11px; margin-top: 12px; }
.paismart-answer-row details { border: 1px solid #efeff5; border-radius: 6px; margin-top: 10px; padding: 0 10px; font-size: 12px; }
.paismart-answer-row summary { min-height: 38px; padding: 10px 0; cursor: pointer; color: #737b8c; }
.paismart-answer-row pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 280px; overflow-y: auto; background: #f8f9fb; padding: 12px; line-height: 1.8; }
.paismart-thinking { font-size: 13px; color: #646cff; }
.paismart-chat-notice { font-size: 11px; color: #9298a6; text-align: center; padding: 0 24px 16px; }
@media (max-width: 980px) { .paismart-chat-window { flex-direction: column; } .paismart-show-history { display: block; } }
@media (max-width: 760px) { .paismart-chat-window { min-height: 460px; } .paismart-chat-toolbar { padding: 10px; gap: 4px; } .paismart-chat-toolbar button { padding: 8px 6px; } .paismart-message-list { padding: 18px 14px; min-height: 240px; max-height: 45dvh; } .paismart-chat-empty { padding: 20px 0; gap: 10px; } .paismart-chat-empty h2, .paismart-chat-empty p { margin-bottom: 0; } .paismart-chat-mark { width: 44px; height: 44px; margin-bottom: 0; } .paismart-answer-content { font-size: 16px; } }
</style>
