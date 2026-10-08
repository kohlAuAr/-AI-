<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import CommunityIcon from './CommunityIcon.vue';
defineProps<{ title: string; busy?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
let previousFocus: HTMLElement | null = null;
onMounted(() => { previousFocus = document.activeElement as HTMLElement; dialog.value?.showModal(); });
onUnmounted(() => previousFocus?.focus());
</script>
<template><dialog ref="dialog" class="community-dialog" aria-labelledby="community-dialog-title" @cancel.prevent="!busy && emit('close')"><header><h2 id="community-dialog-title">{{ title }}</h2><button type="button" aria-label="关闭弹窗" :disabled="busy" @click="emit('close')"><CommunityIcon name="close" /></button></header><slot /></dialog></template>
