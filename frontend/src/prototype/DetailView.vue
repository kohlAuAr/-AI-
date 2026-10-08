<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { clubs } from './data';
import { state, apply, register, cancelRegistration, toggleFavorite, dateLabel, statusLabels } from './store';
import Icon from './Icon.vue';
import Modal from './Modal.vue';
const route = useRoute();
const activityMode = computed(() => route.path.startsWith('/activities/'));
const activity = computed(() => state.activities.find(a => a.id === route.params.id && a.status === 'published'));
const club = computed(() => clubs.find(c => c.id === (activityMode.value ? activity.value?.clubId : route.params.id)));
const application = computed(() => state.applications.find(a => a.mine && a.clubId === club.value?.id && ['pending', 'approved'].includes(a.status)));
const related = computed(() => state.activities.filter(a => a.clubId === club.value?.id && a.status === 'published'));
const registered = computed(() => !!activity.value && state.registrations.includes(activity.value.id));
const started = computed(() => !!activity.value && new Date(`${activity.value.date}T${activity.value.time}`).getTime() <= Date.now());
const modal = ref(''); const reason = ref(''); const consent = ref(false);
function submitApplication() { if (club.value && reason.value.trim() && consent.value && apply(club.value.id, reason.value)) modal.value = ''; }
function confirmRegistration() { if (activity.value && register(activity.value.id)) modal.value = ''; }
function confirmCancellation() { if (activity.value) cancelRegistration(activity.value.id); modal.value = ''; }
</script>
<template>
  <RouterLink :to="activityMode ? '/activities' : '/clubs'" class="p-back">← 返回{{ activityMode ? '活动' : '社团' }}列表</RouterLink>
  <template v-if="club && (!activityMode || activity)">
    <section class="p-detail-hero" :class="club.color"><div><span class="p-chip white">{{ club.category }} · {{ activityMode ? '活动详情' : '社团详情' }}</span><h1>{{ activityMode ? activity?.title : club.name }}</h1><p>{{ activityMode ? club.name + ' · 欢迎一起参与' : club.slogan }}</p></div><strong aria-hidden="true">{{ club.mark }}</strong></section>
    <div class="p-detail-grid"><div>
      <section class="p-panel"><p class="p-kicker">{{ activityMode ? 'ABOUT THE EVENT' : 'ABOUT THE CLUB' }}</p><h2>{{ activityMode ? '这次，我们一起做什么？' : '我们是一群怎样的人？' }}</h2><p>{{ activityMode ? activity?.description : club.description }}</p><div class="p-tags"><span v-for="tag in club.tags" :key="tag">{{ tag }}</span></div></section>
      <section v-if="activityMode && activity" class="p-panel"><h2>活动安排</h2><ol class="p-agenda"><li v-for="(step, i) in activity.agenda" :key="step"><span>{{ String(i + 1).padStart(2, '0') }}</span>{{ step }}</li></ol><p class="p-small">请提前 10 分钟到场。以上为原型中的虚构活动安排。</p></section>
      <template v-else><section class="p-panel"><h2>招新说明</h2><div class="p-info-line"><span>当前状态</span><span class="p-chip" :class="club.recruiting ? 'green' : 'gray'">{{ club.recruiting ? '开放申请' : '本轮已结束' }}</span></div><div class="p-info-line"><span>面向同学</span><strong>全校学生 · 不限专业</strong></div><div class="p-info-line"><span>加入条件</span><p>{{ club.requirements }}</p></div><div class="p-info-line"><span>申请方式</span><p>填写兴趣与加入理由，由社团负责人审核。</p></div></section><section class="p-panel"><div class="p-section-head"><h2>社团近期活动</h2><RouterLink to="/activities" class="p-text-button">更多 ↗</RouterLink></div><RouterLink v-for="item in related" :key="item.id" :to="`/activities/${item.id}`" class="p-list-item"><div><h3>{{ item.title }}</h3><p>{{ dateLabel(item.date) }} · {{ item.location }}</p></div><Icon name="arrow" /></RouterLink><p v-if="!related.length" class="p-empty-text">暂未安排活动，可以先关注社团。</p></section></template>
    </div><aside class="p-detail-side"><section class="p-panel"><h2>{{ activityMode ? '参与这场活动' : '在这里开始新故事' }}</h2><template v-if="activityMode && activity"><p class="p-meta"><Icon name="calendar" />{{ dateLabel(activity.date) }} {{ activity.time }}</p><p class="p-meta"><Icon name="pin" />{{ activity.location }}</p><p class="p-meta"><Icon name="clubs" />{{ activity.enrolled }} / {{ activity.capacity }} 人</p><div class="p-progress"><span :style="{ width: Math.min(100, activity.enrolled / activity.capacity * 100) + '%' }"></span></div><button v-if="registered" class="p-button secondary full" @click="modal = 'cancel'">已报名 · 取消报名</button><button v-else class="p-button full" :disabled="started || activity.enrolled >= activity.capacity" @click="modal = 'signup'">{{ started ? '活动已开始' : activity.enrolled >= activity.capacity ? '名额已满' : '报名参加' }}<Icon name="arrow" /></button></template><template v-else><p class="p-meta"><Icon name="clubs" />{{ club.members }} 位伙伴 · 示例人数</p><p class="p-meta"><Icon name="clock" />{{ club.schedule }}</p><p class="p-meta"><Icon name="pin" />{{ club.place }}</p><button class="p-button full" :disabled="!club.recruiting || !!application" @click="modal = 'apply'; consent = false">{{ application ? statusLabels[application.status] : club.recruiting ? '申请加入社团' : '招新已结束' }}<Icon name="arrow" /></button><button class="p-button secondary full" @click="toggleFavorite(club.id)"><Icon name="heart" />{{ state.favorites.includes(club.id) ? '已收藏 · 取消收藏' : '先收藏，再了解' }}</button></template><p class="p-small p-top-gap">仅演示交互，不会提交到学校或社团。</p></section><section class="p-side-note"><Icon name="book" /><h3>还有一点疑问？</h3><p>先查看招新条件与活动说明，资料助手也可以作为辅助入口。</p><RouterLink to="/assistant" class="p-text-button">查看帮助 ↗</RouterLink></section></aside></div>
  </template>
  <div v-else class="p-empty"><h2>没有找到这条内容</h2><p>活动可能尚未发布，或链接有误。</p></div>
  <Modal v-if="modal === 'apply' && club" :title="`申请加入${club.name}`" @close="modal = ''"><form class="p-form" @submit.prevent="submitApplication"><div class="p-form-summary"><strong>{{ state.profile.name }}</strong><span>{{ state.profile.major }}</span></div><label>为什么想加入？<textarea v-model="reason" required maxlength="500" rows="4" placeholder="说说你的兴趣、想学习的内容，或你愿意参与的事情。"></textarea></label><label class="p-checkbox"><input v-model="consent" type="checkbox" required />我已了解招新条件，并知晓这是模拟申请。</label><button class="p-button full" :disabled="!reason.trim() || !consent">提交模拟申请<Icon name="arrow" /></button></form></Modal>
  <Modal v-if="modal === 'signup' && activity" title="确认活动报名" @close="modal = ''"><p>{{ activity.title }}</p><p>{{ dateLabel(activity.date) }} {{ activity.time }} · {{ activity.location }}</p><p class="p-small">以{{ state.profile.name }}的模拟身份报名。不会向真实学校发送数据。</p><button class="p-button full" @click="confirmRegistration">确认模拟报名<Icon name="check" /></button></Modal>
  <Modal v-if="modal === 'cancel'" title="取消这次模拟报名？" @close="modal = ''"><p>取消后将释放原型中的一个名额，你仍可在名额充足时重新报名。</p><div class="p-actions"><button class="p-button secondary" @click="modal = ''">保留报名</button><button class="p-button" @click="confirmCancellation">确认取消</button></div></Modal>
</template>
