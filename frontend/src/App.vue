<script setup lang="ts">
const links = [
  { path: '/', label: '项目概览', number: '01' },
  { path: '/clubs', label: '社团广场', number: '02' },
  { path: '/activities', label: '活动列表', number: '03' },
  { path: '/knowledge', label: '资料知识库', number: '04' },
  { path: '/chat', label: '资料问答', number: '05' },
  { path: '/roadmap', label: '后续开发', number: '06' }
];
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"><span class="brand-mark">社</span><span>校园社团<span class="brand-sub">活动与招新平台</span></span></RouterLink>
      <div class="sidebar-caption">开发工作台</div>
      <nav aria-label="主导航">
        <RouterLink v-for="link in links" :key="link.path" :to="link.path" :exact-active-class="'active'" :active-class="link.path === '/' ? '' : 'active'">
          <span class="nav-number">{{ link.number }}</span>{{ link.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-footer"><span class="status-dot"></span>精简开发骨架<p>先跑通，再逐项补齐业务。</p></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><span>校园文化服务 / 社团平台</span><span class="badge neutral">本地开发 · 公开示例资料</span></header>
      <main><RouterView /></main>
      <footer class="footer">当前未接入真实账号与权限。示例社团和活动用于开发验证。</footer>
    </div>
  </div>
</template>
