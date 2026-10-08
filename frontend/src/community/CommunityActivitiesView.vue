<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { business, clubCatalog } from '../prototype/business';
import { activityState, refreshActivities, activityTime, registrationClosed } from './activities';
import CommunityIcon from './CommunityIcon.vue';
const keyword = ref('');
const matches = computed(() => activityState.activities.filter(a => `${a.title} ${a.location} ${clubCatalog.value.find(c => c.backendId === a.clubId)?.name}`.includes(keyword.value.trim())));
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
</script>
<template><div class="community-directory">
  <div class="community-page-title"><h1>活动</h1><RouterLink :to="business.user?.role === 'MANAGER' ? '/manage/activities' : '/me?tab=registrations'">{{ business.user?.role === 'MANAGER' ? '管理活动' : '我的报名' }}<CommunityIcon name="chevron" /></RouterLink></div>
  <div class="community-search-wrap"><label class="community-search" for="activity-search"><CommunityIcon name="search" /><input id="activity-search" v-model="keyword" type="search" aria-label="搜索活动" placeholder="搜索活动、社团或地点" /></label></div>
  <div class="community-list-heading"><h2>已发布活动</h2><span v-if="!activityState.loading && !activityState.error" role="status">共 {{ matches.length }} 场</span></div>
  <div v-if="activityState.error" class="community-list-message" role="alert"><h3>活动加载失败</h3><p>{{ activityState.error }}</p><button @click="refreshActivities">重新加载</button></div>
  <p v-else-if="activityState.loading" class="community-list-loading" role="status">正在读取活动…</p>
  <template v-else><article v-for="activity in matches" :key="activity.id" class="community-club-row"><RouterLink :to="`/activities/${activity.id}`" class="community-club-link"><span class="community-club-cover community-event-cover"><span>{{ activity.startTime.slice(5, 7) }}月</span><strong>{{ activity.startTime.slice(8, 10) }}</strong></span><div class="community-club-copy"><div class="community-club-title"><h3>{{ activity.title }}</h3><span class="community-recruiting">{{ registrationClosed(activity) ? '已截止' : activity.enrolled >= activity.capacity ? '名额已满' : '报名中' }}</span></div><p class="community-club-description">{{ activity.description }}</p><div class="community-club-meta"><span>{{ clubCatalog.find(c => c.backendId === activity.clubId)?.name }}</span><span>{{ activity.enrolled }} / {{ activity.capacity }} 人</span></div><p class="community-club-meeting">{{ activityTime(activity.startTime) }}<span class="community-meeting-place">{{ activity.location }}</span></p></div><CommunityIcon name="chevron" /></RouterLink></article><div v-if="!matches.length" class="community-list-message"><h3>{{ keyword ? '没有找到匹配活动' : '暂时没有已发布活动' }}</h3><p>{{ keyword ? '换一个关键词试试。' : '负责人确认发布后，活动会出现在这里。' }}</p></div></template>
  <p class="community-list-disclosure">本地虚构账号与资料 · 发布和报名记录保存在校园数据库<br />活动时间按校园本地时间填写，面向全校学生报名。</p>
</div></template>
