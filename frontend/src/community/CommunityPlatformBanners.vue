<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';
import { toast } from '../prototype/store';
import { bannerStatus, bannerType, type Banner, type BannerTarget } from './banners';
import CommunityDialog from './CommunityDialog.vue';
const allowed = computed(() => businessMode && business.user?.role === 'PLATFORM_ADMIN');
const rows = ref<Banner[]>([]), targets = ref<BannerTarget[]>([]), loading = ref(false), busy = ref(false), error = ref('');
const showForm = ref(false), editing = ref<Banner>(), selected = ref<Banner>(), formError = ref('');
const form = ref({ title: '', target: '', sortOrder: 10, startsAt: '', endsAt: '' });
const file = ref<File>(), previewUrl = ref('');
const preview = computed(() => previewUrl.value || editing.value?.imageUrl || '');
let generation = 0;
function clearFile() { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; file.value = undefined; }
function closeForm() { clearFile(); showForm.value = false; editing.value = undefined; formError.value = ''; }
async function refresh() {
  const ticket = ++generation; rows.value = []; targets.value = []; error.value = '';
  if (!allowed.value) { loading.value = false; return; }
  loading.value = true;
  try { const result = await Promise.all([request<Banner[]>('/platform/banners'), request<BannerTarget[]>('/platform/banner-targets')]); if (ticket === generation) [rows.value, targets.value] = result; }
  catch (failure) { if (ticket === generation) error.value = errorMessage(failure); }
  finally { if (ticket === generation) loading.value = false; }
}
watch(() => [business.user?.id, business.user?.role], () => { closeForm(); selected.value = undefined; void refresh(); }, { immediate: true });
function open(banner?: Banner) {
  closeForm(); editing.value = banner;
  form.value = { title: banner?.title || '', target: banner ? `${banner.targetType}:${banner.targetId}` : '', sortOrder: banner?.sortOrder ?? 10, startsAt: banner?.startsAt?.slice(0, 16) || '', endsAt: banner?.endsAt?.slice(0, 16) || '' };
  showForm.value = true;
}
function chooseImage(event: Event) {
  clearFile(); formError.value = ''; const input = event.target as HTMLInputElement; const next = input.files?.[0];
  if (!next) return;
  if (!['image/png', 'image/jpeg'].includes(next.type) || next.size > 2 * 1024 * 1024) { formError.value = '请选择不超过 2MB 的 PNG/JPG 图片。'; input.value = ''; return; }
  file.value = next; previewUrl.value = URL.createObjectURL(next);
}
async function save() {
  if (busy.value || !allowed.value) return;
  formError.value = '';
  if (!editing.value && !file.value) { formError.value = '请先选择海报图片。'; return; }
  if (form.value.startsAt && form.value.endsAt && form.value.endsAt <= form.value.startsAt) { formError.value = '展示结束时间必须晚于开始时间。'; return; }
  const [targetType, targetId] = form.value.target.split(':');
  const body = new FormData();
  body.append('metadata', new Blob([JSON.stringify({ title: form.value.title, targetType, targetId: Number(targetId), sortOrder: form.value.sortOrder, startsAt: form.value.startsAt || null, endsAt: form.value.endsAt || null })], { type: 'application/json' }));
  if (file.value) body.append('image', file.value);
  busy.value = true;
  try { await request(`/platform/banners${editing.value ? `/${editing.value.id}` : ''}`, { method: editing.value ? 'PUT' : 'POST', body }); closeForm(); await refresh(); toast(error.value ? '海报已保存，列表读取失败，请刷新' : '海报已保存；新海报需确认上架后才会展示'); }
  catch (failure) { formError.value = errorMessage(failure); }
  finally { busy.value = false; }
}
async function visibility() {
  if (busy.value || !allowed.value || !selected.value) return;
  busy.value = true; formError.value = '';
  try { await request(`/platform/banners/${selected.value.id}/visibility`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !selected.value.enabled }) }); const enabled = !selected.value.enabled; selected.value = undefined; await refresh(); toast(error.value ? '状态已保存，列表读取失败，请刷新' : enabled ? '已上架；按展示时间和关联内容状态显示' : '已下架'); }
  catch (failure) { formError.value = errorMessage(failure); }
  finally { busy.value = false; }
}
function askVisibility(banner: Banner) { formError.value = ''; selected.value = banner; }
onUnmounted(() => { ++generation; clearFile(); });
</script>
<template>
  <div class="community-directory community-platform-page"><div class="community-page-title"><h1>首页内容管理</h1><RouterLink to="/">查看发现页</RouterLink></div><p class="community-page-intro">挑选校园内容，安排首页海报。这里只管理展示，不审批社团活动。</p>
    <div v-if="!businessMode" class="community-list-message"><h2>原型模式没有平台管理服务</h2><p>请使用正常开发模式登录平台管理员账号。</p></div>
    <div v-else-if="!allowed" class="community-list-message"><h2>{{ business.loading ? '正在确认账号权限…' : '请使用平台管理员账号' }}</h2><p>学生和社团负责人不能管理全校首页。平台管理员也不会因此获得社团负责人权限。</p><RouterLink v-if="!business.loading" to="/login" class="community-button">前往登录</RouterLink></div>
    <template v-else>
      <div class="community-banner-toolbar"><span>共 {{ rows.length }} 张海报</span><div><button type="button" class="community-text-button" :disabled="loading || busy" @click="refresh">刷新列表</button><button type="button" class="community-button" :disabled="loading || busy" @click="open()">新增海报</button></div></div>
      <div v-if="error" class="community-list-message" role="alert"><p>{{ error }}</p><button type="button" @click="refresh">重新加载</button></div>
      <form v-if="showForm" class="community-event-form community-banner-form" aria-labelledby="banner-form-heading" :aria-describedby="formError ? 'banner-form-error' : undefined" @submit.prevent="save"><h2 id="banner-form-heading">{{ editing ? '编辑海报' : '新增海报' }}</h2><p>新海报先保存为下架状态。编辑已上架海报会直接更新展示内容。</p><fieldset :disabled="busy"><label for="banner-title">海报标题<input id="banner-title" v-model="form.title" required maxlength="80" /></label><label for="banner-target">关联内容<select id="banner-target" v-model="form.target" required><option value="" disabled>请选择社团、招新或已发布活动</option><optgroup v-for="(label, type) in bannerType" :key="type" :label="label"><option v-for="target in targets.filter(t => t.type === type)" :key="target.id" :value="`${target.type}:${target.id}`">{{ target.title }}</option></optgroup></select></label><p class="community-section-description">只展示开放招新和尚未开始的已发布活动；活动取消或招新关闭后，对应海报自动停止展示。</p><label for="banner-image">海报图片{{ editing ? '（不选择则保留原图）' : '（必选）' }}<input id="banner-image" type="file" accept="image/png,image/jpeg" :required="!editing" aria-describedby="banner-image-help" @change="chooseImage" /></label><p id="banner-image-help" class="community-section-description">PNG/JPG，不超过 2MB，宽高各不超过 4096 像素。建议横图 1200 × 500；标题会覆盖在图片左侧。</p><img v-if="preview" :src="preview" alt="待保存的海报图片预览" class="community-banner-preview" /><div class="community-form-columns"><label for="banner-start">展示开始（可留空）<input id="banner-start" v-model="form.startsAt" type="datetime-local" /></label><label for="banner-end">展示结束（可留空）<input id="banner-end" v-model="form.endsAt" type="datetime-local" /></label></div><label for="banner-order">展示顺序（数字越小越靠前）<input id="banner-order" v-model.number="form.sortOrder" type="number" min="0" max="999" required /></label><p v-if="formError" id="banner-form-error" class="community-error" role="alert">{{ formError }}</p><div class="community-dialog-actions"><button class="community-button" :disabled="busy">{{ busy ? '正在保存…' : '保存海报' }}</button><button type="button" class="community-text-button" :disabled="busy" @click="closeForm">取消编辑</button></div></fieldset></form>
      <p v-if="loading" class="community-list-loading" role="status">正在读取首页海报…</p>
      <template v-else-if="!error"><article v-for="banner in rows" :key="banner.id" class="community-banner-row"><img :src="banner.imageUrl" :alt="`${banner.title}海报预览`" width="1200" height="500" loading="lazy" /><div class="community-banner-row-copy"><div class="community-record-heading"><h2>{{ banner.title }}</h2><span class="community-record-status" :class="{ muted: banner.displayStatus !== 'VISIBLE' }">{{ bannerStatus[banner.displayStatus] }}</span></div><p>{{ bannerType[banner.targetType] }}：{{ banner.targetTitle }}</p><p>顺序 {{ banner.sortOrder }} · {{ banner.startsAt?.replace('T', ' ').slice(0, 16) || '立即开始' }} 至 {{ banner.endsAt?.replace('T', ' ').slice(0, 16) || '不限结束时间' }}</p><div class="community-dialog-actions"><button type="button" class="community-text-button" :disabled="busy || loading" @click="open(banner)">编辑</button><button type="button" class="community-text-button" :disabled="busy || loading" @click="askVisibility(banner)">{{ banner.enabled ? '下架' : '上架' }}</button></div></div></article><div v-if="!rows.length" class="community-list-message"><h2>还没有首页海报</h2><p>新增海报并确认上架后，学生就能在发现页看到。</p></div></template>
      <p class="community-list-disclosure">海报和展示设置保存在校园数据库。示例海报为原创插画与虚构资料；没有海报申请审核、多级审批或外部链接。</p>
    </template>
    <CommunityDialog v-if="selected && allowed" :title="selected.enabled ? '确认下架海报' : '确认上架海报'" :busy="busy" @close="selected = undefined"><h3>{{ selected.title }}</h3><p>{{ selected.enabled ? '下架后，不再向学生展示，海报记录仍会保留。' : '确认图片、标题和关联内容无误。上架后按展示时间进入全校发现页。' }}</p><p v-if="formError" class="community-error" role="alert">{{ formError }}</p><div class="community-dialog-actions"><button type="button" class="community-button" :disabled="busy" @click="visibility">{{ busy ? '正在保存…' : selected.enabled ? '确认下架' : '确认上架' }}</button><button type="button" class="community-text-button" :disabled="busy" @click="selected = undefined">返回</button></div></CommunityDialog>
  </div>
</template>
