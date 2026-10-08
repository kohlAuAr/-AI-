<script setup lang="ts">
const links = [
  { path: '/system', label: '项目概览' }, { path: '/system/clubs', label: '社团接口' },
  { path: '/system/activities', label: '活动接口' }, { path: '/system/knowledge', label: '资料知识库' },
  { path: '/system/chat', label: '资料问答' }, { path: '/system/roadmap', label: '后续开发' }
];
</script>
<template><div class="app-shell"><aside class="sidebar"><RouterLink to="/" class="brand"><span class="brand-mark">社</span><span>校园社团<span class="brand-sub">返回交互原型</span></span></RouterLink><div class="sidebar-caption">开发联调 · 非产品首页</div><nav aria-label="联调导航"><RouterLink v-for="link in links" :key="link.path" :to="link.path" exact-active-class="active">{{ link.label }}</RouterLink></nav><div class="sidebar-footer">真实接口联调<p>需启动业务与 AI 服务。</p></div></aside><div class="main-shell"><header class="topbar"><span>校园文化服务 / 开发联调</span><RouterLink to="/" class="text-link">返回产品原型 →</RouterLink></header><main><RouterView /></main><footer class="footer">当前未接入真实账号与权限。示例社团和活动用于开发验证。</footer></div></div></template>
