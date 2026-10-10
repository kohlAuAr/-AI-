<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { business, clubCatalog } from '../prototype/business';
import { activityState, myRegistrations, refreshActivities, activityTime, registrationClosed, registerActivity, cancelActivityRegistration, checkInActivity } from './activities';
import CommunityIcon from './CommunityIcon.vue';
import CommunityDialog from './CommunityDialog.vue';
const route = useRoute(), confirmation = ref('');
const code = ref('');
const activity = computed(() => activityState.activities.find(a => a.id === Number(route.params.id)));
const registration = computed(() => myRegistrations.value.find(r => r.activityId === activity.value?.id && r.status === 'REGISTERED'));
const registered = computed(() => !!registration.value);
const started = computed(() => !!activity.value && Date.now() >= new Date(activity.value.startTime).getTime());
function confirm(action: string) { activityState.writeError = ''; confirmation.value = action; }
async function save() { if (activity.value && await (confirmation.value === 'signup' ? registerActivity(activity.value.id) : cancelActivityRegistration(activity.value.id))) confirmation.value = ''; }
async function signIn() {
  const id = activity.value?.id, userId = business.user?.id;
  if (id && await checkInActivity(id, code.value) && id === activity.value?.id && userId === business.user?.id) code.value = '';
}
watch([() => route.params.id, () => business.user?.id, () => business.user?.role], () => { code.value = ''; confirmation.value = ''; activityState.writeError = ''; }, { flush: 'sync' });
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
</script>
<template><div class="community-directory community-event-detail"><RouterLink to="/activities" class="community-back"><CommunityIcon name="chevron" />返回活动列表</RouterLink>
  <div v-if="activityState.error" class="community-list-message" role="alert"><p>{{ activityState.error }}</p><button @click="refreshActivities">重新加载</button></div><p v-else-if="activityState.loading" class="community-list-loading">正在读取活动…</p>
  <template v-else-if="activity"><div class="community-event-heading"><p>{{ clubCatalog.find(c => c.backendId === activity?.clubId)?.name }}</p><h1>{{ activity.title }}</h1><span class="community-recruiting">{{ registration?.checkedInAt ? '已签到' : registered ? '已报名' : registrationClosed(activity) ? '报名已截止' : activity.enrolled >= activity.capacity ? '名额已满' : '报名中' }}</span></div>
    <dl class="community-event-facts"><div><dt>活动时间</dt><dd>{{ activityTime(activity.startTime) }}</dd></div><div><dt>活动地点</dt><dd>{{ activity.location }}</dd></div><div><dt>报名截止</dt><dd>{{ activityTime(activity.registrationDeadline) }}</dd></div><div><dt>报名人数</dt><dd>{{ activity.enrolled }} / {{ activity.capacity }} 人</dd></div><div><dt>参与范围</dt><dd>全校学生，无需先加入社团</dd></div></dl>
    <section class="community-event-description"><h2>活动介绍</h2><p>{{ activity.description }}</p></section>
    <section v-if="business.user?.role === 'STUDENT' && registered" class="community-student-check-in" aria-labelledby="student-check-in-title">
      <div class="community-list-heading"><h2 id="student-check-in-title">现场签到</h2><button type="button" class="community-text-link" :disabled="activityState.loading || activityState.saving" @click="refreshActivities">刷新签到状态</button></div>
      <p v-if="registration?.checkedInAt" role="status">已签到 · {{ activityTime(registration.checkedInAt) }}</p>
      <form v-else-if="activity.checkInOpen" @submit.prevent="signIn"><p id="check-in-help">向现场负责人获取签到码，可直接粘贴。签到后不可取消报名。</p><label for="student-check-in-code">现场签到码</label><input id="student-check-in-code" v-model="code" required maxlength="64" autocomplete="off" autocapitalize="none" spellcheck="false" aria-describedby="check-in-help check-in-error" :disabled="activityState.saving" /><button type="submit" class="community-button" :disabled="activityState.saving">{{ activityState.saving ? '正在签到…' : '确认签到' }}</button></form>
      <p v-else>签到尚未开启或已关闭。请联系现场负责人，开启后点击“刷新签到状态”。</p>
      <p v-if="activityState.writeError && !confirmation" id="check-in-error" class="community-error" role="alert">{{ activityState.writeError }} 可刷新记录核对后重试。</p>
    </section>
    <div class="community-event-actions"><RouterLink v-if="!business.user" to="/login" class="community-button">登录后报名</RouterLink><p v-else-if="business.user.role !== 'STUDENT'">请使用学生账号报名。<RouterLink to="/manage/activities">管理社团活动</RouterLink></p><button v-else-if="registered" class="community-button secondary" :disabled="started || !!registration?.checkedInAt || activityState.saving" @click="confirm('cancel')">{{ registration?.checkedInAt ? '已签到 · 不可取消报名' : started ? '已报名 · 活动已开始' : '取消报名' }}</button><button v-else class="community-button" :disabled="registrationClosed(activity) || activity.enrolled >= activity.capacity || activityState.saving" @click="confirm('signup')">{{ registrationClosed(activity) ? '报名已截止' : activity.enrolled >= activity.capacity ? '名额已满' : '报名参加' }}</button><RouterLink to="/me?tab=registrations" class="community-text-link">查看我的报名</RouterLink></div>
    <p class="community-list-disclosure">本地虚构活动 · 报名使用当前登录账号并保存到数据库<br />活动开始前且未签到可取消；重新报名仍需满足截止时间和名额条件。</p>
  </template><div v-else class="community-list-message"><h1>没有找到已发布活动</h1><p>活动可能尚未发布，或链接有误。</p></div>
  <CommunityDialog v-if="confirmation && activity" :title="confirmation === 'signup' ? '确认报名' : '确认取消报名'" :busy="activityState.saving" @close="confirmation = ''"><p>{{ activity.title }}</p><p>{{ activityTime(activity.startTime) }} · {{ activity.location }}</p><p>{{ business.user?.name }} · {{ confirmation === 'signup' ? '确认后保存报名记录。' : '确认后释放名额，保留取消记录。' }}</p><p v-if="activityState.writeError" class="community-error" role="alert">{{ activityState.writeError }}</p><div class="community-dialog-actions"><button class="community-button secondary" :disabled="activityState.saving" @click="confirmation = ''">返回</button><button class="community-button" :disabled="activityState.saving" @click="save">{{ activityState.saving ? '正在保存…' : confirmation === 'signup' ? '确认报名' : '确认取消' }}</button></div></CommunityDialog>
</div></template>
<style scoped>
.community-student-check-in { margin: 24px 0; padding: 20px 0; border-block: 1px solid var(--community-line); overflow-wrap: anywhere; }
.community-student-check-in h2 { font-size: 18px; }
.community-student-check-in p { margin: 12px 0; line-height: 1.8; }
.community-student-check-in form { display: grid; gap: 12px; }
.community-student-check-in input { width: 100%; min-width: 0; min-height: 44px; border: 1px solid var(--community-line); border-radius: 4px; padding: 12px; font: inherit; font-size: 16px; }
.community-student-check-in form > button { justify-self: start; }
</style>
