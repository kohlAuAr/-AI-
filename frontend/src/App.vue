<script setup lang="ts">
import { useRoute } from 'vue-router';
import PrototypeShell from './prototype/PrototypeShell.vue';
import SystemShell from './views/SystemShell.vue';
import CommunityShell from './community/CommunityShell.vue';
const route = useRoute();
</script>

<template>
  <SystemShell v-if="route.path.startsWith('/system')" /><CommunityShell v-else-if="route.path === '/clubs'" /><PrototypeShell v-else />
</template>
