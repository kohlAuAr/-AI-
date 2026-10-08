<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { request, errorMessage } from '../api';
import { businessMode } from '../prototype/business';
import { bannerType, type Banner } from './banners';
const banners = ref<Banner[]>([]), error = ref(''), loading = ref(false), index = ref(0);
const paused = ref(false), hover = ref(false), focusWithin = ref(false), reduced = ref(false), clock = ref(Date.now());
const failed = ref<number[]>([]);
const available = computed(() => banners.value.filter(b => !failed.value.includes(b.id) && (!b.endsAt || new Date(b.endsAt).getTime() > clock.value)));
const active = computed(() => available.value[index.value % available.value.length]);
let timer: ReturnType<typeof setInterval> | undefined, motion: MediaQueryList | undefined, touchX = 0;
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
function visibilityChanged() { if (!document.hidden) { clock.value = Date.now(); void refresh(); } }
function swipe(event: TouchEvent) { const distance = event.changedTouches[0].clientX - touchX; if (Math.abs(distance) > 50) { event.preventDefault(); manual(distance > 0 ? -1 : 1); } }
onMounted(() => {
  if (!businessMode) return;
  void refresh(); motion = window.matchMedia('(prefers-reduced-motion: reduce)'); motionChanged();
  motion.addEventListener('change', motionChanged); document.addEventListener('visibilitychange', visibilityChanged);
  timer = setInterval(() => { clock.value = Date.now(); if (!paused.value && !hover.value && !focusWithin.value && !reduced.value && !document.hidden) step(1); }, 6000);
});
onUnmounted(() => { clearInterval(timer); motion?.removeEventListener('change', motionChanged); document.removeEventListener('visibilitychange', visibilityChanged); });
</script>
<template>
  <section v-if="businessMode && (active || error || loading)" class="community-banner-section" aria-label="校园精选海报" aria-roledescription="轮播" @mouseenter="hover = true" @mouseleave="hover = false" @focusin="focusWithin = true" @focusout="focusLeft" @keydown.left.prevent="manual(-1)" @keydown.right.prevent="manual(1)">
    <div v-if="error" class="community-list-message" role="alert"><p>精选海报暂时无法读取：{{ error }}</p><button type="button" @click="refresh">重新加载海报</button></div>
    <p v-else-if="loading && !active" class="community-list-loading" role="status">正在加载校园精选…</p>
    <template v-else-if="active">
      <RouterLink :key="active.id" :to="active.targetPath" class="community-banner-slide" @touchstart="touchX = $event.touches[0].clientX" @touchend="swipe"><img :src="active.imageUrl" alt="" width="1200" height="500" decoding="async" @error="failed.push(active.id)" /><div class="community-banner-caption"><span>{{ bannerType[active.targetType] }}</span><h2>{{ active.title }}</h2><p>{{ active.targetTitle }}</p></div><span class="community-banner-action">查看详情</span></RouterLink>
      <div v-if="available.length > 1" class="community-banner-controls"><button type="button" aria-label="上一张海报" @click="manual(-1)">‹</button><div class="community-banner-dots"><button v-for="(banner, position) in available" :key="banner.id" type="button" :aria-label="`第 ${position + 1} 张：${banner.title}`" :aria-pressed="active.id === banner.id" @click="paused = true; index = position"><span></span></button></div><span class="community-banner-position" :aria-live="paused || reduced ? 'polite' : 'off'">{{ available.indexOf(active) + 1 }} / {{ available.length }}</span><button type="button" aria-label="下一张海报" @click="manual(1)">›</button><button v-if="!reduced" type="button" class="community-banner-play" :aria-label="paused ? '播放海报轮播' : '暂停海报轮播'" @click="togglePlayback">{{ paused ? '播放' : '暂停' }}</button></div>
    </template>
  </section>
</template>
