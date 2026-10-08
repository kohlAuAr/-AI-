<script setup lang="ts">
import { useRoute } from 'vue-router';
import PrototypeShell from './prototype/PrototypeShell.vue';
import SystemShell from './views/SystemShell.vue';
const route = useRoute();
</script>

<template>
  <SystemShell v-if="route.path.startsWith('/system')" /><PrototypeShell v-else />
</template>
