<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string> = {
  planet: 'M18 5a8 8 0 1 0 1 12M3 16C0 14 5 10 11 7s12-3 12-1-4 6-10 9-12 3-10 1Z',
  home: 'M4 10 12 3l8 7v11h-6v-7h-4v7H4Z',
  clubs: 'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM2 21v-2a6 6 0 0 1 12 0v2m3-9a4 4 0 0 0 0-8m0 11a5 5 0 0 1 5 5v1',
  join: 'M4 5h10v16H4Zm3 4h4m-4 4h4m5-2h7m-3-3 3 3-3 3',
  calendar: 'M4 5h16v16H4ZM8 2v6m8-6v6M4 10h16m-11 4h2m3 0h2m-7 4h2',
  assistant: 'M5 5h14v12H9l-4 4Zm4 5h.01M15 10h.01M9 13h6',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4 21v-1a8 8 0 0 1 16 0v1',
  search: 'M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm6-2 6 6',
  chevron: 'm9 5 7 7-7 7',
  close: 'm6 6 12 12M18 6 6 18',
  bookmark: 'M6 3h12v18l-6-4-6 4Z',
  bell: 'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5Zm5 4h4',
  photo: 'M3 6h5l2-3h4l2 3h5v14H3Zm9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  code: 'm8 6-6 6 6 6m8-12 6 6-6 6M14 3l-4 18',
  music: 'M9 17V5l12-3v12M9 8l12-3M9 17c0 2-2 4-4 4s-3-1-3-2 2-3 4-3 3 0 3 1Zm12-3c0 2-2 4-4 4s-3-1-3-2 2-3 4-3 3 0 3 1Z',
  hike: 'm2 21 8-16 5 10 3-6 4 12ZM7 11l3 2 3-2',
  volunteer: 'M12 19v3M12 11c-9-10-13 2 0 8 13-6 9-18 0-8Z',
  read: 'M12 5v16M3 3c4-1 6 0 9 2 3-2 5-3 9-2v16c-4-1-6 0-9 2-3-2-5-3-9-2Z'
};
</script>
<template><svg class="community-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.clubs" /></svg></template>
