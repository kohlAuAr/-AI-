<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue';
import { NButton, NTag } from 'naive-ui';
import { business, businessMode } from '../prototype/business';
import { displayTime } from '../api';
import { modelAdmin, canManageModels, loadModels, modelOperation, releaseModels, type Purpose, type Protocol, type ModelProfile } from './platformModels';
import CommunityDialog from './CommunityDialog.vue';
const allowed = computed(canManageModels), selected = ref<Purpose>('CHAT');
const profile = computed(() => modelAdmin.overview?.profiles.find(p => p.purpose === selected.value));
const form = reactive({ protocol: 'OLLAMA' as Protocol, vendor: 'Ollama', baseUrl: 'http://127.0.0.1:11434', model: '', apiKey: '' });
const installed = ref<string[]>([]), notice = ref(''), confirmation = ref(false), reindex = ref(false), cloud = ref(false);
const labels = { CHAT: '聊天模型', EMBEDDING: 'Embedding 模型' };
const protocols = { NONE: '关闭模型能力', OLLAMA: '本地部署 · Ollama', OPENAI: '云端 API · OpenAI 兼容' };
let scope = 0;
const dirty = computed(() => !!profile.value && (form.protocol !== profile.value.draft.protocol || form.vendor !== profile.value.draft.vendor || form.baseUrl !== profile.value.draft.baseUrl || form.model !== profile.value.draft.model || !!form.apiKey));
function reset() {
  const value = profile.value?.draft;
  Object.assign(form, value ? { protocol: value.protocol, vendor: value.vendor, baseUrl: value.baseUrl, model: value.model, apiKey: '' } : { protocol: 'OLLAMA', vendor: 'Ollama', baseUrl: 'http://127.0.0.1:11434', model: '', apiKey: '' });
  installed.value = []; notice.value = ''; confirmation.value = false; reindex.value = false; cloud.value = false;
}
watch(() => [business.user?.id, business.user?.role], () => { ++scope; form.apiKey = ''; reset(); void loadModels(); }, { immediate: true, flush: 'sync' });
watch(profile, reset, { flush: 'sync' });
onUnmounted(() => { ++scope; form.apiKey = ''; releaseModels(); });
function protocolChanged() {
  form.apiKey = ''; installed.value = []; notice.value = '';
  form.vendor = form.protocol === 'OLLAMA' ? 'Ollama' : '自定义兼容服务';
  form.baseUrl = form.protocol === 'OLLAMA' ? 'http://127.0.0.1:11434' : '';
  form.model = '';
}
function vendorChanged() {
  const defaults: Record<string, string> = { '千问': 'https://dashscope.aliyuncs.com/compatible-mode/v1', GLM: 'https://open.bigmodel.cn/api/paas/v4', DeepSeek: 'https://api.deepseek.com/v1' };
  form.baseUrl = defaults[form.vendor] || ''; form.model = ''; form.apiKey = ''; notice.value = '';
}
async function discover() {
  const ticket = scope;
  const result = await modelOperation<{ models: string[]; notice: string }>('discover', { baseUrl: form.baseUrl });
  if (ticket !== scope) return;
  if (result) { installed.value = result.models; notice.value = result.models.length ? `服务已连接，发现 ${result.models.length} 个已安装模型。请选择后进行推理测试。` : '服务已连接，但没有已安装模型。请在服务器安装模型后重新检测。'; }
}
async function save() {
  const ticket = scope, input = { ...form, revision: profile.value?.revision ?? 0 };
  form.apiKey = '';
  const result = await modelOperation<ModelProfile>(`${selected.value}/save`, input);
  if (ticket !== scope) return;
  if (result && modelAdmin.overview) { modelAdmin.overview.profiles = modelAdmin.overview.profiles.map(p => p.purpose === result.purpose ? result : p); notice.value = '待启用配置已保存，学生仍使用原配置。请测试后再启用。'; }
}
async function test() {
  const ticket = scope;
  const result = await modelOperation<{ ok: boolean; notice: string; dimension?: number }>(`${selected.value}/test`, { revision: profile.value?.revision });
  if (ticket !== scope) return;
  if (profile.value) profile.value.tested = !!result;
  if (result) notice.value = result.notice + (result.dimension ? ` 向量维度：${result.dimension}。` : '');
}
async function activate() {
  const ticket = scope;
  const result = await modelOperation<ModelProfile>(`${selected.value}/activate`, { revision: profile.value?.revision, acknowledgeReindex: reindex.value, acknowledgeCloud: cloud.value });
  if (ticket !== scope) return;
  if (result && modelAdmin.overview) { modelAdmin.overview.profiles = modelAdmin.overview.profiles.map(p => p.purpose === result.purpose ? result : p); confirmation.value = false; notice.value = '配置已启用，后续新请求使用新配置。'; }
  else if (profile.value) { profile.value.tested = false; if (modelAdmin.error) modelAdmin.error += '。请重新读取当前生效配置核对结果；网络中断不代表服务器一定未切换。'; }
}
</script>
<template>
  <div class="community-directory platform-model-page">
    <div class="community-page-title"><h1>AI 服务设置</h1><RouterLink to="/assistant">查看学生助手</RouterLink></div>
    <p class="community-page-intro">平台统一提供模型，学生无需配置密钥。服务器先部署模型，这里负责连接、测试和启用。</p>
    <div v-if="!businessMode" class="community-list-message"><h2>原型模式没有模型配置服务</h2><p>请使用正常开发模式登录平台管理员账号。</p></div>
    <div v-else-if="!allowed" class="community-list-message"><h2>请使用平台管理员账号</h2><p>学生和社团负责人不能查看或修改平台模型配置。</p><RouterLink to="/login" class="community-button">前往登录</RouterLink></div>
    <template v-else>
      <div class="model-settings-tabs" role="group" aria-label="配置用途"><button v-for="(label, purpose) in labels" :key="purpose" type="button" :aria-pressed="selected === purpose" :class="{ selected: selected === purpose }" :disabled="modelAdmin.busy || modelAdmin.loading" @click="selected = purpose">{{ label }}</button><NButton quaternary :disabled="modelAdmin.busy || modelAdmin.loading" @click="loadModels">重新读取</NButton></div>
      <p v-if="modelAdmin.loading" role="status">正在读取平台配置…</p><p v-if="modelAdmin.error" class="community-error" role="alert">{{ modelAdmin.error }}</p>
      <template v-if="profile">
        <section class="model-active-summary" aria-labelledby="model-active-title"><div><h2 id="model-active-title">当前生效</h2><NTag :type="profile.active.protocol === 'NONE' ? 'default' : 'success'" size="small">{{ protocols[profile.active.protocol] }}</NTag></div><strong>{{ profile.active.model || '未启用模型' }}</strong><p>{{ profile.active.baseUrl || '不调用模型服务' }}</p><small>{{ profile.activatedAt ? '上次启用：' + displayTime(profile.activatedAt) : '来源：服务器部署配置' }}。连接状态请通过测试确认。</small></section>
        <form class="model-settings-form" @submit.prevent="save"><h2>待启用配置</h2><p>{{ selected === 'CHAT' ? '用于问答和活动策划。关闭后保留本地摘录与固定查询，不是本地大模型。' : '用于兴趣匹配和资料向量检索，可与聊天模型使用不同服务。' }}</p>
          <fieldset :disabled="modelAdmin.busy || modelAdmin.loading">
            <label for="model-protocol">接入方式<select id="model-protocol" v-model="form.protocol" @change="protocolChanged"><option value="OLLAMA">本地部署 · Ollama</option><option value="OPENAI">云端 API · OpenAI 兼容</option><option value="NONE">关闭模型能力</option></select></label>
            <template v-if="form.protocol !== 'NONE'">
              <label v-if="form.protocol === 'OPENAI'" for="model-vendor">服务商<select id="model-vendor" v-model="form.vendor" @change="vendorChanged"><option>自定义兼容服务</option><option>千问</option><option>GLM</option><option>DeepSeek</option></select></label>
              <label for="model-url">服务基础地址<input id="model-url" v-model="form.baseUrl" required maxlength="400" type="url" placeholder="http://127.0.0.1:11434" @input="installed = []; notice = ''; form.apiKey = ''" /></label>
              <p class="model-field-help">{{ form.protocol === 'OLLAMA' ? '127.0.0.1 指 AI 服务所在机器，不是浏览器所在电脑。这里只读取已安装模型，不下载、不启动模型。' : '填写服务商提供的 HTTPS 基础地址，不要填写完整 chat/completions 路径。服务商名称不代表已完成真实兼容验收。' }}</p>
              <NButton v-if="form.protocol === 'OLLAMA'" secondary :disabled="!form.baseUrl || modelAdmin.busy" :loading="modelAdmin.operation === 'discover'" @click="discover">检测服务并读取模型</NButton>
              <label v-if="installed.length" for="installed-model">已安装模型<select id="installed-model" v-model="form.model"><option value="" disabled>请选择模型（仍需测试用途是否匹配）</option><option v-for="name in installed" :key="name" :value="name">{{ name }}</option></select></label>
              <label for="model-name">模型 ID<input id="model-name" v-model="form.model" required maxlength="120" placeholder="填写或从已安装列表选择" /></label>
              <label for="model-key">{{ form.protocol === 'OPENAI' ? 'API Key' : '服务访问密钥（直连 Ollama 通常留空）' }}<input id="model-key" v-model="form.apiKey" type="password" autocomplete="new-password" maxlength="4096" placeholder="留空保留同地址已保存密钥；更换地址需重新填写" /></label>
              <p class="model-field-help">{{ profile.draft.keyConfigured ? '同地址已配置密钥，保存后不回显原文。' : '尚未配置密钥。' }} {{ modelAdmin.overview?.secretStorageReady ? '密钥在 AI 数据库加密保存。' : '服务器尚未配置加密主密钥，不能保存新密钥，请部署人员运行初始化脚本。' }}</p>
              <p v-if="form.protocol === 'OPENAI'" class="model-field-help">测试及启用前复测会调用云端，可能产生少量费用。测试仅发送固定测试文本；启用后的学生问题和检索上下文可能发送给服务商。</p>
            </template>
            <div class="model-settings-actions"><NButton attr-type="submit" :disabled="modelAdmin.busy" :loading="modelAdmin.operation.endsWith('/save')">保存待启用配置</NButton><NButton :disabled="!profile.saved || dirty || modelAdmin.busy" :loading="modelAdmin.operation.endsWith('/test')" @click="test">测试已保存配置</NButton><NButton type="primary" :disabled="!profile.saved || !profile.tested || dirty || modelAdmin.busy" @click="confirmation = true">启用此配置</NButton></div>
          </fieldset>
        </form>
        <p v-if="notice" class="model-operation-notice" role="status">{{ notice }}</p>
        <details class="model-deployment-help"><summary>部署与安全说明</summary><p>允许连接的地址由部署人员设置 AI_MODEL_ALLOWED_URLS，网页不能扩大访问范围：</p><ul><li v-for="url in modelAdmin.overview?.allowedBaseUrls" :key="url">{{ url }}</li></ul><p>云端测试和启用前复测会产生少量模型调用费用。启用云端后，相关问题、历史上下文和检索片段可能发送给服务商，应先完成学校数据授权。Ollama 中也可能有云端模型，请部署人员仅安装/允许本地模型，需严格本地时关闭 Ollama 云功能。</p><p>更换 Embedding 后不自动重建旧资料。仅相同版本向量参与语义检索，旧资料仍可按关键词检索；需要当前模型向量时重新上传原文。此页面不部署 Nginx、不管理服务器或下载模型。</p></details>
      </template>
    </template>
    <CommunityDialog v-if="confirmation && allowed && profile" title="确认启用平台模型" :busy="modelAdmin.busy" @close="confirmation = false"><p>{{ labels[selected] }}将切换为 {{ profile.draft.model || '关闭模型能力' }}。已有请求继续使用原配置，新请求使用新配置；启用前会再次测试，失败保留原配置。</p><label v-if="profile.draft.protocol === 'OPENAI'" class="model-confirm-check"><input v-model="cloud" type="checkbox" />我已确认云端服务的数据使用授权与调用费用。</label><label v-if="selected === 'EMBEDDING'" class="model-confirm-check"><input v-model="reindex" type="checkbox" />我理解旧向量不自动重建，不能混用不同模型的向量。</label><p v-if="modelAdmin.error" class="community-error" role="alert">{{ modelAdmin.error }}</p><div class="community-dialog-actions"><NButton type="primary" :loading="modelAdmin.operation.endsWith('/activate')" :disabled="modelAdmin.busy || profile.draft.protocol === 'OPENAI' && !cloud || selected === 'EMBEDDING' && !reindex" @click="activate">确认启用</NButton><NButton :disabled="modelAdmin.busy" @click="confirmation = false">暂不启用</NButton></div></CommunityDialog>
  </div>
