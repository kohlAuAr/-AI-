<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { business, businessMode, clubCatalog, refreshBusiness } from '../prototype/business';
import { state, dateLabel } from '../prototype/store';
import { activityState, refreshActivities, activityTime, registrationClosed } from './activities';
import CommunityIcon from './CommunityIcon.vue';
const recruiting = computed(() => clubCatalog.value.filter(c => c.recruiting).slice(0, 3));
const upcoming = computed(() => businessMode ? activityState.activities.slice(0, 3).map(a => ({ id: String(a.id), title: a.title, time: activityTime(a.startTime), location: a.location, full: a.enrolled >= a.capacity, closed: registrationClosed(a), club: clubCatalog.value.find(c => c.backendId === a.clubId)?.name }))
  : state.activities.filter(a => a.status === 'published').slice(0, 3).map(a => ({ id: a.id, title: a.title, time: `${dateLabel(a.date)} ${a.time}`, location: a.location, full: a.enrolled >= a.capacity, closed: Date.now() >= new Date(`${a.date}T${a.time}`).getTime(), club: clubCatalog.value.find(c => c.id === a.clubId)?.name })));
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
</script>
<template>
  <div class="community-directory community-discover">
    <div class="community-page-title"><h1>发现</h1><RouterLink to="/messages">查看消息<CommunityIcon name="chevron" /></RouterLink></div>
    <p class="community-page-intro">看看校园里的社团，安排下一次活动。</p>
    <div class="community-discover-shortcuts">
      <RouterLink to="/recruitment"><CommunityIcon name="join" /><strong>社团招新</strong><span>查看加入条件</span></RouterLink>
      <RouterLink to="/activities"><CommunityIcon name="calendar" /><strong>近期活动</strong><span>找到想参加的活动</span></RouterLink>
      <RouterLink to="/me"><CommunityIcon name="user" /><strong>我的记录</strong><span>申请与报名进度</span></RouterLink>
    </div>
    <section aria-labelledby="discover-clubs-title">
      <div class="community-section-heading"><h2 id="discover-clubs-title">正在招新的社团</h2><RouterLink to="/recruitment">查看全部<CommunityIcon name="chevron" /></RouterLink></div>
      <div v-if="businessMode && business.error" class="community-list-message" role="alert"><h3>社团暂时加载失败</h3><p>{{ business.error }}</p><button type="button" @click="refreshBusiness">重新加载</button></div>
      <p v-else-if="businessMode && business.loading" class="community-list-loading" role="status">正在加载社团…</p>
      <template v-else>
        <article v-for="club in recruiting" :key="club.id" class="community-club-row">
          <RouterLink :to="`/clubs/${club.id}`" class="community-club-link"><span class="community-club-cover" :class="`cover-${club.id}`"><CommunityIcon :name="club.id" /></span><div class="community-club-copy"><div class="community-club-title"><h3>{{ club.name }}</h3><span class="community-recruiting">招新中</span></div><p class="community-club-description">{{ club.description }}</p><div class="community-club-meta"><span>{{ club.category }}</span><span>{{ club.members }} 位成员</span></div></div><CommunityIcon name="chevron" /></RouterLink>
        </article>
        <div v-if="!recruiting.length" class="community-list-message"><h3>暂无正在招新的社团</h3><p>可以先浏览社团资料，稍后再查看招新安排。</p><RouterLink to="/clubs" class="community-text-link">浏览全部社团</RouterLink></div>
      </template>
    </section>
    <section aria-labelledby="discover-events-title" class="community-discover-events">
      <div class="community-section-heading"><h2 id="discover-events-title">近期活动</h2><RouterLink to="/activities">查看全部<CommunityIcon name="chevron" /></RouterLink></div>
      <div v-if="businessMode && activityState.error" class="community-list-message" role="alert"><h3>活动暂时加载失败</h3><p>{{ activityState.error }}</p><button type="button" @click="refreshActivities">重新加载活动</button></div>
      <p v-else-if="businessMode && activityState.loading" class="community-list-loading" role="status">正在加载活动…</p>
      <template v-else><RouterLink v-for="activity in upcoming" :key="activity.id" :to="`/activities/${activity.id}`" class="community-home-event"><span class="community-home-event-icon"><CommunityIcon name="calendar" /></span><div><small>{{ activity.club || '社团活动' }}</small><h3>{{ activity.title }}</h3><p>{{ activity.time }}</p><p>{{ activity.location }}</p></div><span class="community-record-status" :class="{ muted: activity.full || activity.closed }">{{ activity.closed ? '报名截止' : activity.full ? '名额已满' : '可报名' }}</span></RouterLink><div v-if="!upcoming.length" class="community-list-message"><h3>暂无已发布活动</h3><p>社团发布活动后，会出现在这里。</p></div></template>
    </section>
    <RouterLink to="/assistant" class="community-help-row"><CommunityIcon name="assistant" /><div><strong>社团助手</strong><p>筛选兴趣方向，查看社团资料。</p></div><CommunityIcon name="chevron" /></RouterLink>
    <p class="community-list-disclosure">{{ businessMode ? '本地开发 · 社团和活动来自数据库，所有资料均为虚构。' : '交互原型 · 虚构数据 · 非真实报名。' }}</p>
  </div>
</template>
