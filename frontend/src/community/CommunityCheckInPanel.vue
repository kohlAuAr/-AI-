<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { business } from '../prototype/business';
import { checkInState, loadCheckIn, clearCheckIn, changeCheckIn } from './checkIn';
import { activityTime } from './activities';
const props = defineProps<{ activityId: number }>();
const confirmClose = ref(false);
onMounted(() => loadCheckIn(props.activityId));
onUnmounted(() => { if (checkInState.activityId === props.activityId) clearCheckIn(); });
watch([() => props.activityId, () => business.user?.id, () => business.user?.role, () => business.managedClubId], () => { confirmClose.value = false; clearCheckIn(); }, { flush: 'sync' });
watch(() => props.activityId, id => loadCheckIn(id));
async function close() { if (await changeCheckIn('close')) confirmClose.value = false; }
</script>
<template><section class="community-participants community-check-in" aria-label="活动签到与报名名单">
  <div class="community-list-heading"><h4>签到与报名名单</h4><button type="button" class="community-text-link" :disabled="checkInState.loading || checkInState.saving" @click="loadCheckIn(activityId)">刷新签到与名单</button></div>
  <p v-if="checkInState.loading" role="status">正在读取签到与名单…</p>
  <p v-if="checkInState.error" class="community-error" role="alert">{{ checkInState.error }}</p>
  <template v-if="checkInState.report && !checkInState.loading">
    <p>已报名 {{ checkInState.report.registered }} 人，已签到 {{ checkInState.report.checkedIn }} 人 · {{ checkInState.report.open ? '签到开放中' : '签到未开放或已关闭' }}</p>
    <template v-if="checkInState.report.open"><label>现场签到码（可选中复制）<textarea :value="checkInState.report.code" readonly rows="2" spellcheck="false" /></label><p>只向现场学生提供；关闭后此码失效，重新开启会生成新码。</p><button type="button" class="community-button secondary" :disabled="checkInState.saving" @click="confirmClose = true">关闭签到</button></template>
    <template v-else><p>活动开始前 30 分钟起可开启，由负责人手动关闭。</p><button type="button" class="community-button" :disabled="checkInState.saving" @click="changeCheckIn('open')">{{ checkInState.saving ? '正在开启…' : '开启签到' }}</button></template>
    <div v-if="confirmClose && checkInState.report.open" class="community-check-in-confirm"><p>关闭后学生不能继续签到，已签到记录会保留。确定关闭？</p><div class="community-dialog-actions"><button type="button" class="community-button secondary" :disabled="checkInState.saving" @click="confirmClose = false">暂不关闭</button><button type="button" class="community-button" :disabled="checkInState.saving" @click="close">{{ checkInState.saving ? '正在关闭…' : '确认关闭签到' }}</button></div></div>
    <ul class="community-check-in-people"><li v-for="person in checkInState.participants" :key="person.id"><strong>{{ person.name }}</strong> · {{ person.major }}<p>{{ activityTime(person.registeredAt) }} 报名</p><p>{{ person.checkedInAt ? `已签到 · ${activityTime(person.checkedInAt)}` : '未签到' }}</p></li></ul><p v-if="!checkInState.participants.length">暂时没有学生报名。</p>
  </template>
  <p v-if="checkInState.writeError" class="community-error" role="alert">{{ checkInState.writeError }}</p><p v-if="checkInState.notice" role="status">{{ checkInState.notice }}</p>
</section></template>
<style scoped>
.community-check-in { overflow-wrap: anywhere; }
.community-check-in label { display: grid; gap: 8px; margin: 16px 0; color: var(--community-text); font-size: 16px; }
.community-check-in textarea { width: 100%; min-width: 0; padding: 12px; border: 1px solid var(--community-line); border-radius: 4px; font: inherit; resize: vertical; }
.community-check-in-people { list-style: none; padding: 0; margin: 20px 0 0; }
.community-check-in-people li { padding: 12px 0; border-top: 1px solid var(--community-line); }
.community-check-in-confirm { margin: 16px 0; padding: 12px 0; border-block: 1px solid var(--community-line); }
</style>
