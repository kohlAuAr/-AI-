<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { business, businessMode, managedClubs } from '../prototype/business';
import { request, errorMessage, displayTime, type KnowledgeDocument } from '../api';
const documents = ref<KnowledgeDocument[]>([]), file = ref<File>(), fileInput = ref<HTMLInputElement>();
const busy = ref(false), loading = ref(false), error = ref(''), success = ref('');
const visibility = ref('CLUB'), clubId = ref<number | null>(business.managedClubId);
const preview = ref<{ document: KnowledgeDocument; content: string }>(); let generation = 0;
const canUpload = computed(() => business.user?.role === 'PLATFORM_ADMIN' || business.user?.role === 'MANAGER' && managedClubs.value.length > 0);
function clear() { ++generation; documents.value = []; preview.value = undefined; file.value = undefined; if (fileInput.value) fileInput.value.value = ''; busy.value = false; loading.value = false; error.value = ''; success.value = ''; visibility.value = business.user?.role === 'PLATFORM_ADMIN' ? 'PUBLIC' : 'CLUB'; clubId.value = business.managedClubId; }
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], clear, { flush: 'sync' });
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], load);
onMounted(() => { clear(); load(); });
async function load() {
  if (!businessMode || !business.user) return;
  const owner = business.user.id, ticket = ++generation; loading.value = true; error.value = ''; documents.value = []; preview.value = undefined;
  try { const rows = await request<KnowledgeDocument[]>('/ai/knowledge'); if (ticket === generation && owner === business.user?.id) documents.value = rows; }
  catch (failure) { if (ticket === generation) error.value = errorMessage(failure); }
  finally { if (ticket === generation) loading.value = false; }
}
function select(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; }
async function upload() {
  if (!file.value || busy.value || !canUpload.value || !business.user) return;
  const owner = business.user.id, ticket = ++generation; busy.value = true; error.value = ''; success.value = '';
  try {
    const form = new FormData(); form.append('file', file.value); form.append('visibility', visibility.value);
    if (visibility.value === 'CLUB' && clubId.value !== null) form.append('clubId', String(clubId.value));
    const result = await request<KnowledgeDocument>('/ai/knowledge', { method: 'POST', body: form });
    if (ticket !== generation || owner !== business.user?.id) return;
    success.value = `已处理 ${result.name}，共 ${result.chunkCount} 个资料片段。`; file.value = undefined; if (fileInput.value) fileInput.value.value = '';
    busy.value = false; await load();
  } catch (failure) { if (ticket === generation && owner === business.user?.id) error.value = errorMessage(failure); }
  finally { if (ticket === generation) busy.value = false; }
}
async function show(id: number) {
  if (!business.user) return;
  const owner = business.user.id, ticket = generation; preview.value = undefined;
  try { const result = await request<{ document: KnowledgeDocument; content: string }>(`/ai/knowledge/${id}`); if (ticket === generation && owner === business.user?.id) preview.value = result; }
  catch (failure) { if (ticket === generation) error.value = errorMessage(failure); }
}
</script>
<template><div>
  <div class="page-heading"><div><h1>资料知识库</h1><p class="intro">支持 UTF-8 的 Markdown 和文本文件，最大 128KB。公开资料由平台管理员维护，社团资料仅该社团成员可见。</p></div><RouterLink to="/system/chat" class="button secondary">资料问答</RouterLink></div>
  <p v-if="!businessMode" class="note">纯前端原型不访问知识资料接口。</p>
  <p v-else-if="!business.user" class="note">请先登录查看你可见的资料。<RouterLink to="/login">前往登录</RouterLink></p>
  <template v-else>
    <section v-if="canUpload" class="panel"><h2>上传资料</h2><p>仅上传本项目获准使用的资料。模型启用时正文会发送到服务器配置的 Embedding 服务。</p>
      <form class="knowledge-form" @submit.prevent="upload"><label>可见范围<input :value="visibility === 'PUBLIC' ? '公开资料（登录用户可见）' : '社团成员资料'" readonly /></label><label v-if="visibility === 'CLUB'">所属社团<select v-model="clubId" :disabled="busy"><option v-for="club in managedClubs" :key="club.id" :value="club.backendId">{{ club.name }}</option></select></label>
      <label>选择 .md / .txt 文件<input ref="fileInput" type="file" accept=".md,.txt" :disabled="busy" @change="select" /></label><span class="muted">{{ file?.name || '尚未选择文件' }}</span><button class="button" :disabled="!file || busy">{{ busy ? '处理中…' : '上传并处理' }}</button></form>
    </section><p v-else class="note">学生可查看公开资料和已加入社团的资料；上传请联系社团负责人。</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="success" class="success" role="status">{{ success }}</p>
    <section class="panel table-scroll"><div class="section-heading"><h2>当前账号可见资料</h2><button type="button" class="button secondary" :disabled="busy || loading" @click="load">刷新资料</button></div><p v-if="loading" role="status">正在读取…</p><table v-else><thead><tr><th>资料名称</th><th>可见范围</th><th>分块</th><th>向量状态</th><th>上传时间</th><th>原文</th></tr></thead><tbody><tr v-for="doc in documents" :key="doc.id"><td>{{ doc.name }}</td><td>{{ doc.scope === 'PUBLIC' ? '公开' : `社团 ID ${doc.clubId}` }}</td><td>{{ doc.chunkCount }}</td><td>{{ doc.embeddingVersion === 'local-keyword' ? '文本检索' : '真实向量' }}</td><td>{{ displayTime(doc.createdAt) }}</td><td><button class="text-link" @click="show(doc.id)">查看</button></td></tr></tbody></table><p v-if="!loading && !documents.length" class="empty">当前没有可见资料。</p></section>
    <section v-if="preview" class="panel"><div class="section-heading"><h2>{{ preview.document.name }}</h2><button class="button secondary" @click="preview = undefined">收起</button></div><pre class="source-text">{{ preview.content }}</pre></section>
  </template>
</div></template>
<style scoped>
.knowledge-form { display: grid; gap: 16px; margin-top: 16px; }
.knowledge-form label { display: grid; gap: 8px; }
.knowledge-form :is(input, select) { width: 100%; max-width: 100%; min-height: 44px; padding: 10px; border: 1px solid var(--community-line); border-radius: 4px; font: inherit; font-size: 16px; }
.knowledge-form .button { justify-self: start; }
</style>
