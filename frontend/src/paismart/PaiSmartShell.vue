<!-- Adapted from PaiSmart base-layout, global-header and vertical-menu. Campus auth/API remain unchanged. -->
<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { NAvatar, NButton, NConfigProvider, NMenu, zhCN, dateZhCN, type MenuOption } from 'naive-ui';
import AdminLayout from './AdminLayout.vue';
import { themeOverrides } from './theme';
import { business, businessMode, logout, refreshBusiness } from '../prototype/business';
import { state, toast, ui } from '../prototype/store';
import { publicPreview } from '../prototype/public';
import { unreadNotices, useNotificationRefresh } from '../community/notifications';
import { errorMessage } from '../api';
import CommunityIcon from '../community/CommunityIcon.vue';
import SystemShell from '../views/SystemShell.vue';
import '../community/community.css';
import '../community/community-secondary.css';
import '../community/community-banners.css';
import './campus-pages.css';
const route = useRoute(), collapsed = ref(false);
const navigation = [ { path: '/', label: '发现', icon: 'home' }, { path: '/clubs', label: '社团', icon: 'clubs' }, { path: '/activities', label: '活动', icon: 'calendar' }, { path: '/assistant', label: '助手', icon: 'assistant' }, { path: '/me', label: '我的', icon: 'user' } ];
const adminLinks = [...(businessMode ? [{ path: '/manage/club', label: '社团设置', icon: 'clubs' }] : []), { path: '/manage', label: '工作概览', icon: 'home' }, { path: '/manage/recruitment', label: '招新审核', icon: 'join' }, { path: '/manage/members', label: '成员管理', icon: 'clubs' }, { path: '/manage/activities', label: '活动管理', icon: 'calendar' }, { path: '/manage/finance', label: '经费台账', icon: 'bookmark' }];
const manager = computed(() => route.path.startsWith('/manage'));
const secondary = computed(() => route.path.startsWith('/clubs/') || route.path === '/guide' || ['/manage', '/manage/recruitment', '/manage/members'].includes(route.path) || !businessMode && (manager.value || route.path.startsWith('/activities') || route.path === '/messages'));
const selected = computed(() => route.path.startsWith('/clubs') ? '/clubs' : route.path.startsWith('/activities') ? '/activities' : route.path);
function menuItem(item: { path: string; label: string; icon: string }): MenuOption { return { key: item.path, label: () => h(RouterLink, { to: item.path }, () => item.label), icon: () => h(CommunityIcon, { name: item.icon }) }; }
const menus = computed<MenuOption[]>(() => [ ...navigation.map(menuItem), { key: 'd1', type: 'divider' }, ...(business.user?.role === 'MANAGER' || !businessMode ? [{ key: 'manage-group', type: 'group', label: '社团管理', children: adminLinks.map(menuItem) }] : []), ...(business.user?.role === 'PLATFORM_ADMIN' ? [menuItem({ path: '/platform/banners', label: '首页内容管理', icon: 'photo' })] : []), menuItem({ path: '/system/knowledge', label: '资料知识库', icon: 'read' }), menuItem({ path: '/messages', label: '消息中心', icon: 'bell' }) ]);
const pageTitle = computed(() => [...navigation, ...adminLinks, { path: '/login', label: '账号登录' }, { path: '/messages', label: '消息中心' }, { path: '/platform/banners', label: '首页内容管理' }, { path: '/system/knowledge', label: '资料知识库' }, { path: '/system/chat', label: '资料问答' }].find(item => item.path === selected.value)?.label || '校园社团');
const unread = computed(() => businessMode ? unreadNotices.value : state.notices.filter(n => !n.read).length);
const userName = computed(() => businessMode ? business.user?.name || '未登录' : state.profile.name);
useNotificationRefresh(); onMounted(refreshBusiness); watch(() => route.path, refreshBusiness);
async function signOut() { try { await logout(); toast('已退出登录'); } catch (error) { toast(errorMessage(error)); } }
</script>
<template>
  <NConfigProvider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN" class="paismart-shell">
    <a href="#campus-main" class="paismart-skip-link">跳到主要内容</a>
    <AdminLayout :collapsed="collapsed">
      <template #sider><RouterLink to="/" class="paismart-logo"><span><CommunityIcon name="planet" /></span><strong v-if="!collapsed">社遇<span>校园社团平台</span></strong></RouterLink><nav aria-label="学生端导航" class="paismart-nav"><NMenu :value="selected" :collapsed="collapsed" :collapsed-width="64" :collapsed-icon-size="22" :options="menus" :indent="18" /></nav><div class="paismart-sider-bottom"><NButton quaternary block :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed">{{ collapsed ? '»' : '« 收起导航' }}</NButton><RouterLink v-if="!collapsed" to="/guide">使用说明</RouterLink></div></template>
      <template #mobile-sider><nav aria-label="校园抽屉导航"><NMenu :value="selected" :options="menus" :indent="18" /></nav></template>
      <template #header><div class="paismart-breadcrumb"><span>校园社团</span><span>/</span><strong>{{ pageTitle }}</strong></div><div class="paismart-header-actions"><RouterLink to="/messages" class="paismart-header-bell" :aria-label="`消息中心，${unread} 条未读消息`"><CommunityIcon name="bell" /><span v-if="unread">{{ unread > 99 ? '99+' : unread }}</span></RouterLink><template v-if="!businessMode || business.user"><RouterLink to="/me" class="paismart-account"><NAvatar round :size="30" :style="{ background: '#efefff', color: '#555cd6' }">{{ userName.slice(0, 1) }}</NAvatar><span>{{ userName }}</span></RouterLink><NButton v-if="businessMode" quaternary size="small" @click="signOut">退出</NButton></template><RouterLink v-else to="/login" class="paismart-header-login">登录</RouterLink></div></template>
      <div class="community-app paismart-campus" :class="{ 'paismart-auth-page': route.path === '/login', 'paismart-chat-page': route.path === '/assistant' || route.path === '/system/chat' }">
        <nav v-if="manager" class="community-workspace-nav" aria-label="负责人导航"><RouterLink v-for="item in adminLinks" :key="item.path" :to="item.path" :class="{ current: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">{{ item.label }}</RouterLink></nav>
        <SystemShell v-if="route.path.startsWith('/system')" /><div v-else class="paismart-route-content" :class="{ 'community-secondary-page': secondary }"><RouterView /></div>
        <footer class="community-page-footer"><RouterLink to="/guide">使用说明</RouterLink><RouterLink v-if="!publicPreview" to="/system">开发联调</RouterLink><span>{{ businessMode ? '本地演示 · 虚构资料' : '交互原型 · 非真实报名' }}</span></footer>
      </div>
    </AdminLayout>
    <nav class="community-bottom-nav paismart-mobile-nav" aria-label="手机底部导航"><RouterLink v-for="item in navigation" :key="item.path" :to="item.path" :class="{ current: item.path === '/' ? route.path === '/' : route.path.startsWith(item.path) }" :aria-current="(item.path === '/' ? route.path === '/' : route.path.startsWith(item.path)) ? 'page' : undefined"><CommunityIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></nav>
    <div v-if="ui.toast" class="community-toast" role="status">{{ ui.toast }}</div><div v-if="!businessMode && ui.storageUnavailable" class="community-storage-note" role="alert">当前浏览器无法保存收藏，刷新后可能丢失。</div>
  </NConfigProvider>
</template>
