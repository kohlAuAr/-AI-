<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, managedClubs, refreshBusiness } from '../prototype/business';
import { activityState, managedActivities, refreshActivities, saveDraft, publishActivity, activityTime, type Draft, type Activity, type Participant } from './activities';
import CommunityDialog from './CommunityDialog.vue';
const showForm = ref(false), selected = ref<Activity>();
const form = ref<Draft>({ title: '', description: '', location: '', startTime: '', registrationDeadline: '', capacity: 30 });
const formError = ref(''), peopleError = ref(''), peopleLoading = ref(false), peopleFor = ref<number>(), participants = ref<Participant[]>([]);
const club = computed(() => managedClubs.value.find(c => c.backendId === business.managedClubId));
onMounted(refreshActivities); watch(() => [business.user?.id, business.managedClubId], () => { peopleFor.value = undefined; participants.value = []; refreshActivities(); });
function openForm() { formError.value = ''; activityState.writeError = ''; showForm.value = !showForm.value; }
async function save() {
  formError.value = '';
  if (!form.value.title.trim() || !form.value.description.trim() || !form.value.location.trim()) { formError.value = '请填写活动名称、介绍和地点。'; return; }
  if (new Date(form.value.registrationDeadline).getTime() <= Date.now() || form.value.registrationDeadline > form.value.startTime) { formError.value = '报名截止时间须晚于当前时间，且不晚于活动开始时间。'; return; }
  if (business.managedClubId !== null && await saveDraft(business.managedClubId, form.value)) { showForm.value = false; form.value = { title: '', description: '', location: '', startTime: '', registrationDeadline: '', capacity: 30 }; }
}
function askPublish(activity: Activity) { activityState.writeError = ''; selected.value = activity; }
async function publish() { if (selected.value && await publishActivity(selected.value.id)) selected.value = undefined; }
async function people(activity: Activity) {
  peopleFor.value = activity.id; peopleLoading.value = true; peopleError.value = ''; participants.value = [];
  try { const rows = await request<Participant[]>(`/manage/activities/${activity.id}/registrations`); if (peopleFor.value === activity.id) participants.value = rows; }
  catch (error) { if (peopleFor.value === activity.id) peopleError.value = errorMessage(error); }
  finally { if (peopleFor.value === activity.id) peopleLoading.value = false; }
}
</script>
<template><div class="community-directory"><div class="community-page-title"><h1>活动管理</h1><RouterLink to="/manage">返回工作台</RouterLink></div>
  <div v-if="business.user?.role !== 'MANAGER'" class="community-list-message"><h2>请使用社团负责人账号</h2><p>只能管理你负责社团的活动。</p><RouterLink to="/login" class="community-button">前往登录</RouterLink></div>
  <template v-else><div class="community-manager-toolbar"><label>管理社团<select v-model="business.managedClubId" :disabled="activityState.saving || business.loading" @change="refreshBusiness"><option v-for="item in managedClubs" :key="item.id" :value="item.backendId">{{ item.name }}</option></select></label><button v-if="club" class="community-button" :disabled="activityState.saving" @click="openForm">{{ showForm ? '收起表单' : '新建活动' }}</button></div>
    <p v-if="business.error" class="community-error" role="alert">{{ business.error }}</p>
    <form v-if="showForm && club" class="community-event-form" @submit.prevent="save"><h2>新建活动草稿</h2><p>保存后不会对学生展示，需要再次确认发布。</p><label>活动名称<input v-model="form.title" required maxlength="100" /></label><label>活动介绍<textarea v-model="form.description" required maxlength="2000" rows="4"></textarea></label><label>活动地点<input v-model="form.location" required maxlength="200" placeholder="例如：学生中心 204" /></label><div class="community-form-columns"><label>活动开始时间<input v-model="form.startTime" type="datetime-local" required /></label><label>报名截止时间<input v-model="form.registrationDeadline" type="datetime-local" required /></label></div><label>人数上限（1—500 人）<input v-model.number="form.capacity" type="number" required min="1" max="500" /></label><p v-if="formError || activityState.writeError" class="community-error" role="alert">{{ formError || activityState.writeError }}</p><button class="community-button" :disabled="activityState.saving">{{ activityState.saving ? '正在保存…' : '保存草稿' }}</button></form>
    <div class="community-list-heading"><h2>{{ club?.name || '尚未分配管理社团' }}</h2><button class="community-text-link" :disabled="activityState.loading" @click="refreshActivities">刷新活动</button></div>
    <p v-if="activityState.error" class="community-error" role="alert">{{ activityState.error }}</p><p v-if="activityState.loading" class="community-list-loading">正在读取活动…</p>
    <template v-else-if="!activityState.error"><article v-for="activity in managedActivities" :key="activity.id" class="community-managed-event"><div class="community-club-title"><h3>{{ activity.title }}</h3><span class="community-recruiting">{{ activity.status === 'DRAFT' ? '草稿 · 学生不可见' : '已发布' }}</span></div><p>{{ activityTime(activity.startTime) }} · {{ activity.location }}</p><p>报名截止 {{ activityTime(activity.registrationDeadline) }} · {{ activity.enrolled }} / {{ activity.capacity }} 人</p><div class="community-dialog-actions"><button v-if="activity.status === 'DRAFT'" class="community-button secondary" :disabled="activityState.saving" @click="askPublish(activity)">确认发布</button><template v-else><RouterLink :to="`/activities/${activity.id}`" class="community-text-link">活动详情</RouterLink><button class="community-text-link" @click="people(activity)">查看报名名单</button></template></div><section v-if="peopleFor === activity.id" class="community-participants"><h4>当前报名名单</h4><p v-if="peopleLoading" role="status">正在读取名单…</p><p v-else-if="peopleError" class="community-error" role="alert">{{ peopleError }}</p><template v-else><p v-for="person in participants" :key="person.id">{{ person.name }} · {{ person.major }}<br /><small>{{ activityTime(person.registeredAt) }} 报名</small></p><p v-if="!participants.length">暂时没有学生报名。</p></template></section></article><div v-if="!managedActivities.length" class="community-list-message"><h3>暂无活动</h3><p>点击“新建活动”，填写内容并保存草稿。</p></div></template>
    <p class="community-list-disclosure">草稿、发布和报名使用本地数据库 · 虚构账号与资料<br />地点直接填写，不包含场地预约；编辑、取消、签到和经费接口已有后端实现，本页面尚未接入这些操作；不包含学校级经费审批。</p>
  </template>
  <CommunityDialog v-if="selected" title="确认发布活动" :busy="activityState.saving" @close="selected = undefined"><h3>{{ selected.title }}</h3><p>{{ activityTime(selected.startTime) }} · {{ selected.location }}</p><p>报名截止 {{ activityTime(selected.registrationDeadline) }} · {{ selected.capacity }} 人</p><p>发布后，全校学生可查看并报名。请确认时间、地点和名额无误。</p><p v-if="activityState.writeError" class="community-error" role="alert">{{ activityState.writeError }}</p><div class="community-dialog-actions"><button class="community-button secondary" :disabled="activityState.saving" @click="selected = undefined">返回</button><button class="community-button" :disabled="activityState.saving" @click="publish">{{ activityState.saving ? '正在发布…' : '发布活动' }}</button></div></CommunityDialog>
</div></template>
