<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { business, managedClubs, refreshBusiness } from '../prototype/business';
import { clubFields, clubInput, validateClub, currentClubSettings, clubSettingsState, loadClubSettings, saveClubSettings, type ClubInput } from './clubSettings';
import CommunityDialog from './CommunityDialog.vue';
const form = ref<ClubInput>(), pending = ref<ClubInput>(), reloadConfirm = ref(false);
const errors = ref<ReturnType<typeof validateClub>>({}), errorElement = ref<HTMLElement>();
const selectedClub = computed(() => managedClubs.value.find(club => club.backendId === business.managedClubId));
const dirty = computed(() => !!form.value && !!currentClubSettings.value && JSON.stringify(clubInput(form.value)) !== JSON.stringify(clubInput(currentClubSettings.value)));
watch(currentClubSettings, club => { form.value = club ? clubInput(club) : undefined; errors.value = {}; pending.value = undefined; reloadConfirm.value = false; }, { flush: 'sync', immediate: true });
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], () => { form.value = undefined; errors.value = {}; pending.value = undefined; reloadConfirm.value = false; }, { flush: 'sync' });
watch([() => business.user?.id, () => business.user?.role, () => business.managedClubId], loadClubSettings);
onMounted(loadClubSettings);
async function focusError() { await nextTick(); errorElement.value?.focus(); }
async function save() {
  if (!form.value || clubSettingsState.saving) return;
  errors.value = validateClub(form.value); clubSettingsState.writeError = '';
  if (Object.keys(errors.value).length) { await focusError(); return; }
  const input = clubInput(form.value);
  if (currentClubSettings.value?.recruiting && !input.recruiting) pending.value = input;
  else if (!await saveClubSettings(input)) await focusError();
}
async function confirmSave() {
  if (!pending.value) return;
  if (await saveClubSettings(pending.value)) pending.value = undefined;
  else { pending.value = undefined; await focusError(); }
}
function reload() { if (dirty.value) reloadConfirm.value = true; else loadClubSettings(); }
</script>
<template><div class="community-club-settings">
  <div class="community-page-title"><h1>社团设置</h1><RouterLink to="/manage">返回工作台</RouterLink></div>
  <div v-if="business.user?.role !== 'MANAGER'" class="community-list-message"><h2>请使用社团负责人账号</h2><p>只能修改你负责社团的资料与招新设置。</p><RouterLink to="/login" class="community-button">前往登录</RouterLink></div>
  <template v-else>
    <div class="community-manager-toolbar"><label>管理社团<select v-model="business.managedClubId" :disabled="clubSettingsState.saving || !!pending || business.loading" @change="refreshBusiness"><option v-for="club in managedClubs" :key="club.id" :value="club.backendId">{{ club.name }}</option></select></label><button type="button" class="community-text-button" :disabled="clubSettingsState.loading || clubSettingsState.saving || !selectedClub" @click="reload">重新读取资料</button></div>
    <p v-if="!selectedClub && !business.loading" class="community-list-message">当前账号尚未分配管理社团。</p>
    <p v-if="clubSettingsState.loading" role="status" class="community-list-loading">正在读取社团资料…</p>
    <p v-if="clubSettingsState.error" class="community-error" role="alert">{{ clubSettingsState.error }} 请点击“重新读取资料”重试。</p>
    <p v-if="clubSettingsState.notice" role="status" class="club-settings-notice">{{ clubSettingsState.notice }}</p>
    <form v-if="form && currentClubSettings" class="club-settings-form" @submit.prevent="save">
      <p>当前已保存状态：{{ currentClubSettings.recruiting ? '招新中' : '招新已关闭' }}。修改后点击“保存资料与招新设置”才会生效。</p>
      <div v-if="Object.keys(errors).length || clubSettingsState.writeError" ref="errorElement" class="community-error" tabindex="-1" role="alert"><p v-if="clubSettingsState.writeError">{{ clubSettingsState.writeError }}</p><p v-else>请检查以下内容：</p><ul v-if="Object.keys(errors).length"><li v-for="(message, key) in errors" :key="key"><a :href="`#club-setting-${key}`">{{ message }}</a></li></ul></div>
      <fieldset v-for="group in [{ id: 'profile', title: '社团资料' }, { id: 'recruitment', title: '招新设置' }]" :key="group.id" :disabled="clubSettingsState.saving || !!pending"><legend>{{ group.title }}</legend>
        <label v-if="group.id === 'recruitment'" class="club-settings-checkbox"><input v-model="form.recruiting" type="checkbox" aria-describedby="club-recruiting-help" />开放招新</label>
        <p v-if="group.id === 'recruitment'" id="club-recruiting-help">关闭后停止接收新申请，已有申请与成员保留，负责人仍可审核已有申请；开启后恢复接收新申请。社团活动报名不受此开关影响。</p>
        <div class="club-settings-fields"><label v-for="field in clubFields.filter(item => item.group === group.id)" :key="field.key" :class="{ wide: field.multiline }" :for="`club-setting-${field.key}`">{{ field.label }}
          <textarea v-if="field.multiline" :id="`club-setting-${field.key}`" v-model="form[field.key]" :rows="field.key === 'description' ? 5 : 3" :maxlength="field.max" :required="!field.optional" :aria-invalid="!!errors[field.key]" :aria-describedby="errors[field.key] ? `club-error-${field.key}` : undefined" />
          <input v-else :id="`club-setting-${field.key}`" v-model="form[field.key]" :maxlength="field.max" :required="!field.optional" :aria-invalid="!!errors[field.key]" :aria-describedby="errors[field.key] ? `club-error-${field.key}` : undefined" />
          <small v-if="errors[field.key]" :id="`club-error-${field.key}`" class="community-error">{{ errors[field.key] }}</small>
        </label></div>
      </fieldset>
      <p class="club-settings-help">社团名称、类别、介绍与兴趣标签用于现有兴趣匹配。保存资料不调用 AI；时间和招新条件是文字说明，不自动审核学生资格或检测课表冲突。</p>
      <div class="community-dialog-actions"><button type="submit" class="community-button" :disabled="clubSettingsState.saving || !!pending">{{ clubSettingsState.saving ? '正在保存…' : '保存资料与招新设置' }}</button><RouterLink :to="`/clubs/${currentClubSettings.slug || currentClubSettings.id}`" class="community-text-link">查看学生端社团页</RouterLink></div>
    </form>
  </template>
  <CommunityDialog v-if="pending" title="确认关闭招新并保存" :busy="clubSettingsState.saving" @close="pending = undefined"><p>本次填写的社团资料会一并保存。关闭招新后不再接收新申请，已有申请与成员记录保留。确定保存？</p><div class="community-dialog-actions"><button type="button" class="community-button secondary" :disabled="clubSettingsState.saving" @click="pending = undefined">返回修改</button><button type="button" class="community-button" :disabled="clubSettingsState.saving" @click="confirmSave">{{ clubSettingsState.saving ? '正在保存…' : '确认关闭并保存' }}</button></div></CommunityDialog>
  <CommunityDialog v-if="reloadConfirm" title="重新读取社团资料？" @close="reloadConfirm = false"><p>重新读取会放弃本页未保存的修改，不会修改数据库。</p><div class="community-dialog-actions"><button type="button" class="community-button secondary" @click="reloadConfirm = false">继续编辑</button><button type="button" class="community-button" @click="reloadConfirm = false; loadClubSettings()">放弃修改并重新读取</button></div></CommunityDialog>
