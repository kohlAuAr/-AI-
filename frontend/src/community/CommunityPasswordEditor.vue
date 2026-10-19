<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';
import { business, changePassword } from '../prototype/business';
import { errorMessage } from '../api';
import { toast } from '../prototype/store';
import CommunityDialog from './CommunityDialog.vue';
import { NButton } from 'naive-ui';
const emit = defineEmits<{ close: [] }>();
const router = useRouter();
const username = business.user?.username;
const currentPassword = ref(''), newPassword = ref(''), confirmation = ref('');
const busy = ref(false), error = ref('');
const errorElement = ref<HTMLElement>();
async function save() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    if (newPassword.value.length < 8 || new TextEncoder().encode(newPassword.value).length > 72) throw new Error('新密码至少 8 个字符，UTF-8 编码不超过 72 字节');
    if (newPassword.value !== confirmation.value) throw new Error('两次输入的新密码不一致');
    if (currentPassword.value === newPassword.value) throw new Error('新密码不能与当前密码相同');
    await changePassword(currentPassword.value, newPassword.value);
    currentPassword.value = ''; newPassword.value = ''; confirmation.value = '';
    toast('密码已修改，请使用新密码重新登录'); emit('close');
    await router.replace('/login');
  } catch (failure) { error.value = errorMessage(failure); await nextTick(); errorElement.value?.focus(); }
  finally { busy.value = false; }
}
</script>
<template>
  <CommunityDialog title="修改密码" :busy="busy" @close="emit('close')">
    <p class="community-profile-hint">修改成功后，需要在当前设备和其他已登录设备上重新登录。</p>
    <form class="p-form" @submit.prevent="save">
      <p v-if="error" ref="errorElement" tabindex="-1" role="alert" class="p-form-error">{{ error }}</p>
      <fieldset class="community-form-fields" :disabled="busy">
        <label for="password-account">账号<input id="password-account" :value="username" autocomplete="username" readonly /></label>
        <label for="password-current">当前密码<input id="password-current" v-model="currentPassword" type="password" required maxlength="72" autocomplete="current-password" /></label>
        <label for="password-new">新密码<input id="password-new" v-model="newPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password" aria-describedby="password-help" /><small id="password-help">至少 8 个字符，建议使用字母、数字和符号组合。</small></label>
        <label for="password-confirm">确认新密码<input id="password-confirm" v-model="confirmation" type="password" required maxlength="72" autocomplete="new-password" :aria-invalid="confirmation.length > 0 && confirmation !== newPassword" :aria-describedby="confirmation.length > 0 && confirmation !== newPassword ? 'password-mismatch' : undefined" /><small v-if="confirmation.length > 0 && confirmation !== newPassword" id="password-mismatch" class="community-field-error">两次输入的新密码不一致。</small></label>
      </fieldset>
      <small>如提交后连接中断，请重新登录，确认密码是否已更新。</small>
      <div class="community-dialog-actions"><NButton attr-type="submit" type="primary" class="password-action" :loading="busy" :disabled="busy">{{ busy ? '正在修改…' : '修改密码并重新登录' }}</NButton><NButton attr-type="button" class="password-action" :disabled="busy" @click="emit('close')">取消</NButton></div>
    </form>
  </CommunityDialog>
</template>
<style scoped>
.password-action { min-height: 44px; }
</style>
