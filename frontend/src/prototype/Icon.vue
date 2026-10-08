<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string> = {
  home: 'M3 10 12 3l9 7v10h-6v-7H9v7H3Z',
  clubs: 'M8 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8 1a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM2 21v-4a6 6 0 0 1 12 0v4m2-7a5 5 0 0 1 6 5v2',
  calendar: 'M4 5h16v16H4ZM7 2v6m10-6v6M4 10h16m-12 4h2m4 0h2m-8 4h2',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4 22v-2a8 8 0 0 1 16 0v2',
  bell: 'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5Zm5 4h4',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  search: 'M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14Zm5-2 6 6',
  heart: 'M12 21 3 12a5 5 0 0 1 9-7 5 5 0 0 1 9 7Z',
  pin: 'M12 22s7-7 7-13a7 7 0 0 0-14 0c0 6 7 13 7 13Zm0-10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Zm0-16v6l4 3',
  grid: 'M3 3h7v7H3Zm11 0h7v7h-7ZM3 14h7v7H3Zm11 0h7v7h-7Z',
  check: 'm4 12 5 5L20 6',
  plus: 'M12 4v16M4 12h16',
  book: 'M12 5v17M2 3c4-1 7 0 10 2 3-2 6-3 10-2v16c-4-1-7 0-10 2-3-2-6-3-10-2Z',
  spark: 'm12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z',
  close: 'm5 5 14 14M19 5 5 19'
};
</script>
<template><svg class="p-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.grid" /></svg></template>
