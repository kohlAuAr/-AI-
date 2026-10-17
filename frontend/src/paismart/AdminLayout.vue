<!-- Adapted from PaiSmart packages/materials AdminLayout + base-layout.
     Preserves header/sider/content slots and collapse behavior; removes unused layout modes.
     Apache-2.0; see THIRD-PARTY-NOTICES.md. -->
<script setup lang="ts">
import { ref, watch } from 'vue';
import { NDrawer, NDrawerContent } from 'naive-ui';
import { useRoute } from 'vue-router';
defineProps<{ collapsed: boolean }>();
const opened = ref(false), route = useRoute();
watch(() => route.path, () => { opened.value = false; });
</script>
<template>
  <div class="paismart-layout" :class="{ 'sider-collapsed': collapsed }">
    <aside class="paismart-sider"><slot name="sider" /></aside>
    <header class="paismart-header"><button type="button" class="paismart-menu-open" aria-label="打开导航菜单" :aria-expanded="opened" @click="opened = true"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg></button><slot name="header" /></header>
    <main id="campus-main" class="paismart-content" tabindex="-1"><slot /></main>
    <NDrawer v-model:show="opened" :width="260" placement="left" class="paismart-drawer"><NDrawerContent title="校园导航" closable><slot name="mobile-sider" /></NDrawerContent></NDrawer>
  </div>
</template>
<style scoped>
.paismart-layout { --sider-width: 204px; min-height: 100dvh; padding: 80px 24px 24px calc(var(--sider-width) + 40px); background: #f7fafc; transition: padding-left .2s ease; }
.paismart-layout.sider-collapsed { --sider-width: 64px; }
.paismart-sider { position: fixed; inset: 16px auto 16px 16px; width: var(--sider-width); z-index: 30; display: flex; flex-direction: column; overflow: hidden; border-radius: 16px; background: #fff; box-shadow: 0 4px 24px rgb(29 35 41 / 7%); transition: width .2s ease; }
.paismart-header { position: fixed; top: 0; right: 0; left: calc(var(--sider-width) + 32px); height: 64px; z-index: 25; display: flex; align-items: center; gap: 12px; padding: 0 24px 0 12px; background: #f7fafcf5; }
.paismart-content { max-width: 1500px; min-width: 0; margin: 0 auto; padding: 0; outline: none; }
.paismart-menu-open { display: none; border: 0; padding: 10px; background: transparent; color: #515667; }
.paismart-menu-open svg { display: block; width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.8; }
@media (max-width: 760px) { .paismart-layout, .paismart-layout.sider-collapsed { padding: 76px 12px 96px; } .paismart-sider { display: none; } .paismart-header { left: 0; padding: 0 12px; background: white; border-bottom: 1px solid #efeff5; } .paismart-menu-open { display: inline-flex; } }
@media (prefers-reduced-motion: reduce) { .paismart-layout, .paismart-sider { transition: none; } }
</style>
