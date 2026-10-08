<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';
import { business, businessMode, login, register } from './business';
import { errorMessage } from '../api';
const router = useRouter();
const username = ref(''); const password = ref(''); const busy = ref(false); const error = ref('');
const registering = ref(false), name = ref(''), major = ref(''), confirmation = ref(''), success = ref('');
const errorElement = ref<HTMLElement>();
function switchMode(value: boolean) { registering.value = value; password.value = ''; confirmation.value = ''; error.value = ''; success.value = ''; }
async function submit() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try {
    if (registering.value) {
      if (!name.value.trim() || !major.value.trim()) throw new Error('姓名和专业不能只填写空格');
      if (password.value !== confirmation.value) throw new Error('两次输入的密码不一致，请重新确认');
      if (new TextEncoder().encode(password.value).length > 72) throw new Error('密码过长，请缩短至 UTF-8 编码 72 字节以内');
      await register({ username: username.value.trim(), password: password.value, name: name.value.trim(), major: major.value.trim() });
      registering.value = false; password.value = ''; confirmation.value = '';
      success.value = '注册成功，请使用刚才的账号和密码登录。';
      await nextTick(); document.getElementById('login-password')?.focus();
      return;
    }
    await login(username.value.trim(), password.value);
    password.value = '';
    await router.push(business.user?.role === 'PLATFORM_ADMIN' ? '/platform/banners' : business.user?.role === 'MANAGER' ? '/manage/recruitment' : '/me');
  } catch (failure) { error.value = errorMessage(failure); await nextTick(); errorElement.value?.focus(); }
  finally { busy.value = false; }
}
</script>
<template>
  <div class="community-login-page">
    <div class="p-page-heading"><h1>{{ registering ? '注册学生账号' : '账号登录' }}</h1><p>登录后查看你的社团、申请和活动记录。</p></div>
    <section v-if="businessMode" class="p-panel">
      <div class="community-category-tabs community-account-tabs" role="group" aria-label="登录或注册"><button type="button" :aria-pressed="!registering" :class="{ active: !registering }" :disabled="busy" @click="switchMode(false)">登录</button><button type="button" :aria-pressed="registering" :class="{ active: registering }" :disabled="busy" @click="switchMode(true)">注册学生账号</button></div>
      <p class="community-login-intro">{{ registering ? '注册后为学生账号，不能自行获得社团或平台管理权限。' : '学生、社团负责人和平台管理员使用同一个入口，管理权限由账号确定。' }}</p>
      <p v-if="success" role="status" class="success">{{ success }}</p>
      <form class="p-form" @submit.prevent="submit">
        <p v-if="error" id="login-error" ref="errorElement" tabindex="-1" role="alert" class="p-form-error">{{ error }}</p>
        <fieldset class="community-form-fields" :disabled="busy">
          <label for="login-username">账号<input id="login-username" v-model="username" required :maxlength="registering ? 32 : 64" :minlength="registering ? 3 : undefined" :pattern="registering ? '[a-z0-9_]{3,32}' : undefined" autocomplete="username" autocapitalize="none" :spellcheck="false" :aria-describedby="registering ? 'register-username-help' : undefined" /><small v-if="registering" id="register-username-help">3–32 位小写字母、数字或下划线。</small></label>
          <label v-if="registering" for="register-name">姓名<input id="register-name" v-model="name" required maxlength="40" autocomplete="name" /></label>
          <label v-if="registering" for="register-major">专业<input id="register-major" v-model="major" required maxlength="80" placeholder="例如：软件工程" /></label>
          <label for="login-password">密码<input id="login-password" v-model="password" required type="password" :minlength="registering ? 8 : undefined" :maxlength="registering ? 72 : undefined" :autocomplete="registering ? 'new-password' : 'current-password'" :aria-describedby="registering ? 'register-password-help' : undefined" /><small v-if="registering" id="register-password-help">至少 8 位；支持粘贴和密码管理器。</small></label>
          <label v-if="registering" for="register-confirmation">确认密码<input id="register-confirmation" v-model="confirmation" required type="password" maxlength="72" autocomplete="new-password" :aria-invalid="confirmation.length > 0 && confirmation !== password" :aria-describedby="confirmation.length > 0 && confirmation !== password ? 'register-confirmation-error' : undefined" /><small v-if="confirmation.length > 0 && confirmation !== password" id="register-confirmation-error" class="community-field-error">两次输入的密码不一致。</small></label>
        </fieldset>
        <button class="p-button full" :disabled="busy">{{ busy ? (registering ? '正在注册…' : '正在登录…') : (registering ? '创建学生账号' : '登录') }}</button>
      </form>
      <details v-if="business.demoAccounts && !registering" class="community-demo-accounts"><summary>查看本地演示账号</summary><dl><dt>学生账号</dt><dd>student / student2</dd><dt>摄影社负责人</dt><dd>photo_manager</dd><dt>程序设计协会负责人</dt><dd>code_manager</dd><dt>平台管理员</dt><dd>platform_admin</dd><dt>演示密码</dt><dd>CampusDemo123!</dd></dl><p>账号与资料均为虚构，仅在 demo 配置下创建。正式部署不得启用公开演示账号。</p></details>
      <p class="p-small p-top-gap">找回密码与学校统一身份认证暂未开放。</p>
    </section>
    <section v-else class="p-panel"><h2>纯前端原型没有登录服务</h2><p>请启动正常开发模式体验真实登录；此模式仍只展示浏览器模拟数据。</p><RouterLink to="/clubs" class="p-button secondary">返回社团列表</RouterLink></section>
  </div>
</template>
