<script setup lang="ts">
import { ref } from 'vue';
import { business, clubCatalog as clubs, refreshBusiness, withdrawApplication } from './business';
import { statusLabels, state } from './store';
import Modal from './Modal.vue';
const tab = ref('applications'); const withdrawing = ref('');
async function withdraw() { if (await withdrawApplication(withdrawing.value)) withdrawing.value = ''; }
</script>
<template>
  <section v-if="!business.user" class="p-panel"><h2>登录后查看你的申请与社团</h2><p>这些记录来自校园数据库，不使用原型中的模拟身份。</p><RouterLink to="/login" class="p-button">前往登录</RouterLink></section>
  <template v-else>
    <section class="p-profile"><div class="p-profile-avatar">{{ business.user.name.slice(0, 1) }}</div><div><p class="p-kicker">MY CAMPUS LIFE</p><h1>你好，{{ business.user.name }}。</h1><p>{{ business.user.major }} · {{ business.user.role === 'MANAGER' ? '社团负责人' : '学生' }}账号</p></div><button class="p-button secondary" :disabled="business.loading" @click="refreshBusiness">刷新记录</button></section>
    <div class="p-stat-grid"><section><strong>{{ business.memberships.length }}</strong><span>已加入社团</span></section><section><strong>{{ business.applications.filter(a => a.status === 'pending').length }}</strong><span>待审核申请</span></section><section><strong>{{ state.registrations.length }}</strong><span>模拟活动报名 · 未接入</span></section><section><strong>{{ state.favorites.length }}</strong><span>本浏览器收藏 · 未接入</span></section></div>
    <div class="p-tabs" role="tablist" aria-label="我的数据库记录"><button v-for="item in [{ id: 'applications', label: '入社申请' }, { id: 'memberships', label: '我的社团' }]" :key="item.id" role="tab" :aria-selected="tab === item.id" :class="{ selected: tab === item.id }" @click="tab = item.id">{{ item.label }}</button></div>
    <section class="p-panel" role="tabpanel">
      <template v-if="tab === 'applications'"><article v-for="item in business.applications" :key="item.id" class="p-list-item"><div><h3>{{ clubs.find(c => c.id === item.clubId)?.name }}</h3><p>{{ item.date }} · {{ item.reason }}</p><p v-if="item.feedback" class="p-review-feedback">审核意见：{{ item.feedback }}</p></div><div class="p-record-actions"><span class="p-chip" :class="item.status === 'approved' ? 'green' : item.status === 'pending' ? 'yellow' : 'gray'">{{ statusLabels[item.status] }}</span><button v-if="item.status === 'pending'" class="p-text-button" :disabled="business.saving" @click="withdrawing = item.id">撤回申请</button><RouterLink :to="`/clubs/${item.clubId}`" class="p-text-button">社团详情 ↗</RouterLink></div></article><div v-if="!business.applications.length && !business.loading" class="p-empty"><h3>你的社团故事，还没开始。</h3><p>提交第一份申请后，可以在这里查看审核结果。</p><RouterLink to="/recruitment" class="p-button">看看招新社团</RouterLink></div></template>
      <template v-else><RouterLink v-for="item in business.memberships" :key="item.id" :to="`/clubs/${item.clubId}`" class="p-list-item"><div><h3>{{ clubs.find(c => c.id === item.clubId)?.name }}</h3><p>{{ item.date }} 加入 · 成员关系已保存到数据库</p></div><span class="p-chip green">{{ item.role === 'MANAGER' ? '负责人' : '社团成员' }}</span></RouterLink><div v-if="!business.memberships.length && !business.loading" class="p-empty"><h3>暂未加入社团</h3><p>负责人通过申请后，这里会出现你的社团。</p></div></template>
    </section>
    <section class="p-panel"><h2>其他功能仍在逐步接入</h2><p>活动报名、收藏和消息仍为浏览器模拟；它们不会自动转成你的正式账号记录。当前真实业务为登录、入社申请、审核、撤回和成员关系。</p><RouterLink v-if="business.user.role === 'MANAGER'" to="/manage/recruitment" class="p-text-button">进入负责人工作台 ↗</RouterLink></section>
  </template>
  <Modal v-if="withdrawing" title="撤回这份申请？" persistent @close="withdrawing = ''"><p>只可撤回待审核申请。撤回记录会保留，可以再次申请。</p><button class="p-button" :disabled="business.saving" @click="withdraw">{{ business.saving ? '正在保存…' : '确认撤回' }}</button></Modal>
</template>
