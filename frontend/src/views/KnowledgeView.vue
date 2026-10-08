<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request, errorMessage, displayTime, type KnowledgeDocument } from '../api';
const documents = ref<KnowledgeDocument[]>([]);
const file = ref<File>();
const fileInput = ref<HTMLInputElement>();
const busy = ref(false);
const error = ref('');
const success = ref('');
const preview = ref<{ document: KnowledgeDocument; content: string }>();

async function load() { documents.value = await request<KnowledgeDocument[]>('/ai/knowledge'); }
onMounted(async () => { try { await load(); } catch (e) { error.value = errorMessage(e); } });
function select(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; }
async function upload() {
  if (!file.value) return;
  busy.value = true; error.value = ''; success.value = '';
  try {
    const form = new FormData(); form.append('file', file.value);
    const result = await request<KnowledgeDocument>('/ai/knowledge', { method: 'POST', body: form });
    success.value = `已处理 ${result.name}，共 ${result.chunkCount} 个资料片段。`;
    await load(); file.value = undefined;
    if (fileInput.value) fileInput.value.value = '';
  } catch (e) { error.value = errorMessage(e); }
  finally { busy.value = false; }
}
async function show(id: number) {
  try { preview.value = await request(`/ai/knowledge/${id}`); }
  catch (e) { error.value = errorMessage(e); }
}
</script>

<template>
  <div class="page-heading"><div><h1>资料知识库</h1><p class="intro">支持 UTF-8 编码的 Markdown 和文本文件，最大 128KB。当前资料均按公开演示资料处理。</p></div><RouterLink to="/chat" class="button secondary">资料问答</RouterLink></div>
  <section class="panel"><h2>上传资料</h2><p>适合社团介绍、招新说明和活动指引。PDF、Word、私有资料权限和异步处理待后续实现。</p><form class="upload-row" @submit.prevent="upload"><label class="file-picker">选择 .md / .txt 文件<input ref="fileInput" type="file" accept=".md,.txt" :disabled="busy" @change="select" /></label><span class="muted">{{ file?.name || '尚未选择文件' }}</span><button class="button" type="submit" :disabled="!file || busy">{{ busy ? '处理中…' : '上传并处理' }}</button></form><p class="note">本地模式只分块并保存文本；真实模型模式会调用 Embedding 接口生成向量。</p></section>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="success" class="success" role="status">{{ success }}</p>
  <section class="panel table-scroll"><div class="section-heading"><h2>已保存资料</h2><span class="muted">{{ documents.length }} 份</span></div><table><thead><tr><th>资料名称</th><th>分块</th><th>向量状态</th><th>上传时间</th><th>原文</th></tr></thead><tbody><tr v-for="doc in documents" :key="doc.id"><td>{{ doc.name }}</td><td>{{ doc.chunkCount }}</td><td>{{ doc.embeddingVersion === 'local-keyword' ? '文本检索' : '真实向量' }}</td><td>{{ displayTime(doc.createdAt) }}</td><td><button class="text-link" @click="show(doc.id)">查看</button></td></tr></tbody></table><p v-if="!documents.length" class="empty">尚无资料，上传后可以开始问答。</p></section>
  <section v-if="preview" class="panel"><div class="section-heading"><h2>{{ preview.document.name }}</h2><button class="button secondary" @click="preview = undefined">收起</button></div><pre class="source-text">{{ preview.content }}</pre></section>
</template>
