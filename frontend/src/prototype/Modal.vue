<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import Icon from './Icon.vue';
defineProps<{ title: string }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
let previousFocus: HTMLElement | null = null;
onMounted(() => { previousFocus = document.activeElement as HTMLElement; dialog.value?.showModal(); });
onUnmounted(() => previousFocus?.focus());
</script>
<template><dialog ref="dialog" class="p-modal" aria-labelledby="modal-title" @cancel.prevent="emit('close')"><header><div><span class="p-kicker">交互原型 · 仅保存模拟数据</span><h2 id="modal-title">{{ title }}</h2></div><button class="p-icon-button" aria-label="关闭弹窗" @click="emit('close')"><Icon name="close" /></button></header><slot /></dialog></template>
