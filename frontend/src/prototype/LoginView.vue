<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { business, businessMode, login } from './business';
import { errorMessage } from '../api';
const router = useRouter();
const username = ref(''); const password = ref(''); const busy = ref(false); const error = ref('');
async function submit() {
  busy.value = true; error.value = '';
  try {
    await login(username.value.trim(), password.value);
    password.value = '';
    await router.push(business.user?.role === 'MANAGER' ? '/manage/recruitment' : '/me');
  } catch (failure) { error.value = errorMessage(failure); }
  finally { busy.value = false; }
}
</script>
<template>
  <div class="community-login-page">
    <div class="p-page-heading"><h1>账号登录</h1><p>登录后查看你的社团、申请和活动记录。</p></div>
    <section v-if="businessMode" class="p-panel">
      <p class="community-login-intro">学生与负责人使用同一个入口，管理权限由账号确定。</p>
      <form class="p-form" @submit.prevent="submit"><label for="login-username">账号<input id="login-username" v-model="username" required maxlength="64" autocomplete="username" autocapitalize="none" :aria-describedby="error ? 'login-error' : undefined" /></label><label for="login-password">密码<input id="login-password" v-model="password" required type="password" autocomplete="current-password" :aria-describedby="error ? 'login-error' : undefined" /></label><p v-if="error" id="login-error" role="alert" class="p-form-error">{{ error }}</p><button class="p-button full" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button></form>
      <details v-if="business.demoAccounts" class="community-demo-accounts"><summary>查看本地演示账号</summary><dl><dt>学生账号</dt><dd>student / student2</dd><dt>摄影社负责人</dt><dd>photo_manager</dd><dt>程序设计协会负责人</dt><dd>code_manager</dd><dt>演示密码</dt><dd>CampusDemo123!</dd></dl><p>账号与资料均为虚构，仅在 demo 配置下创建。正式部署不得启用公开演示账号。</p></details>
      <p class="p-small p-top-gap">本页面尚未接入注册、找回密码或学校统一身份认证。</p>
    </section>
    <section v-else class="p-panel"><h2>纯前端原型没有登录服务</h2><p>请启动正常开发模式体验真实登录；此模式仍只展示浏览器模拟数据。</p><RouterLink to="/clubs" class="p-button secondary">返回社团列表</RouterLink></section>
  </div>
</template>
