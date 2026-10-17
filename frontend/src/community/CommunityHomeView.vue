<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { business, businessMode, clubCatalog, refreshBusiness } from '../prototype/business';
import { state, dateLabel } from '../prototype/store';
import { activityState, refreshActivities, activityTime, registrationClosed } from './activities';
import CommunityIcon from './CommunityIcon.vue';
import CommunityBannerCarousel from './CommunityBannerCarousel.vue';
const recruiting = computed(() => clubCatalog.value.filter(c => c.recruiting).slice(0, 4));
const upcoming = computed(() => businessMode ? activityState.activities.filter(a => new Date(a.startTime).getTime() > Date.now()).slice(0, 4).map(a => ({ id: String(a.id), title: a.title, time: activityTime(a.startTime), date: a.startTime.slice(5, 10), location: a.location, full: a.enrolled >= a.capacity, closed: registrationClosed(a), club: clubCatalog.value.find(c => c.backendId === a.clubId)?.name }))
  : state.activities.filter(a => a.status === 'published' && new Date(a.date + 'T' + a.time).getTime() > Date.now()).slice(0, 4).map(a => ({ id: a.id, title: a.title, time: dateLabel(a.date) + ' ' + a.time, date: a.date.slice(5, 10), location: a.location, full: a.enrolled >= a.capacity, closed: false, club: clubCatalog.value.find(c => c.id === a.clubId)?.name })));
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
</script>
<template>
  <div class="community-directory community-discover paismart-home-discover">
    <header class="paismart-home-header"><div><h1>发现</h1><p>{{ business.user ? business.user.name + '，欢迎回来。' : '欢迎来到社遇。' }}在这里了解社团，参与校园活动。</p></div><RouterLink to="/clubs?recruiting=true">查看招新社团</RouterLink></header>
    <CommunityBannerCarousel />
    <div class="paismart-home-columns">
      <section class="paismart-home-panel" aria-labelledby="discover-clubs-title"><div class="community-section-heading"><h2 id="discover-clubs-title">正在招新的社团</h2><RouterLink to="/clubs?recruiting=true">查看全部<CommunityIcon name="chevron" /></RouterLink></div>
        <div v-if="businessMode && business.error" class="community-list-message" role="alert"><h3>社团暂时加载失败</h3><p>{{ business.error }}</p><button type="button" @click="refreshBusiness">重新加载</button></div><p v-else-if="businessMode && business.loading" class="community-list-loading" role="status">正在加载社团…</p>
        <div v-else class="paismart-home-clubs"><RouterLink v-for="club in recruiting" :key="club.id" :to="'/clubs/' + club.id" class="paismart-home-club"><div class="paismart-home-club-header"><span class="community-club-cover" :class="'cover-' + club.id"><CommunityIcon :name="club.id" /></span><div><h3>{{ club.name }}</h3><small>{{ club.category }}</small></div></div><p>{{ club.description }}</p><span><span>{{ club.members }} 位成员</span><span>了解社团 ›</span></span></RouterLink><div v-if="!recruiting.length" class="community-list-message"><h3>暂无正在招新的社团</h3><RouterLink to="/clubs">浏览全部社团</RouterLink></div></div>
      </section>
      <section class="paismart-home-panel" aria-labelledby="discover-events-title"><div class="community-section-heading"><h2 id="discover-events-title">近期活动</h2><RouterLink to="/activities">查看全部<CommunityIcon name="chevron" /></RouterLink></div>
        <div v-if="businessMode && activityState.error" class="community-list-message" role="alert"><p>{{ activityState.error }}</p><button type="button" @click="refreshActivities">重新加载活动</button></div><p v-else-if="businessMode && activityState.loading" class="community-list-loading" role="status">正在加载活动…</p>
        <template v-else><RouterLink v-for="activity in upcoming" :key="activity.id" :to="'/activities/' + activity.id" class="paismart-home-event"><span class="paismart-event-date"><strong>{{ activity.date.slice(3) }}</strong>{{ activity.date.slice(0, 2) }} 月</span><div><h3>{{ activity.title }}</h3><p>{{ activity.time }} · {{ activity.location }}</p><p>{{ activity.club || '社团活动' }}</p><small>{{ activity.closed ? '报名截止' : activity.full ? '名额已满' : '开放报名' }}</small></div></RouterLink><div v-if="!upcoming.length" class="community-list-message"><CommunityIcon name="calendar" /><h3>暂无即将开始的活动</h3><p>社团发布新活动后会显示在这里。</p></div></template>
      </section>
    </div>
    <p class="community-list-disclosure">{{ businessMode ? '本地开发 · 社团和活动来自数据库，所有资料均为虚构。' : '交互原型 · 虚构数据 · 非真实报名。' }}</p>
  </div>
</template>
