<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { request, errorMessage } from '../api';
import { businessMode } from '../prototype/business';
import { bannerType, type Banner } from './banners';
import CommunityIcon from './CommunityIcon.vue';
const banners = ref<Banner[]>([]), error = ref(''), loading = ref(false), index = ref(0);
const paused = ref(false), hover = ref(false), focusWithin = ref(false), reduced = ref(false), clock = ref(Date.now());
const hidden = ref(false);
const failed = ref<number[]>([]);
const available = computed(() => banners.value.filter(b => !failed.value.includes(b.id) && (!b.endsAt || new Date(b.endsAt).getTime() > clock.value)));
const active = computed(() => available.value[index.value % available.value.length]);
const playing = computed(() => available.value.length > 1 && !paused.value && !hover.value && !focusWithin.value && !reduced.value && !hidden.value);
const dots = computed(() => {
  const current = available.value.indexOf(active.value);
  const start = Math.max(0, Math.min(current - 2, available.value.length - 5));
  return available.value.slice(start, start + 5).map((banner, offset) => ({ banner, position: start + offset }));
});
const action = computed(() => active.value?.targetType === 'ACTIVITY' ? '查看活动' : active.value?.targetType === 'RECRUITMENT' ? '了解招新' : '走进社团');
let timer: ReturnType<typeof setInterval> | undefined, rotation: ReturnType<typeof setTimeout> | undefined, motion: MediaQueryList | undefined;
let touchX = 0, touchY = 0, swiped = false;
watch([playing, () => active.value?.id], () => {
  clearTimeout(rotation);
  if (playing.value) rotation = setTimeout(() => step(1), 6000);
});
async function refresh() {
  if (!businessMode) return;
  loading.value = true; error.value = '';
  try { banners.value = await request<Banner[]>('/banners'); failed.value = []; index.value = 0; }
  catch (failure) { banners.value = []; error.value = errorMessage(failure); }
  finally { loading.value = false; }
}
function step(delta: number) { if (available.value.length) index.value = (index.value + delta + available.value.length) % available.value.length; }
function manual(delta: number) { paused.value = true; step(delta); }
function focusLeft(event: FocusEvent) { focusWithin.value = (event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null); }
function togglePlayback() { paused.value = !paused.value; if (!paused.value) focusWithin.value = false; }
function motionChanged() { reduced.value = !!motion?.matches; }
function visibilityChanged() { hidden.value = document.hidden; if (!document.hidden) { clock.value = Date.now(); void refresh(); } }
function touchStarted(event: TouchEvent) { touchX = event.touches[0].clientX; touchY = event.touches[0].clientY; swiped = false; }
function swipe(event: TouchEvent) {
  const distance = event.changedTouches[0].clientX - touchX, vertical = event.changedTouches[0].clientY - touchY;
  if (Math.abs(distance) > 50 && Math.abs(distance) > Math.abs(vertical)) {
    swiped = true; if (event.cancelable) event.preventDefault(); manual(distance > 0 ? -1 : 1);
  }
}
function ignoreSwipeClick(event: MouseEvent) { if (swiped) { event.preventDefault(); swiped = false; } }
function failedImage(event: Event) { failed.value.push(Number((event.target as HTMLImageElement).dataset.bannerId)); }
onMounted(() => {
  if (!businessMode) return;
  void refresh(); hidden.value = document.hidden; motion = window.matchMedia('(prefers-reduced-motion: reduce)'); motionChanged();
  motion.addEventListener('change', motionChanged); document.addEventListener('visibilitychange', visibilityChanged);
  timer = setInterval(() => { clock.value = Date.now(); }, 1000);
});
onUnmounted(() => { clearInterval(timer); clearTimeout(rotation); motion?.removeEventListener('change', motionChanged); document.removeEventListener('visibilitychange', visibilityChanged); });
</script>
<template>
  <section v-if="businessMode && (active || error || loading)" class="community-banner-section" aria-label="校园精选海报" aria-roledescription="轮播" @mouseenter="hover = true" @mouseleave="hover = false" @focusin="focusWithin = true" @focusout="focusLeft" @keydown.left.prevent="manual(-1)" @keydown.right.prevent="manual(1)">
    <div v-if="error" class="community-list-message" role="alert"><p>精选海报暂时无法读取：{{ error }}</p><button type="button" @click="refresh">重新加载海报</button></div>
    <p v-else-if="loading && !active" class="community-list-loading" role="status">正在加载校园精选…</p>
    <div v-else-if="active" class="community-banner-stage" :class="{ 'is-playing': playing }">
      <Transition name="community-banner-fade" mode="out-in">
        <RouterLink :key="active.id" :to="active.targetPath" class="community-banner-slide" @touchstart="touchStarted" @touchend="swipe" @click.capture="ignoreSwipeClick">
          <img :src="active.imageUrl" :data-banner-id="active.id" alt="" width="1536" height="864" decoding="async" fetchpriority="high" @error="failedImage" />
          <div class="community-banner-caption"><div class="community-banner-label"><span>校园精选</span><span>{{ bannerType[active.targetType] }}</span></div><h2>{{ active.title }}</h2><p>{{ active.targetTitle }}</p><span class="community-banner-action">{{ action }}<CommunityIcon name="chevron" /></span></div>
        </RouterLink>
      </Transition>
      <div v-if="available.length > 1" class="community-banner-controls">
        <div class="community-banner-pagination"><span class="community-banner-position" :aria-live="paused || reduced ? 'polite' : 'off'"><strong>{{ available.indexOf(active) + 1 }}</strong> / {{ available.length }}</span><div class="community-banner-dots"><button v-for="{ banner, position } in dots" :key="banner.id" type="button" :aria-label="`第 ${position + 1} 张：${banner.title}`" :aria-pressed="active.id === banner.id" @click="paused = true; index = position"><span :key="active.id"><i /></span></button></div></div>
        <div class="community-banner-navigation"><button type="button" class="community-banner-previous" aria-label="上一张海报" @click="manual(-1)"><CommunityIcon name="chevron" /></button><button type="button" aria-label="下一张海报" @click="manual(1)"><CommunityIcon name="chevron" /></button><button v-if="!reduced" type="button" class="community-banner-play" :aria-label="paused ? '播放海报轮播' : '暂停海报轮播'" @click="togglePlayback"><svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path v-if="paused" d="m8 5 11 7-11 7Z" fill="currentColor" /><path v-else d="M8 5v14M16 5v14" fill="none" stroke="currentColor" stroke-width="3" /></svg><span>{{ paused ? '播放' : '暂停' }}</span></button></div>
      </div>
    </div>
  </section>
</template>