</div></template>
<style scoped>
.community-club-settings { color: var(--community-text); overflow-wrap: anywhere; }
.club-settings-form > p, .club-settings-help, fieldset > p { font-size: 14px; line-height: 1.8; color: var(--community-muted); margin: 12px 0; }
.club-settings-form fieldset { border: 0; border-top: 1px solid var(--community-line); margin: 24px 0; padding: 20px 0 0; min-width: 0; }
.club-settings-form legend { padding: 0 12px 0 0; font-size: 18px; font-weight: 600; }
.club-settings-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px 16px; }
.club-settings-fields label { display: grid; gap: 8px; font-size: 15px; }
.club-settings-fields .wide { grid-column: 1 / -1; }
.club-settings-fields :is(input, textarea) { width: 100%; min-width: 0; min-height: 44px; padding: 10px 12px; border: 1px solid var(--community-line); border-radius: 4px; font: inherit; font-size: 16px; color: var(--community-text); background: white; }
.club-settings-fields textarea { resize: vertical; line-height: 1.7; }
.club-settings-checkbox { display: flex; gap: 12px; align-items: center; min-height: 44px; width: fit-content; font-size: 16px; cursor: pointer; }
.club-settings-checkbox input { width: 20px; height: 20px; accent-color: var(--community-green); }
.club-settings-notice { color: var(--community-green); margin: 16px 0; line-height: 1.8; }
@media (max-width: 480px) { .club-settings-fields { grid-template-columns: 1fr; } }
</style>
