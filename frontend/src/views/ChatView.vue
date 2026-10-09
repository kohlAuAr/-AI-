<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { request, errorMessage, type Turn, type Reply, type AiStatus } from '../api';
const conversationId = ref(sessionStorage.getItem('campus-conversation') || '');
const turns = ref<Turn[]>([]);
const question = ref('');
const ai = ref<AiStatus>();
const error = ref('');
const busy = ref(false);
const bottom = ref<HTMLElement>();
const examples = ['程序设计社适合零基础学生吗？', '活动策划材料需要说明哪些内容？'];

onMounted(async () => {
  try {
    ai.value = await request<AiStatus>('/ai/status');
    if (conversationId.value) turns.value = await request<Turn[]>(`/ai/conversations/${conversationId.value}`);
  } catch (e) { error.value = errorMessage(e); }
});
async function send() {
  if (!question.value.trim() || busy.value) return;
  busy.value = true; error.value = '';
  const currentQuestion = question.value.trim();
  try {
    const reply = await request<Reply>('/ai/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: currentQuestion, conversationId: conversationId.value || null }) });
    conversationId.value = reply.conversationId;
    sessionStorage.setItem('campus-conversation', reply.conversationId);
    turns.value.push({ ...reply, question: currentQuestion });
    question.value = '';
    await nextTick(); bottom.value?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  } catch (e) { error.value = errorMessage(e); }
  finally { busy.value = false; }
}
function reset() { conversationId.value = ''; turns.value = []; error.value = ''; sessionStorage.removeItem('campus-conversation'); }
</script>

<template>
  <div class="page-heading"><div><h1>资料问答</h1><p class="intro">{{ !ai ? '正在确认问答模式；请以服务状态为准。' : ai.mode === 'LOCAL' ? '本地演示模式：关键词检索与原文摘录，不调用大模型。' : ai.mode === 'OLLAMA' ? '本机模型模式：检索资料后，由 Ollama 模型整理回答。' : '接口模型模式：检索资料后，由配置的模型整理回答。' }}</p><p class="muted">模型回答可能有误，请展开来源核对。当前资料仅用于公开测试。</p></div><button class="button secondary" :disabled="busy" @click="reset">新建会话</button></div>
  <section v-if="!turns.length" class="panel welcome"><h2>你想了解什么？</h2><p>可以先试试示例问题，也可以上传自己的公开测试资料。</p><div class="example-questions"><button v-for="example in examples" :key="example" class="button secondary" @click="question = example">{{ example }}</button></div></section>
  <div class="conversation"><article v-for="(turn, index) in turns" :key="index" class="turn"><div class="user-message"><span>你的问题</span><p>{{ turn.question }}</p></div><div class="panel answer"><div class="section-heading"><h3>资料助手</h3><span class="badge neutral">{{ !turn.references.length ? '未找到依据' : turn.mode === 'LOCAL' ? '本地摘录' : 'AI 生成回答' }} · {{ turn.retrieval === 'KEYWORD' ? '关键词检索' : '混合检索' }}</span></div><div class="answer-text">{{ turn.answer }}</div><div v-if="turn.references.length" class="references"><h4>本次检索来源（不代表回答已通过审核）</h4><details v-for="(citation, i) in turn.references" :key="`${citation.documentId}-${citation.chunkNumber}`"><summary>[{{ i + 1 }}] {{ citation.documentName }} · 片段 {{ citation.chunkNumber + 1 }}</summary><pre class="source-text">{{ citation.excerpt }}</pre></details></div></div></article><div ref="bottom"></div></div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <form class="panel chat-input" @submit.prevent="send"><label class="field-label" for="question">输入问题</label><textarea id="question" v-model="question" maxlength="2000" rows="3" placeholder="例如：程序设计社适合零基础学生吗？" :disabled="busy"></textarea><div class="card-actions"><span class="muted">当前只读问答，不会申请、报名或发布活动。</span><button class="button" type="submit" :disabled="busy || !question.trim()">{{ busy ? '检索与回答中…' : '发送问题' }}</button></div></form>
</template>
