<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request, errorMessage, displayTime, type Activity } from '../api';
const activities = ref<Activity[]>([]);
const error = ref('');
const loading = ref(true);
onMounted(async () => {
  try { activities.value = await request<Activity[]>('/activities'); }
  catch (e) { error.value = errorMessage(e); }
  finally { loading.value = false; }
});
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">活动列表</p><h1>活动信息与报名入口</h1><p class="intro">当前只提供示例信息查询。发布、取消、报名与人数校验待实现。</p></div></div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <section class="panel table-scroll"><table><thead><tr><th>活动名称</th><th>时间</th><th>地点</th><th>人数上限</th><th>状态</th></tr></thead><tbody><tr v-for="activity in activities" :key="activity.id"><td>{{ activity.title }}</td><td>{{ displayTime(activity.startTime) }}</td><td>{{ activity.location }}</td><td>{{ activity.capacity }}</td><td><span class="badge neutral">模拟样本</span></td></tr></tbody></table><p v-if="loading" class="empty">正在读取活动…</p><p v-if="!loading && !error && !activities.length" class="empty">暂无活动数据。</p></section>
  <section class="panel"><h2>接下来补齐的规则</h2><p>发布前校验必填字段；报名时检查活动状态、截止时间和剩余名额；取消活动后保留变更记录并通知报名人员。</p></section>
</template>
