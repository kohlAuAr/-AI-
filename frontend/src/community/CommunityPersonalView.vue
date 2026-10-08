<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { business, businessMode, clubCatalog, refreshBusiness, withdrawApplication } from '../prototype/business';
import { state, statusLabels, dateLabel, toast, resetPrototype } from '../prototype/store';
import { activityState, myRegistrations, refreshActivities, activityTime } from './activities';
import CommunityIcon from './CommunityIcon.vue';
import CommunityDialog from './CommunityDialog.vue';
import CommunityProfileEditor from './CommunityProfileEditor.vue';
const route = useRoute();
const tabs = [{ id: 'applications', label: '入社申请' }, { id: 'memberships', label: '我的社团' }, { id: 'registrations', label: '活动报名' }, { id: 'favorites', label: '我的收藏' }];
const tab = ref(tabs.some(t => t.id === route.query.tab) ? String(route.query.tab) : 'applications');
watch(() => route.query.tab, value => { if (tabs.some(t => t.id === value)) tab.value = String(value); });
const modal = ref(''), withdrawing = ref('');
const user = computed(() => businessMode ? business.user : state.profile);
const accountLabel = computed(() => !businessMode ? '原型演示身份' : business.user?.role === 'PLATFORM_ADMIN' ? '平台管理员' : business.user?.role === 'MANAGER' ? '社团负责人' : '学生账号');
const applications = computed(() => businessMode ? business.applications : state.applications.filter(a => a.mine));
const memberships = computed(() => businessMode ? business.memberships : applications.value.filter(a => a.status === 'approved').map(a => ({ id: a.id, clubId: a.clubId, role: 'MEMBER', date: a.date })));
const registrations = computed(() => businessMode ? myRegistrations.value.map(r => ({ id: String(r.id), activityId: String(r.activityId), title: r.title, time: activityTime(r.startTime), location: r.location, status: r.status }))
  : state.activities.filter(a => state.registrations.includes(a.id)).map(a => ({ id: a.id, activityId: a.id, title: a.title, time: `${dateLabel(a.date)} ${a.time}`, location: a.location, status: 'REGISTERED' })));
