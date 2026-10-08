<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { state, ui } from './store';
import Icon from './Icon.vue';
import { publicPreview } from './public';
const route = useRoute();
const manager = computed(() => route.path.startsWith('/manage'));
const unread = computed(() => state.notices.filter(n => !n.read).length);
const links = [{ path: '/', label: '发现', icon: 'home' }, { path: '/clubs', label: '社团', icon: 'clubs' }, { path: '/recruitment', label: '招新', icon: 'spark' }, { path: '/activities', label: '活动', icon: 'calendar' }, { path: '/assistant', label: '助手', icon: 'book' }, { path: '/me', label: '我的', icon: 'user' }];
const adminLinks = [{ path: '/manage', label: '工作概览' }, { path: '/manage/recruitment', label: '招新审核' }, { path: '/manage/members', label: '成员管理' }, { path: '/manage/activities', label: '活动管理' }, { path: '/manage/finance', label: '经费台账' }];
</script>
<template>
  <div class="proto">
    <header class="p-header"><RouterLink to="/" class="p-brand"><span class="p-brand-symbol">社</span><span>社遇<small>CAMPUS CLUBS</small></span></RouterLink>
      <nav class="p-desktop-nav" aria-label="学生端导航"><RouterLink v-for="link in links" :key="link.path" :to="link.path" :class="{ selected: link.path === '/' ? route.path === '/' : route.path.startsWith(link.path) }">{{ link.label }}</RouterLink></nav>
      <div class="p-header-actions"><RouterLink to="/messages" class="p-icon-button" aria-label="消息中心"><Icon name="bell" /><span v-if="unread" class="p-notice-dot"></span></RouterLink><RouterLink :to="manager ? '/' : '/manage'" class="p-role-switch">{{ manager ? '学生端' : '负责人工作台' }}<Icon name="arrow" /></RouterLink><RouterLink to="/me" class="p-avatar" aria-label="个人中心">林</RouterLink></div>
    </header>
    <div class="p-prototype-bar"><span><i></i>交互原型 · 虚构数据 · 非真实报名</span><RouterLink to="/guide">原型说明 ↗</RouterLink></div>
    <nav v-if="manager" class="p-admin-nav" aria-label="负责人导航"><RouterLink v-for="link in adminLinks" :key="link.path" :to="link.path" :class="{ selected: route.path === link.path }">{{ link.label }}</RouterLink></nav>
    <main class="p-main"><RouterView /></main>
    <footer class="p-footer"><span>社遇 / 让校园生活，多一种可能。</span><div><RouterLink to="/guide">使用说明</RouterLink><RouterLink to="/assistant">社团助手</RouterLink><RouterLink v-if="!publicPreview" to="/system">开发联调</RouterLink></div></footer>
    <nav class="p-mobile-nav" aria-label="手机底部导航"><RouterLink v-for="link in links" :key="link.path" :to="link.path" :class="{ selected: link.path === '/' ? route.path === '/' : route.path.startsWith(link.path) }"><Icon :name="link.icon" /><span>{{ link.label }}</span></RouterLink></nav>
    <div v-if="ui.toast" class="p-toast" role="status"><Icon name="check" />{{ ui.toast }}</div>
    <div v-if="ui.storageUnavailable" class="p-storage-note" role="alert">浏览器不允许保存数据，演示记录将在刷新后丢失。</div>
  </div>
</template>
