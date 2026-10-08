<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { business, clubCatalog } from '../prototype/business';
import { activityState, myRegistrations, refreshActivities, activityTime, registrationClosed, registerActivity, cancelActivityRegistration } from './activities';
import CommunityIcon from './CommunityIcon.vue';
import CommunityDialog from './CommunityDialog.vue';
const route = useRoute(), confirmation = ref('');
const activity = computed(() => activityState.activities.find(a => a.id === Number(route.params.id)));
const registered = computed(() => myRegistrations.value.some(r => r.activityId === activity.value?.id && r.status === 'REGISTERED'));
const started = computed(() => !!activity.value && Date.now() >= new Date(activity.value.startTime).getTime());
function confirm(action: string) { activityState.writeError = ''; confirmation.value = action; }
async function save() { if (activity.value && await (confirmation.value === 'signup' ? registerActivity(activity.value.id) : cancelActivityRegistration(activity.value.id))) confirmation.value = ''; }
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
</script>
<template><div class="community-directory community-event-detail"><RouterLink to="/activities" class="community-back"><CommunityIcon name="chevron" />返回活动列表</RouterLink>
  <div v-if="activityState.error" class="community-list-message" role="alert"><p>{{ activityState.error }}</p><button @click="refreshActivities">重新加载</button></div><p v-else-if="activityState.loading" class="community-list-loading">正在读取活动…</p>
  <template v-else-if="activity"><div class="community-event-heading"><p>{{ clubCatalog.find(c => c.backendId === activity?.clubId)?.name }}</p><h1>{{ activity.title }}</h1><span class="community-recruiting">{{ registered ? '已报名' : registrationClosed(activity) ? '报名已截止' : activity.enrolled >= activity.capacity ? '名额已满' : '报名中' }}</span></div>
    <dl class="community-event-facts"><div><dt>活动时间</dt><dd>{{ activityTime(activity.startTime) }}</dd></div><div><dt>活动地点</dt><dd>{{ activity.location }}</dd></div><div><dt>报名截止</dt><dd>{{ activityTime(activity.registrationDeadline) }}</dd></div><div><dt>报名人数</dt><dd>{{ activity.enrolled }} / {{ activity.capacity }} 人</dd></div><div><dt>参与范围</dt><dd>全校学生，无需先加入社团</dd></div></dl>
    <section class="community-event-description"><h2>活动介绍</h2><p>{{ activity.description }}</p></section>
    <div class="community-event-actions"><RouterLink v-if="!business.user" to="/login" class="community-button">登录后报名</RouterLink><p v-else-if="business.user.role !== 'STUDENT'">请使用学生账号报名。<RouterLink to="/manage/activities">管理社团活动</RouterLink></p><button v-else-if="registered" class="community-button secondary" :disabled="started || activityState.saving" @click="confirm('cancel')">{{ started ? '已报名 · 活动已开始' : '取消报名' }}</button><button v-else class="community-button" :disabled="registrationClosed(activity) || activity.enrolled >= activity.capacity || activityState.saving" @click="confirm('signup')">{{ registrationClosed(activity) ? '报名已截止' : activity.enrolled >= activity.capacity ? '名额已满' : '报名参加' }}</button><RouterLink to="/me?tab=registrations" class="community-text-link">查看我的报名</RouterLink></div>
    <p class="community-list-disclosure">本地虚构活动 · 报名使用当前登录账号并保存到数据库<br />活动开始前可取消；重新报名仍需满足截止时间和名额条件。</p>
  </template><div v-else class="community-list-message"><h1>没有找到已发布活动</h1><p>活动可能尚未发布，或链接有误。</p></div>
  <CommunityDialog v-if="confirmation && activity" :title="confirmation === 'signup' ? '确认报名' : '确认取消报名'" :busy="activityState.saving" @close="confirmation = ''"><p>{{ activity.title }}</p><p>{{ activityTime(activity.startTime) }} · {{ activity.location }}</p><p>{{ business.user?.name }} · {{ confirmation === 'signup' ? '确认后保存报名记录。' : '确认后释放名额，保留取消记录。' }}</p><p v-if="activityState.writeError" class="community-error" role="alert">{{ activityState.writeError }}</p><div class="community-dialog-actions"><button class="community-button secondary" :disabled="activityState.saving" @click="confirmation = ''">返回</button><button class="community-button" :disabled="activityState.saving" @click="save">{{ activityState.saving ? '正在保存…' : confirmation === 'signup' ? '确认报名' : '确认取消' }}</button></div></CommunityDialog>
</div></template>