const favorites = computed(() => clubCatalog.value.filter(c => state.favorites.includes(c.id)));
const interests = ['摄影', '编程', '户外', '音乐', '艺术', '公益', '运动', '阅读', '科技', '表达'];
const selectedInterests = ref([...state.profile.interests]);
const profile = computed(() => business.profile?.id === business.user?.id ? business.profile : null);
watch(() => business.user?.id, () => { modal.value = ''; });
onMounted(refreshActivities); watch(() => business.user?.id, refreshActivities);
async function refresh() { await refreshBusiness(); await refreshActivities(); }
async function withdraw() {
  if (businessMode) { if (await withdrawApplication(withdrawing.value)) withdrawing.value = ''; }
  else { const item = applications.value.find(a => a.id === withdrawing.value); if (item?.status === 'pending') item.status = 'withdrawn'; withdrawing.value = ''; toast('已撤回模拟申请'); }
}
function toggleInterest(tag: string) { const i = selectedInterests.value.indexOf(tag); if (i < 0) selectedInterests.value.push(tag); else selectedInterests.value.splice(i, 1); }
function saveInterests() { state.profile.interests = [...selectedInterests.value]; modal.value = ''; toast('兴趣偏好已保存，仅用于原型标签筛选'); }
</script>
<template>
  <div class="community-directory community-personal-page">
    <div class="community-page-title"><h1>我的</h1><RouterLink to="/messages">消息中心<CommunityIcon name="chevron" /></RouterLink></div>
    <div v-if="businessMode && business.error" class="community-list-message" role="alert"><h3>记录暂时无法读取</h3><p>{{ business.error }}。真实业务不可用时不会自动切换成模拟申请。</p><button type="button" @click="refresh">重新加载</button></div>
    <div v-if="!user" class="community-list-message"><h2>登录后查看你的申请与社团</h2><p>登录后，在这里查看报名和审核进度。</p><RouterLink to="/login" class="community-button">前往登录</RouterLink></div>
    <template v-else>
      <RouterLink v-if="businessMode && business.user?.role === 'PLATFORM_ADMIN'" to="/platform/banners" class="community-help-row"><CommunityIcon name="home" /><strong>首页内容管理</strong><CommunityIcon name="chevron" /></RouterLink>
      <section class="community-profile-summary"><span class="community-profile-avatar">{{ user.name.slice(0, 1) }}</span><div><h2>你好，{{ user.name }}。</h2><p>{{ user.major }}</p><span>{{ accountLabel }}</span></div><button v-if="businessMode" type="button" class="community-text-button" :disabled="business.loading || !profile" @click="modal = 'profile'">编辑资料</button><button v-else type="button" class="community-text-button" @click="selectedInterests = [...state.profile.interests]; modal = 'interests'">编辑兴趣</button></section>
      <template v-if="businessMode && profile"><div class="community-profile-interests"><span v-for="tag in profile.interests" :key="tag">{{ tag }}</span><span v-if="!profile.interests.length">尚未选择兴趣</span></div><p class="community-profile-time">空闲时间：{{ profile.availableTime || '尚未填写' }}</p></template>
      <div v-if="businessMode" class="community-profile-refresh"><button type="button" class="community-text-button" :disabled="business.loading || activityState.loading" @click="refresh">刷新记录</button></div>
      <div v-if="!businessMode" class="community-profile-interests"><span v-for="tag in state.profile.interests" :key="tag">{{ tag }}</span></div>
      <div class="community-profile-counts"><button type="button" @click="tab = 'memberships'"><strong>{{ memberships.length }}</strong><span>已加入社团</span></button><button type="button" @click="tab = 'applications'"><strong>{{ applications.filter(a => a.status === 'pending').length }}</strong><span>待审核申请</span></button><button type="button" @click="tab = 'registrations'"><strong>{{ registrations.filter(r => r.status === 'REGISTERED').length }}</strong><span>已报名活动</span></button><button type="button" @click="tab = 'favorites'"><strong>{{ favorites.length }}</strong><span>本地收藏</span></button></div>
      <div class="community-category-tabs community-personal-tabs" role="group" aria-label="我的记录"><button v-for="item in tabs" :key="item.id" type="button" :aria-pressed="tab === item.id" :class="{ active: tab === item.id }" @click="tab = item.id">{{ item.label }}</button></div>
      <section class="community-personal-records" :aria-label="tabs.find(t => t.id === tab)?.label">
        <p v-if="businessMode && business.loading" class="community-list-loading" role="status">正在读取记录…</p>
        <template v-else-if="tab === 'applications'"><article v-for="item in applications" :key="item.id" class="community-record-row"><div class="community-record-copy"><div class="community-record-heading"><h3>{{ clubCatalog.find(c => c.id === item.clubId)?.name }}</h3><span class="community-record-status" :class="{ muted: item.status !== 'approved' && item.status !== 'pending' }">{{ statusLabels[item.status] }}</span></div><p>{{ item.reason }}</p><p v-if="item.feedback">审核意见：{{ item.feedback }}</p><time>{{ item.date }}</time><div class="community-record-actions"><RouterLink :to="`/clubs/${item.clubId}`" class="community-text-link">社团详情<CommunityIcon name="chevron" /></RouterLink><button v-if="item.status === 'pending'" type="button" class="community-text-button" :disabled="businessMode && business.saving" @click="withdrawing = item.id">撤回申请</button></div></div></article><div v-if="!applications.length && !business.error" class="community-list-message"><h3>还没有入社申请</h3><p>找到感兴趣的社团，提交申请后可以在这里查看结果。</p><RouterLink to="/recruitment" class="community-text-link">看看招新社团<CommunityIcon name="chevron" /></RouterLink></div></template>
        <template v-else-if="tab === 'memberships'"><RouterLink v-for="item in memberships" :key="item.id" :to="`/clubs/${item.clubId}`" class="community-record-row"><span class="community-small-cover" :class="`cover-${item.clubId}`"><CommunityIcon :name="item.clubId" /></span><div class="community-record-copy"><h3>{{ clubCatalog.find(c => c.id === item.clubId)?.name }}</h3><p>{{ item.date }} 加入</p></div><span class="community-record-status">{{ item.role === 'MANAGER' ? '负责人' : '社团成员' }}</span></RouterLink><div v-if="!memberships.length && !business.error" class="community-list-message"><h3>暂未加入社团</h3><p>负责人通过申请后，这里会出现你的社团。</p><RouterLink v-if="!businessMode" to="/manage/recruitment" class="community-text-link">体验负责人审核</RouterLink></div></template>
        <template v-else-if="tab === 'registrations'"><div v-if="businessMode && activityState.error" class="community-list-message" role="alert"><h3>报名记录暂时无法读取</h3><p>{{ activityState.error }}</p><button type="button" @click="refreshActivities">重新加载报名</button></div><p v-else-if="businessMode && activityState.loading" class="community-list-loading" role="status">正在读取报名…</p><template v-else><RouterLink v-for="item in registrations" :key="item.id" :to="`/activities/${item.activityId}`" class="community-record-row"><div class="community-record-copy"><h3>{{ item.title }}</h3><p>{{ item.time }}</p><p>{{ item.location }}</p></div><span class="community-record-status" :class="{ muted: item.status !== 'REGISTERED' }">{{ item.status === 'REGISTERED' ? '已报名' : '已取消' }}</span></RouterLink><div v-if="!registrations.length" class="community-list-message"><h3>暂无活动报名</h3><p>报名后可以在这里查看活动时间和地点。</p><RouterLink to="/activities" class="community-text-link">看看近期活动</RouterLink></div></template></template>
        <template v-else><p class="community-section-description">收藏仅保存在当前浏览器，尚未接入账号收藏接口。</p><RouterLink v-for="item in favorites" :key="item.id" :to="`/clubs/${item.id}`" class="community-record-row"><span class="community-small-cover" :class="`cover-${item.id}`"><CommunityIcon :name="item.id" /></span><div class="community-record-copy"><h3>{{ item.name }}</h3><p>{{ item.description }}</p></div><CommunityIcon name="chevron" /></RouterLink><div v-if="!favorites.length" class="community-list-message"><h3>暂无收藏社团</h3><p>在社团列表点击收藏图标，稍后再回来看看。</p><RouterLink to="/clubs" class="community-text-link">浏览社团</RouterLink></div></template>
      </section>
      <div class="community-profile-menu"><RouterLink v-if="!businessMode || business.user?.role === 'MANAGER'" to="/manage" class="community-help-row"><CommunityIcon name="clubs" /><strong>负责人工作台</strong><CommunityIcon name="chevron" /></RouterLink><RouterLink v-if="businessMode && business.user?.role === 'MANAGER'" to="/manage/activities" class="community-help-row"><CommunityIcon name="calendar" /><strong>管理活动</strong><CommunityIcon name="chevron" /></RouterLink><RouterLink to="/guide" class="community-help-row"><CommunityIcon name="read" /><strong>使用说明</strong><CommunityIcon name="chevron" /></RouterLink></div>
      <p class="community-list-disclosure">{{ businessMode ? '申请、成员关系、报名和消息来自本地数据库，所有账号与资料均为虚构。' : '交互原型 · 虚构数据 · 非真实报名，记录仅保存在本浏览器。' }}</p>
      <button v-if="!businessMode" type="button" class="community-prototype-reset community-text-button" @click="modal = 'reset'">重置本浏览器的演示记录</button>
    </template>
  </div>
  <CommunityProfileEditor v-if="modal === 'profile' && profile" :profile="profile" @close="modal = ''" />
  <CommunityDialog v-if="withdrawing" title="撤回这份申请？" :busy="businessMode && business.saving" @close="withdrawing = ''"><p>{{ businessMode ? '只可撤回待审核申请。撤回记录会保留，可以再次申请。' : '撤回这份模拟申请后，可以重新申请；已通过的申请不可撤回。' }}</p><div class="community-dialog-actions"><button type="button" class="community-button" :disabled="businessMode && business.saving" @click="withdraw">{{ businessMode && business.saving ? '正在保存…' : '确认撤回' }}</button><button type="button" class="community-text-button" :disabled="businessMode && business.saving" @click="withdrawing = ''">暂不撤回</button></div></CommunityDialog>
  <CommunityDialog v-if="modal === 'interests'" title="编辑兴趣偏好" @close="modal = ''"><p>仅用于本浏览器的标签筛选，不代表 AI 推荐，也不修改数据库个人资料。</p><div class="community-interest-picker"><button v-for="tag in interests" :key="tag" type="button" :aria-pressed="selectedInterests.includes(tag)" :class="{ selected: selectedInterests.includes(tag) }" @click="toggleInterest(tag)">{{ tag }}</button></div><div class="community-dialog-actions"><button type="button" class="community-button" @click="saveInterests">保存偏好</button></div></CommunityDialog>
  <CommunityDialog v-if="modal === 'reset'" title="恢复原型的初始数据？" @close="modal = ''"><p>只重置本浏览器的模拟记录，不删除后端数据库资料。</p><div class="community-dialog-actions"><button type="button" class="community-button" @click="resetPrototype(); modal = ''">确认恢复演示数据</button><button type="button" class="community-text-button" @click="modal = ''">取消</button></div></CommunityDialog>
</template>
