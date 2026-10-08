<script setup lang="ts">
import { computed, ref } from 'vue';
import { categories } from './data';
import { clubCatalog as clubs } from './business';
import { state, dateLabel } from './store';
import CommunityIcon from '../community/CommunityIcon.vue';
const category = ref('全部'); const keyword = ref(''); const openOnly = ref(false);
const activities = computed(() => state.activities.filter(a => a.status === 'published' && (category.value === '全部' || a.category === category.value) && (!openOnly.value || a.enrolled < a.capacity) && `${a.title} ${a.location} ${clubs.value.find(c => c.id === a.clubId)?.name}`.includes(keyword.value.trim())).sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`)));
</script>
<template>
  <div class="p-page-heading"><h1>活动</h1><p>查看社团近期活动，选择适合自己的时间和内容。</p></div>
  <label class="community-search"><CommunityIcon name="search" /><input v-model="keyword" aria-label="搜索活动" placeholder="搜索活动名称、地点或社团" /><button v-if="keyword" type="button" aria-label="清除搜索" @click="keyword = ''"><CommunityIcon name="close" /></button></label>
  <div class="p-filter-chips" aria-label="活动类别筛选"><button v-for="item in categories" :key="item" type="button" :aria-pressed="category === item" :class="{ selected: category === item }" @click="category = item">{{ item }}</button></div>
  <div class="community-prototype-event-toolbar"><span>共 {{ activities.length }} 场活动</span><label class="p-checkbox"><input v-model="openOnly" type="checkbox" />只看有名额</label></div>
  <RouterLink v-for="activity in activities" :key="activity.id" :to="`/activities/${activity.id}`" class="community-prototype-event-row"><span class="community-event-cover"><span>{{ new Date(activity.date + 'T00:00:00').getMonth() + 1 }} 月</span><strong>{{ Number(activity.date.slice(8)) }}</strong></span><div><h2>{{ activity.title }}</h2><p>{{ clubs.find(c => c.id === activity.clubId)?.name }} · {{ activity.category }}</p><p>{{ dateLabel(activity.date) }} {{ activity.time }} · {{ activity.location }}</p><div class="community-prototype-event-meta"><span>{{ activity.enrolled }} / {{ activity.capacity }} 人</span><span class="community-recruiting" :class="{ ended: activity.enrolled >= activity.capacity }">{{ state.registrations.includes(activity.id) ? '已报名' : activity.enrolled >= activity.capacity ? '名额已满' : '查看并报名' }}</span></div></div><CommunityIcon name="chevron" /></RouterLink>
  <div v-if="!activities.length" class="p-empty"><h2>暂时没有找到匹配活动</h2><p>换个关键词或选择其他类别。</p><button type="button" class="p-button secondary" @click="keyword = ''; category = '全部'; openOnly = false">清除筛选</button></div>
  <p class="community-login-intro">交互原型 · 非真实报名，活动与人数均为虚构演示数据。</p>
</template>
