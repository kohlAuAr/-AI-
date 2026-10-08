<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';
import { saveProfile, type Profile } from '../prototype/business';
import { errorMessage } from '../api';
import { toast } from '../prototype/store';
import CommunityDialog from './CommunityDialog.vue';
const props = defineProps<{ profile: Profile }>();
const emit = defineEmits<{ close: [] }>();
const form = reactive({ name: props.profile.name, major: props.profile.major, interests: [...props.profile.interests], availableTime: props.profile.availableTime, interestDescription: props.profile.interestDescription || '' });
const tags = [...new Set(['摄影', '编程', '户外', '音乐', '艺术', '公益', '运动', '阅读', '科技', '表达', ...form.interests])];
const busy = ref(false), error = ref('');
const errorElement = ref<HTMLElement>();
function toggle(tag: string) {
  const index = form.interests.indexOf(tag);
  if (index >= 0) form.interests.splice(index, 1);
  else if (form.interests.length < 12) form.interests.push(tag);
}
async function save() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    if (!form.name.trim() || !form.major.trim()) throw new Error('姓名和专业不能只填写空格');
    await saveProfile({ ...form, name: form.name.trim(), major: form.major.trim(), availableTime: form.availableTime.trim(), interestDescription: form.interestDescription.trim() });
    toast('个人资料已保存'); emit('close');
  } catch (failure) { error.value = errorMessage(failure); await nextTick(); errorElement.value?.focus(); }
  finally { busy.value = false; }
}
</script>
<template>
  <CommunityDialog title="编辑个人资料" :busy="busy" @close="emit('close')">
    <p class="community-profile-hint">用自己的话描述兴趣，助手会据此匹配社团。资料保存不依赖 AI 服务。</p>
    <form class="p-form" @submit.prevent="save">
      <p v-if="error" ref="errorElement" tabindex="-1" role="alert" class="p-form-error">{{ error }}</p>
      <fieldset class="community-form-fields" :disabled="busy">
        <label for="profile-username">账号<input id="profile-username" :value="profile.username" readonly autocomplete="username" /></label>
        <label for="profile-name">姓名<input id="profile-name" v-model="form.name" required maxlength="40" autocomplete="name" /></label>
        <label for="profile-major">专业<input id="profile-major" v-model="form.major" required maxlength="80" /></label>
        <label for="profile-interest-description">兴趣描述（可选）<textarea id="profile-interest-description" v-model="form.interestDescription" rows="5" maxlength="1000" placeholder="例如：喜欢用手机记录校园生活，想学拍照和剪视频，零基础，希望社团氛围轻松一点。" aria-describedby="profile-interest-help" /><small id="profile-interest-help">写下喜欢的事情、想学的技能和参与偏好，最多 1000 字。保存后到助手获取推荐；模型启用时，这段文字会发送到学校配置的 Embedding 服务。</small></label>
        <div><p id="profile-interests-label">辅助标签（可选）</p><div class="community-interest-picker" role="group" aria-labelledby="profile-interests-label"><button v-for="tag in tags" :key="tag" type="button" :aria-pressed="form.interests.includes(tag)" :class="{ selected: form.interests.includes(tag) }" :disabled="busy || (form.interests.length >= 12 && !form.interests.includes(tag))" @click="toggle(tag)">{{ tag }}</button></div></div>
        <label for="profile-time">空闲时间（可选）<textarea id="profile-time" v-model="form.availableTime" rows="3" maxlength="200" placeholder="例如：周三晚上、周末下午" aria-describedby="profile-time-help" /><small id="profile-time-help">文字备注，暂不自动检测课表冲突。</small></label>
      </fieldset>
      <div class="community-dialog-actions"><button class="community-button" :disabled="busy">{{ busy ? '正在保存…' : '保存资料' }}</button><button type="button" class="community-text-button" :disabled="busy" @click="emit('close')">取消</button></div>
    </form>
  </CommunityDialog>
</template>