</template>
<style scoped>
.model-settings-tabs { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
.model-settings-tabs > button:not(.n-button) { min-height: 44px; padding: 10px 16px; border-bottom: 2px solid transparent; color: #666d7d; }
.model-settings-tabs > button.selected { border-color: #646cff; color: #555cd6; }
.model-settings-tabs .n-button { margin-left: auto; }
.model-active-summary { border-left: 3px solid #646cff; background: #fafaff; padding: 18px 20px; margin-bottom: 30px; overflow-wrap: anywhere; }
.model-active-summary > div { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.model-active-summary h2, .model-settings-form h2 { font-size: 17px; font-weight: 600; margin: 0 0 12px; }
.model-active-summary strong { display: block; font-size: 16px; margin: 4px 0 8px; }
.model-active-summary p { font-size: 13px; margin-bottom: 8px; color: #626979; }
.model-active-summary small { color: #666d7d; font-size: 12px; }
.model-settings-form { max-width: 760px; }
.model-settings-form > p { color: #666d7d; font-size: 13px; }
.model-settings-form fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 14px; min-width: 0; }
.model-settings-form label { display: grid; gap: 8px; font-size: 14px; color: #343b48; }
.model-settings-form input, .model-settings-form select { width: 100%; min-width: 0; min-height: 44px; padding: 10px 12px; border: 1px solid #dedfe6; border-radius: 6px; background: #fff; font: inherit; }
.model-field-help { margin: -4px 0 0; color: #666d7d; font-size: 12px; line-height: 1.8; }
.model-settings-form .n-button { justify-self: start; min-height: 44px; }
.model-settings-actions { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 8px; }
.model-settings-actions .n-button { min-height: 44px; }
.model-operation-notice { padding: 14px 16px; border-radius: 6px; color: #3a6352; background: #f0f8f4; margin-top: 20px; }
.model-deployment-help { margin-top: 28px; border-top: 1px solid #efeff5; padding-top: 16px; color: #626979; font-size: 13px; overflow-wrap: anywhere; }
.model-deployment-help summary { min-height: 44px; cursor: pointer; }
.model-confirm-check { display: flex; gap: 10px; align-items: flex-start; margin: 18px 0; font-size: 14px; line-height: 1.8; }
.model-confirm-check input { width: 18px; height: 18px; flex-shrink: 0; margin-top: 4px; }
@media (max-width: 760px) { .model-settings-form input, .model-settings-form select { font-size: 16px; } .model-active-summary { padding: 16px; } .model-settings-actions { display: grid; grid-template-columns: 1fr; } .model-settings-actions .n-button { width: 100%; } }
</style>
