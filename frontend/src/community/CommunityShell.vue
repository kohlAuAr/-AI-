<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { business, businessMode, clubCatalog, logout, refreshBusiness } from '../prototype/business';
import { state, toast, ui } from '../prototype/store';
import { errorMessage } from '../api';
import CommunityIcon from './CommunityIcon.vue';
import { unreadNotices, useNotificationRefresh } from './notifications';
import SystemShell from '../views/SystemShell.vue';
import { publicPreview } from '../prototype/public';
import './community-secondary.css';
import './community-banners.css';
const unread = computed(() => businessMode ? unreadNotices.value : state.notices.filter(n => !n.read).length);
useNotificationRefresh();
const route = useRoute();
function current(path: string) { return path === '/' ? route.path === '/' : route.path.startsWith(path); }
const manager = computed(() => route.path.startsWith('/manage'));
const secondary = computed(() => route.path === '/login' || route.path === '/guide' || route.path.startsWith('/clubs/') || (manager.value && route.path !== '/manage/activities') || (!businessMode && (route.path.startsWith('/activities') || route.path === '/messages' || manager.value)));
const adminLinks = [{ path: '/manage', label: '工作概览' }, { path: '/manage/recruitment', label: '招新审核' }, { path: '/manage/members', label: '成员管理' }, { path: '/manage/activities', label: '活动管理' }, { path: '/manage/finance', label: '经费台账' }];
const navigation = [
  { path: '/', label: '发现', icon: 'home' }, { path: '/clubs', label: '社团', icon: 'clubs' },
  { path: '/activities', label: '活动', icon: 'calendar' },
  { path: '/assistant', label: '助手', icon: 'assistant' }, { path: '/me', label: '我的', icon: 'user' }
];
const joinedClubs = computed(() => clubCatalog.value.filter(club => businessMode
  ? business.memberships.some(member => member.clubId === club.id)
  : state.applications.some(application => application.mine && application.clubId === club.id && application.status === 'approved')));
onMounted(refreshBusiness);
watch(() => route.path, refreshBusiness);
async function signOut() { try { await logout(); toast('已退出登录'); } catch (error) { toast(errorMessage(error)); } }
</script>
<template>
  <div class="community-app">
    <header class="community-topbar">
      <RouterLink to="/" class="community-brand"><CommunityIcon name="planet" /><strong>社遇</strong></RouterLink>
      <span class="community-topbar-label">校园社群</span>
      <div class="community-account-actions">
        <RouterLink to="/messages" class="community-icon-action" :aria-label="`消息中心，${unread} 条未读消息`"><CommunityIcon name="bell" /><span v-if="unread" class="community-notice-badge" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</span></RouterLink>
        <RouterLink v-if="businessMode && !business.user" to="/login" class="community-login">登录</RouterLink>
        <template v-else><RouterLink to="/me" class="community-user"><span>{{ businessMode ? business.user?.name.slice(0, 1) : '林' }}</span><b>{{ businessMode ? business.user?.name : state.profile.name }}</b></RouterLink><button v-if="businessMode" type="button" class="community-logout" @click="signOut">退出</button></template>
      </div>
    </header>
    <div class="community-layout">
      <aside class="community-sidebar">
        <nav aria-label="学生端导航"><RouterLink v-for="item in navigation" :key="item.path" :to="item.path" :class="{ current: current(item.path) }" :aria-current="current(item.path) ? 'page' : undefined"><CommunityIcon :name="item.icon" />{{ item.label }}</RouterLink></nav>
        <div class="community-sidebar-footer"><RouterLink v-if="businessMode && business.user?.role === 'PLATFORM_ADMIN'" to="/platform/banners">首页内容管理</RouterLink><RouterLink v-if="!businessMode || business.user?.role === 'MANAGER'" to="/manage">负责人工作台</RouterLink><RouterLink v-if="businessMode && business.user?.role === 'MANAGER'" to="/manage/activities">活动管理</RouterLink><RouterLink to="/guide">使用说明</RouterLink></div>
      </aside>
      <main class="community-main">
        <nav v-if="manager" class="community-workspace-nav" aria-label="负责人导航"><RouterLink v-for="item in adminLinks" :key="item.path" :to="item.path" :class="{ current: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">{{ item.label }}</RouterLink></nav>
        <SystemShell v-if="route.path.startsWith('/system')" />
        <div v-else-if="secondary" class="community-secondary-page">
          <section v-if="businessMode && business.error" class="community-list-message" role="alert"><p>{{ business.error }}。真实业务不可用时不会自动切换成模拟申请。</p><button type="button" @click="refreshBusiness">重新加载</button></section>
          <p v-if="businessMode && business.loading" class="community-list-loading" role="status">正在读取校园数据库…</p>
          <RouterView />
        </div>
        <RouterView v-else />
        <footer class="community-page-footer"><RouterLink to="/guide">使用说明</RouterLink><RouterLink v-if="!publicPreview" to="/system">开发联调</RouterLink><span>{{ businessMode ? '本地演示 · 虚构资料' : '交互原型 · 非真实报名' }}</span></footer>
      </main>
      <aside class="community-rightbar">
        <section class="community-side-section"><h2>我的社团</h2><template v-if="businessMode && !business.user"><p>登录后查看已加入的社团。</p><RouterLink to="/login" class="community-side-login">登录</RouterLink></template><template v-else><RouterLink v-for="club in joinedClubs" :key="club.id" :to="`/clubs/${club.id}`" class="community-joined-link"><span class="community-small-cover" :class="`cover-${club.id}`"><CommunityIcon :name="club.id" /></span><span>{{ club.name }}</span><CommunityIcon name="chevron" /></RouterLink><p v-if="!joinedClubs.length">还没有加入社团。</p></template><RouterLink to="/me" class="community-side-more">查看入社申请<CommunityIcon name="chevron" /></RouterLink></section>
        <section class="community-side-section community-note"><h2>本地演示</h2><p>{{ businessMode ? '社团、申请与成员关系来自本地数据库，人物与资料均为虚构。' : '交互原型使用虚构数据，非真实报名。' }}</p><p>收藏仅保存在当前浏览器。</p></section>
      </aside>
    </div>
    <nav class="community-bottom-nav" aria-label="手机底部导航"><RouterLink v-for="item in navigation" :key="item.path" :to="item.path" :class="{ current: current(item.path) }" :aria-current="current(item.path) ? 'page' : undefined"><CommunityIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></nav>
    <div v-if="ui.toast" class="community-toast" role="status">{{ ui.toast }}</div>
    <div v-if="ui.storageUnavailable" class="community-storage-note" role="alert">当前浏览器无法保存收藏，刷新后可能丢失。</div>
  </div>
</template>
<style src="./community.css"></style>
