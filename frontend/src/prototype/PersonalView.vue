<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { clubs } from './data';
import { state, statusLabels, dateLabel, toast, resetPrototype, type Application } from './store';
import Icon from './Icon.vue';
import Modal from './Modal.vue';
const route = useRoute(); const tab = ref('applications'); const modal = ref('');
const interests = ['摄影', '编程', '户外', '音乐', '艺术', '公益', '运动', '阅读', '科技', '表达'];
const selectedInterests = ref([...state.profile.interests]);
const applications = computed(() => state.applications.filter(a => a.mine));
const memberships = computed(() => applications.value.filter(a => a.status === 'approved'));
const registrations = computed(() => state.activities.filter(a => state.registrations.includes(a.id)));
const favorites = computed(() => clubs.filter(c => state.favorites.includes(c.id)));
const pendingWithdrawal = ref<Application>();
function toggleInterest(tag: string) { const i = selectedInterests.value.indexOf(tag); if (i < 0) selectedInterests.value.push(tag); else selectedInterests.value.splice(i, 1); }
function saveInterests() { state.profile.interests = [...selectedInterests.value]; modal.value = ''; toast('兴趣偏好已保存，仅用于原型标签筛选'); }
function withdraw() { if (pendingWithdrawal.value?.status === 'pending') pendingWithdrawal.value.status = 'withdrawn'; modal.value = ''; toast('已撤回模拟申请'); }
</script>
<template>
  <template v-if="route.path === '/messages'"><div class="p-page-heading p-inline"><div><h1>消息中心</h1><p>这里收集你的申请与报名结果。</p></div><button class="p-button secondary" @click="state.notices.forEach(n => n.read = true); toast('全部消息已读')">全部已读</button></div><div class="p-notice-list"><article v-for="notice in state.notices" :key="notice.id" class="p-panel p-notice" :class="{ unread: !notice.read }"><span class="p-mini-icon"><Icon name="bell" /></span><div><div class="p-inline"><h3>{{ notice.title }}</h3><small>{{ notice.date }}</small></div><p>{{ notice.text }}</p><RouterLink :to="notice.path" class="p-text-button" @click="notice.read = true">查看相关内容<Icon name="arrow" /></RouterLink></div></article></div></template>
  <template v-else>
    <section class="p-profile"><div class="p-profile-avatar">林</div><div><h1>你好，{{ state.profile.name }}。</h1><p>{{ state.profile.major }} · 演示身份</p><div class="p-tags"><span v-for="tag in state.profile.interests" :key="tag">{{ tag }}</span></div></div><button class="p-button secondary" @click="selectedInterests = [...state.profile.interests]; modal = 'interests'">编辑兴趣偏好</button></section>
    <div class="p-stat-grid"><section><strong>{{ memberships.length }}</strong><span>已加入社团</span></section><section><strong>{{ applications.filter(a => a.status === 'pending').length }}</strong><span>待审核申请</span></section><section><strong>{{ registrations.length }}</strong><span>已报名活动</span></section><section><strong>{{ favorites.length }}</strong><span>收藏社团</span></section></div>
    <div class="p-tabs" role="tablist" aria-label="我的记录"><button v-for="item in [{ id: 'applications', label: '入社申请' }, { id: 'memberships', label: '我的社团' }, { id: 'registrations', label: '活动报名' }, { id: 'favorites', label: '我的收藏' }]" :key="item.id" role="tab" :aria-selected="tab === item.id" :class="{ selected: tab === item.id }" @click="tab = item.id">{{ item.label }}</button></div>
    <section class="p-panel" role="tabpanel">
      <template v-if="tab === 'applications'"><article v-for="item in applications" :key="item.id" class="p-list-item"><div><h3>{{ clubs.find(c => c.id === item.clubId)?.name }}</h3><p>{{ item.date }} · {{ item.reason }}</p><p v-if="item.feedback" class="p-review-feedback">审核意见：{{ item.feedback }}</p></div><div class="p-record-actions"><span class="p-chip" :class="item.status === 'approved' ? 'green' : item.status === 'pending' ? 'yellow' : 'gray'">{{ statusLabels[item.status] }}</span><button v-if="item.status === 'pending'" class="p-text-button" @click="pendingWithdrawal = item; modal = 'withdraw'">撤回申请</button><RouterLink :to="`/clubs/${item.clubId}`" class="p-text-button">社团详情 ↗</RouterLink></div></article><div v-if="!applications.length" class="p-empty"><Icon name="clubs" /><h3>你的社团故事，还没开始。</h3><p>找到一个感兴趣的社团，提交你的第一份申请。</p><RouterLink to="/recruitment" class="p-button">看看招新社团<Icon name="arrow" /></RouterLink></div></template>
      <template v-if="tab === 'memberships'"><RouterLink v-for="item in memberships" :key="item.id" :to="`/clubs/${item.clubId}`" class="p-list-item"><div><h3>{{ clubs.find(c => c.id === item.clubId)?.name }}</h3><p>申请已通过 · 已建立模拟成员关系</p></div><span class="p-chip green">正式成员 · 演示</span></RouterLink><div v-if="!memberships.length" class="p-empty"><h3>暂未加入社团</h3><p>负责人通过申请后，这里会出现你的社团。</p><RouterLink to="/manage/recruitment" class="p-button secondary">体验负责人审核 ↗</RouterLink></div></template>
      <template v-if="tab === 'registrations'"><RouterLink v-for="item in registrations" :key="item.id" :to="`/activities/${item.id}`" class="p-list-item"><div><h3>{{ item.title }}</h3><p>{{ dateLabel(item.date) }} {{ item.time }} · {{ item.location }}</p></div><span class="p-chip green">已报名 · 查看详情</span></RouterLink><div v-if="!registrations.length" class="p-empty"><Icon name="calendar" /><h3>给下一场活动，留一个位置。</h3><p>报名后可以在这里找到活动时间和地点。</p><RouterLink to="/activities" class="p-button">发现近期活动 ↗</RouterLink></div></template>
      <template v-if="tab === 'favorites'"><RouterLink v-for="item in favorites" :key="item.id" :to="`/clubs/${item.id}`" class="p-list-item"><div><h3>{{ item.name }}</h3><p>{{ item.slogan }}</p></div><Icon name="arrow" /></RouterLink><div v-if="!favorites.length" class="p-empty"><Icon name="heart" /><h3>收藏一份兴趣，稍后再见。</h3><p>在社团卡片上点击爱心即可收藏。</p><RouterLink to="/clubs" class="p-button secondary">去社团广场 ↗</RouterLink></div></template>
    </section><section class="p-assistant-strip"><div><Icon name="grid" /><div><h3>也想看看社团负责人怎么使用？</h3><p>角色切换仅用于原型展示，不代表真实权限认证。</p></div></div><RouterLink to="/manage" class="p-text-button">进入负责人工作台<Icon name="arrow" /></RouterLink></section><button class="p-text-button p-reset" @click="modal = 'reset'">重置本浏览器的演示记录</button>
  </template>
  <Modal v-if="modal === 'interests'" title="让我们了解一点你的兴趣" @close="modal = ''"><p>选择你喜欢的方向。当前只用于标签匹配演示，不代表已实现 AI 推荐。</p><div class="p-interest-picker"><button v-for="tag in interests" :key="tag" :aria-pressed="selectedInterests.includes(tag)" :class="{ selected: selectedInterests.includes(tag) }" @click="toggleInterest(tag)">{{ tag }}</button></div><button class="p-button full" @click="saveInterests">保存偏好<Icon name="check" /></button></Modal>
  <Modal v-if="modal === 'withdraw'" title="撤回这份模拟申请？" @close="modal = ''"><p>撤回后，可回到社团详情重新申请。已通过的申请不能在这里撤回。</p><button class="p-button full" @click="withdraw">确认撤回</button></Modal>
  <Modal v-if="modal === 'reset'" title="恢复原型的初始数据？" @close="modal = ''"><p>只清除本浏览器的模拟申请、报名、收藏和兴趣记录，不会删除后端数据库中的资料。</p><button class="p-button full" @click="resetPrototype(); modal = ''">确认恢复演示数据</button></Modal>
</template>
