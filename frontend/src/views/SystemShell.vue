<script setup lang="ts">
import { useRoute } from 'vue-router';
const route = useRoute();
const links = [
  { path: '/system', label: '项目概览' }, { path: '/system/clubs', label: '社团接口' },
  { path: '/system/activities', label: '活动接口' }, { path: '/system/knowledge', label: '资料知识库' },
  { path: '/system/chat', label: '资料问答' }, { path: '/system/roadmap', label: '后续开发' }
];
</script>
<template>
  <section class="community-system">
    <div class="community-system-heading"><span>开发联调 · 非正式校园系统</span><RouterLink to="/assistant">返回助手</RouterLink></div>
    <nav class="community-workspace-nav" aria-label="联调导航"><RouterLink v-for="link in links" :key="link.path" :to="link.path" :class="{ current: route.path === link.path }" :aria-current="route.path === link.path ? 'page' : undefined">{{ link.label }}</RouterLink></nav>
    <div class="community-system-content"><RouterView /></div>
    <p class="community-list-disclosure">资料问答使用公开测试资料，尚未完成私有资料权限隔离，请勿上传个人敏感资料。</p>
  </section>
</template>
