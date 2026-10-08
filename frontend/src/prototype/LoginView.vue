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
  <div class="p-page-heading"><p class="p-kicker">CAMPUS ACCOUNT</p><h1>登录，开始你的社团故事。</h1><p>身份和社团管理权限由后端确认，申请与审核结果保存到数据库。</p></div>
  <div v-if="businessMode" class="p-two-columns"><section class="p-panel"><h2>账号登录</h2><form class="p-form" @submit.prevent="submit"><label>账号<input v-model="username" required maxlength="64" autocomplete="username" /></label><label>密码<input v-model="password" required type="password" autocomplete="current-password" /></label><p v-if="error" role="alert" class="p-form-error">{{ error }}</p><button class="p-button" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button></form></section>
    <section v-if="business.demoAccounts" class="p-panel"><h2>本地演示账号</h2><p>所有人物和社团资料为虚构数据；以下账号仅在 demo 配置中自动创建。</p><div class="p-list-item"><div><h3>学生：student / student2</h3><p>提交申请、查看审核结果与已加入社团。</p></div></div><div class="p-list-item"><div><h3>摄影社负责人：photo_manager</h3><p>仅可审核摄影社申请、查看该社团成员。</p></div></div><div class="p-list-item"><div><h3>程序设计协会负责人：code_manager</h3><p>用于验证不能管理其他社团。</p></div></div><p>演示密码：CampusDemo123!</p><p class="p-small">暂无注册、找回密码或正式学校身份认证。生产环境不得启用这些公开演示账号。</p></section></div>
  <section v-else class="p-panel"><h2>纯前端原型没有登录服务</h2><p>请启动正常开发模式体验真实登录；此模式仍只展示浏览器模拟数据。</p></section>
</template>
