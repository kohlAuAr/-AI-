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
  <div class="page-heading"><div><p class="eyebrow">活动列表</p><h1>活动信息与报名入口</h1><p class="intro">已发布活动来自数据库。学生端支持报名与取消，负责人支持草稿、确认发布及查看名单。</p></div></div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <section class="panel table-scroll"><table><thead><tr><th>活动名称</th><th>时间</th><th>地点</th><th>人数上限</th><th>状态</th></tr></thead><tbody><tr v-for="activity in activities" :key="activity.id"><td><RouterLink :to="`/activities/${activity.id}`">{{ activity.title }}</RouterLink></td><td>{{ displayTime(activity.startTime) }}</td><td>{{ activity.location }}</td><td>{{ activity.capacity }}</td><td><span class="badge neutral">已发布 · 本地虚构资料</span></td></tr></tbody></table><p v-if="loading" class="empty">正在读取活动…</p><p v-if="!loading && !error && !activities.length" class="empty">暂无已发布活动。</p></section>
  <section class="panel"><h2>活动业务入口</h2><RouterLink to="/activities">学生端活动列表</RouterLink><p>报名与取消通知已接入消息中心。活动编辑、下架和签到待实现。</p></section>
</template>
