<!-- Adapted from PaiSmart views/chat/modules/input-box.vue. No WebSocket, billing or fake stop action. -->
<script setup lang="ts">
import { NButton } from 'naive-ui';
const props = defineProps<{ modelValue: string; disabled?: boolean; busy?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; send: [] }>();
function shortcut(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 || event.shiftKey || event.ctrlKey || event.metaKey) return;
  event.preventDefault(); if (!props.disabled && !props.busy && props.modelValue.trim()) emit('send');
}
</script>
<template><form class="paismart-input-area" @submit.prevent="emit('send')"><label for="assistant-question">输入问题</label><div class="chat-input-shell"><textarea id="assistant-question" :value="modelValue" :disabled="disabled || busy" required maxlength="2000" rows="2" placeholder="给社团助手发送消息，Enter 发送，Shift+Enter 换行" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" @keydown="shortcut" /><NButton attr-type="submit" type="primary" circle :loading="busy" :disabled="disabled || busy || !modelValue.trim()" aria-label="发送问题"><template #icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6" /></svg></template></NButton></div><div class="paismart-input-help"><span>{{ busy ? '正在查询与整理，请勿重复提交' : '只读查询 · 回答请核对来源' }}</span><span>Shift+Enter 换行</span></div></form></template>
<style scoped>
.paismart-input-area { padding: 14px 24px 18px; background: white; }
.paismart-input-area > label { display: block; font-size: 12px; color: #818796; margin-bottom: 8px; }
.chat-input-shell { max-width: 960px; margin: 0 auto; display: flex; align-items: flex-end; gap: 8px; border-radius: 16px; padding: 10px 14px; border: 1px solid rgba(15, 23, 42, 0.12); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 6px 18px rgba(15, 23, 42, 0.04); transition: border-color .18s ease, box-shadow .18s ease; }
.chat-input-shell:focus-within { border-color: #646cff; box-shadow: 0 0 0 2px rgb(100 108 255 / .18); }
.chat-input-shell textarea { flex: 1; width: 100%; min-width: 0; min-height: 48px; max-height: 128px; resize: vertical; border: 0 !important; outline: none !important; background: transparent; padding: 4px 0; font: inherit; font-size: 14px; line-height: 1.7; color: #333; }
.chat-input-shell .n-button { min-width: 36px; min-height: 36px; }
.chat-input-shell svg { width: 18px; height: 18px; }
.paismart-input-help { display: flex; justify-content: space-between; gap: 12px; margin-top: 9px; font-size: 11px; color: #9399a5; }
@media (max-width: 760px) { .paismart-input-area { padding: 14px; } .chat-input-shell textarea { font-size: 16px; } .paismart-input-help span:last-child { display: none; } }
</style>
