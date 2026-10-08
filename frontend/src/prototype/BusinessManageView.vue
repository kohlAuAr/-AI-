<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { business, managedClubs, refreshBusiness, reviewApplication } from './business';
import { statusLabels, type Application } from './store';
import Modal from './Modal.vue';
const route = useRoute();
const section = computed(() => route.path.split('/')[2] || 'overview');
const club = computed(() => managedClubs.value.find(c => c.backendId === business.managedClubId));
const filter = ref('pending'); const selected = ref<Application>(); const feedback = ref('');
const requests = computed(() => business.managedApplications.filter(a => filter.value === 'all' || a.status === filter.value));
const pending = computed(() => business.managedApplications.filter(a => a.status === 'pending'));
function openReview(item: Application) { selected.value = item; feedback.value = ''; }
async function decide(approved: boolean) {
  if (selected.value && await reviewApplication(selected.value.id, approved, feedback.value)) selected.value = undefined;
}
</script>
<template>
  <section v-if="business.user?.role !== 'MANAGER'" class="p-panel"><h2>负责人工作台需要对应账号权限</h2><p>普通学生不能通过切换页面获得审核权限，请使用社团负责人账号登录。</p><RouterLink to="/login" class="p-button">前往登录</RouterLink></section>
  <template v-else>
    <div class="p-manager-heading"><div class="p-page-heading"><p class="p-kicker">CLUB WORKSPACE / {{ business.user.name }}</p><h1>{{ section === 'recruitment' ? '每一份热爱，都值得认真回应。' : section === 'members' ? '让每一位伙伴，都找到自己的位置。' : '把社团日常，安排得井井有条。' }}</h1><p>招新审核与成员名单来自数据库，只显示你有权管理的社团。</p></div><label class="p-club-selector">当前管理社团<select v-model="business.managedClubId" aria-label="选择管理社团" :disabled="business.saving" @change="refreshBusiness"><option v-for="item in managedClubs" :key="item.id" :value="item.backendId">{{ item.name }}</option></select></label></div>
    <section v-if="!club && !business.loading" class="p-panel"><h2>当前账号尚未分配管理社团</h2><p>需要由后端建立负责人关系，不能在页面上自行选择任意社团。</p></section>
    <template v-if="club">
      <template v-if="section === 'overview'"><div class="p-stat-grid"><section><strong>{{ pending.length }}</strong><span>待审核申请</span></section><section><strong>{{ business.managedMembers.length }}</strong><span>数据库成员数 · 含负责人</span></section></div><section class="p-panel"><h2>{{ club.name }}</h2><p>{{ club.description }}</p><div class="p-actions"><RouterLink to="/manage/recruitment" class="p-button">查看并审核申请</RouterLink><RouterLink to="/manage/members" class="p-button secondary">查看成员名单</RouterLink></div></section></template>
      <template v-if="section === 'recruitment'"><div class="p-filter-chips"><button v-for="item in [{ id: 'pending', label: '待审核' }, { id: 'approved', label: '已通过' }, { id: 'rejected', label: '未通过' }, { id: 'withdrawn', label: '已撤回' }, { id: 'all', label: '全部记录' }]" :key="item.id" :class="{ selected: filter === item.id }" @click="filter = item.id">{{ item.label }}</button><button :disabled="business.loading" @click="refreshBusiness">刷新</button></div><section class="p-panel"><article v-for="item in requests" :key="item.id" class="p-list-item"><div><h3>{{ item.name }}</h3><p>{{ item.major }} · {{ item.date }}</p><p>{{ item.reason }}</p><p v-if="item.feedback" class="p-review-feedback">审核意见：{{ item.feedback }}</p></div><button v-if="item.status === 'pending'" class="p-button secondary" :disabled="business.saving || business.loading" @click="openReview(item)">查看并审核</button><span v-else class="p-chip" :class="item.status === 'approved' ? 'green' : 'gray'">{{ statusLabels[item.status] }}</span></article><div v-if="!requests.length && !business.loading" class="p-empty"><h3>当前列表暂无申请</h3><p>学生提交申请后，刷新列表即可查看。负责人不能替学生提交申请。</p></div></section></template>
      <template v-if="section === 'members'"><section class="p-panel"><div class="p-section-head"><h2>社团成员名单</h2><span>{{ business.managedMembers.length }} 人 · 数据库记录</span></div><article v-for="item in business.managedMembers" :key="item.id" class="p-list-item"><span class="p-avatar">{{ item.name.slice(0, 1) }}</span><div class="p-grow"><h3>{{ item.name }}</h3><p>{{ item.major }} · {{ item.date }} 加入</p></div><span class="p-chip green">{{ item.role === 'MANAGER' ? '负责人' : '成员' }}</span></article><p v-if="!business.managedMembers.length && !business.loading">暂无成员。</p></section></template>
      <section v-if="['activities', 'finance'].includes(section)" class="p-panel"><h2>该业务尚未接入数据库</h2><p>活动发布、活动报名与经费管理留在后续业务中实现。此前保存的纯前端原型仍可演示这些页面，不会冒充已完成的正式业务。</p></section>
    </template>
  </template>
  <Modal v-if="selected" title="认真回应这份申请" persistent @close="selected = undefined"><h3>{{ selected.name }} · {{ selected.major }}</h3><p>{{ selected.reason }}</p><form class="p-form" @submit.prevent="decide(true)"><label>审核意见（可选）<textarea v-model="feedback" maxlength="300" rows="3"></textarea></label><p class="p-small">通过后，申请状态和成员关系在同一数据库事务中保存。学生刷新个人中心即可看到结果。</p><div class="p-actions"><button type="button" class="p-button secondary" :disabled="business.saving" @click="decide(false)">暂不通过</button><button class="p-button" :disabled="business.saving">{{ business.saving ? '正在保存…' : '通过申请' }}</button></div></form></Modal>
</template>
