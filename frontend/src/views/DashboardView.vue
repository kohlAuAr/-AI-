<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request, errorMessage, type SystemInfo, type AiStatus } from '../api';

const system = ref<SystemInfo>();
const ai = ref<AiStatus>();
const campusError = ref('');
const aiError = ref('');
const loading = ref(false);

async function load() {
  loading.value = true;
  campusError.value = ''; aiError.value = '';
  system.value = undefined; ai.value = undefined;
  await Promise.all([
    request<SystemInfo>('/system').then(value => system.value = value).catch(error => campusError.value = errorMessage(error)),
    request<AiStatus>('/ai/status').then(value => ai.value = value).catch(error => aiError.value = errorMessage(error))
  ]);
  loading.value = false;
}
onMounted(load);
</script>

<template>
  <div class="page-heading"><div><h1>项目概览</h1><p class="intro">业务和智能能力分别运行，通过接口协作。这里可以查看联通情况和当前功能边界。</p></div><button class="button secondary" :disabled="loading" @click="load">{{ loading ? '检查中…' : '刷新状态' }}</button></div>
  <div class="stats-grid">
    <section class="stat-card"><span>校园业务服务</span><strong>{{ system ? '已联通' : '未联通' }}</strong><p>8090 · 社团、活动与 AI 请求入口</p></section>
    <section class="stat-card"><span>智能辅助服务</span><strong>{{ ai ? '已联通' : '未联通' }}</strong><p>8091 · 资料、检索与会话记录</p></section>
    <section class="stat-card"><span>问答运行模式</span><strong>{{ ai ? (ai.mode === 'LOCAL' ? '本地检索' : ai.mode === 'OLLAMA' ? '本机 Ollama' : '接口模型') : '待检查' }}</strong><p>{{ !ai ? '尚未取得服务配置状态' : ai.mode === 'LOCAL' ? '不调用大模型，返回原文摘录' : '检索资料并调用模型；联通不代表模型健康检查通过' }}</p></section>
  </div>
  <p v-if="campusError" class="error" role="alert">校园服务：{{ campusError }}</p>
  <p v-if="aiError" class="error" role="alert">智能服务：{{ aiError }}</p>
  <section class="panel"><div class="section-heading"><h2>校园业务入口</h2><RouterLink to="/roadmap" class="text-link">查看后续开发 →</RouterLink></div>
    <div class="flow-grid"><article><span class="flow-index">招新</span><h3>发现社团 → 申请 → 审核 → 入社</h3><p>学生提交申请，负责人审核，审核通过后建立成员关系。当前流程已接通数据库。</p></article><article><span class="flow-index">活动</span><h3>编辑草稿 → 发布 → 报名 → 名单</h3><p>负责人保存草稿并确认发布，学生报名，负责人查看名单。当前页面不包含 AI 自动生成草稿。</p></article></div>
  </section>
  <section class="panel"><div class="section-heading"><h2>当前模块</h2><span class="muted">真实状态，不代表全部功能已完成</span></div>
    <div v-if="system" class="module-list"><article v-for="module in system.modules" :key="module.key"><div><h3>{{ module.name }}</h3><p>{{ module.description }}</p></div><span class="badge" :class="module.status === 'READY' ? 'ready' : 'neutral'">{{ module.status === 'READY' ? '已实现基础链路' : module.status === 'SAMPLE' ? '示例只读' : '待实现' }}</span></article></div>
    <p v-else class="empty">启动校园服务后显示模块状态。</p>
  </section>
</template>
