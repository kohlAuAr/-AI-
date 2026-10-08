<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { categories } from '../prototype/data';
import { business, businessMode, clubCatalog, refreshBusiness } from '../prototype/business';
import { state, toggleFavorite } from '../prototype/store';
import CommunityIcon from './CommunityIcon.vue';
const keyword = ref('');
const category = ref('全部');
const route = useRoute();
const recruitment = computed(() => route.path === '/recruitment');
const matches = computed(() => clubCatalog.value.filter(club => (!recruitment.value || club.recruiting) && (category.value === '全部' || club.category === category.value)
  && `${club.name} ${club.tags.join(' ')} ${club.description}`.includes(keyword.value.trim())));
</script>
<template>
  <div class="community-directory">
    <div class="community-page-title"><h1>{{ recruitment ? '招新' : '社团' }}</h1><RouterLink to="/me?tab=applications">我的申请<CommunityIcon name="chevron" /></RouterLink></div>
    <div v-if="recruitment" class="community-recruit-note"><CommunityIcon name="join" /><p>正在招新的社团都在这里。查看加入条件，再提交申请。</p></div>
    <div class="community-search-wrap"><label class="community-search" for="community-search"><CommunityIcon name="search" /><input id="community-search" v-model="keyword" type="search" aria-label="搜索社团" placeholder="搜索社团或兴趣" /><button v-if="keyword" type="button" aria-label="清除关键词" @click.prevent="keyword = ''"><CommunityIcon name="close" /></button></label></div>
    <div class="community-category-tabs" role="group" aria-label="类别筛选"><button v-for="item in categories" :key="item" type="button" :aria-pressed="category === item" :class="{ active: category === item }" @click="category = item">{{ item }}</button></div>
    <section class="community-list" aria-labelledby="community-list-title" :aria-busy="businessMode && business.loading">
      <div class="community-list-heading"><h2 id="community-list-title">{{ category === '全部' ? recruitment ? '正在招新' : '全部社团' : category }}</h2><span v-if="!(businessMode && (business.error || business.loading))" role="status" aria-live="polite">共 {{ matches.length }} 个</span></div>
      <div v-if="businessMode && business.error" class="community-list-message" role="alert"><h3>社团暂时加载失败</h3><p>{{ business.error }}</p><button type="button" @click="refreshBusiness">重新加载</button></div>
      <p v-else-if="businessMode && business.loading" class="community-list-loading" role="status">正在加载社团…</p>
      <template v-else>
        <article v-for="club in matches" :key="club.id" class="community-club-row">
          <RouterLink :to="`/clubs/${club.id}`" class="community-club-link" :aria-label="`查看${club.name}详情`">
            <span class="community-club-cover" :class="`cover-${club.id}`" aria-hidden="true"><CommunityIcon :name="club.id" /></span>
            <div class="community-club-copy"><div class="community-club-title"><h3>{{ club.name }}</h3><span v-if="club.recruiting" class="community-recruiting">招新中</span><span v-else class="community-recruiting ended">已结束</span></div><p class="community-club-description">{{ club.description }}</p><p v-if="recruitment" class="community-recruit-requirements">加入条件：{{ club.requirements }}</p><div class="community-club-meta"><span>{{ club.category }}</span><span>{{ club.members }} 位成员</span><span v-if="recruitment" class="community-recruiting">查看并申请</span></div><p class="community-club-meeting">{{ club.schedule }}<span class="community-meeting-place">{{ club.place }}</span></p></div>
          </RouterLink>
          <button type="button" class="community-save" :class="{ saved: state.favorites.includes(club.id) }" :aria-pressed="state.favorites.includes(club.id)" :aria-label="`${state.favorites.includes(club.id) ? '取消收藏' : '收藏'}${club.name}`" @click="toggleFavorite(club.id)"><CommunityIcon name="bookmark" /></button>
        </article>
        <div v-if="!matches.length" class="community-list-message"><h3>没有找到匹配的社团</h3><p>换个关键词，或清除筛选再试试。</p><button type="button" @click="keyword = ''; category = '全部'">清除筛选</button></div>
      </template>
    </section>
    <p class="community-list-disclosure">{{ businessMode ? '虚构社团资料 · 入社申请保存在本地数据库' : '虚构社团资料 · 非真实报名' }}<br />收藏仅保存在当前浏览器</p>
  </div>
</template>
