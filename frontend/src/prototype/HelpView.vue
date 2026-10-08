<script setup lang="ts">
import { publicPreview } from './public';
import { businessMode } from './business';
</script>
<template>
  <div class="p-page-heading"><h1>使用说明</h1><p>{{ businessMode ? '本地校园社团系统，账号与资料均为虚构测试数据。' : '响应式交互原型，记录仅保存在当前浏览器。' }}</p></div>
  <section class="p-panel"><h2>学生怎么使用</h2><ol class="p-guide-list"><li v-if="businessMode">在登录页注册学生账号，再登录；进入“我的”编辑姓名、专业、兴趣和空闲时间。</li><li>在社团列表搜索、筛选，查看介绍和招新条件。</li><li>进入社团详情，填写加入理由并确认提交。</li><li>在“我的”查看申请结果、已加入社团和活动报名。</li><li>从活动列表进入详情，确认报名，必要时取消。</li><li>从顶部消息入口查看审核和报名通知。</li></ol><RouterLink to="/clubs" class="p-button">浏览社团</RouterLink></section>
  <section class="p-panel"><h2>负责人怎么使用</h2><ol class="p-guide-list"><li>{{ businessMode ? '使用负责人账号登录，权限由后端核验。' : '进入负责人演示工作台，选择要管理的社团。' }}</li><li>查看入社申请，通过或拒绝并填写意见。</li><li>通过申请后，在成员管理中核对成员名单。</li><li>新建活动草稿，检查内容后确认发布。</li><li>活动发布后查看学生报名名单。</li></ol><RouterLink :to="businessMode ? '/login' : '/manage'" class="p-button secondary">{{ businessMode ? '打开登录页' : '体验负责人工作台' }}</RouterLink></section>
  <section class="p-panel"><h2>数据与功能边界</h2>
    <template v-if="businessMode"><p>注册、登录、个人资料与兴趣、社团查询、入社申请、审核、撤回、成员关系、活动草稿、发布、报名、取消与站内通知已接通数据库。默认 H2 文件库，重启后业务记录保留，登录会话可能需要重新建立。</p><p>社团维护、活动编辑与取消、签到、经费和账号收藏已有后端接口，但对应前端操作尚未全部连接。当前收藏仍仅保存在本浏览器。助手根据账号兴趣描述与社团资料做语义推荐，需要配置 Embedding 模型；未配置时不展示模拟推荐。仅自动过滤招新关闭状态；空闲时间和招新条件仍需本人确认。</p><p>通知仅记录功能接入后的事件，没有实时推送。ReAct、MCP 与 DAG 仍为计划实现；模型不可用不影响个人资料保存或基础报名。</p></template>
    <template v-else><p>此模式没有真实登录和权限认证。申请、审核、报名与预算台账均为浏览器模拟；不同设备、浏览器和访问地址的记录不共享。</p><p>{{ publicPreview ? '独立纯前端网页不调用后端、数据库或 AI。' : '纯原型模式与正常开发模式相互独立，不向业务服务提交申请。' }}经费仅汇总活动计划预算，不含支付、报销或学校级审批。</p></template>
  </section>
  <section class="p-panel"><h2>资料助手与隐私</h2><p>{{ publicPreview ? '纯前端预览不开放资料问答。' : '资料问答需启动业务与 AI 服务。本地模式只做关键词检索并展示原文摘录，配置模型后才会调用模型接口。' }}资料联调尚未完成私有资料权限隔离，请勿上传个人敏感资料，不要公开部署本地演示系统。</p><RouterLink to="/assistant" class="p-text-button">查看助手</RouterLink></section>
  <section class="p-panel"><h2>手机访问</h2><p>页面适配手机浏览器。在局域网原型模式下，手机与电脑需处于可互通的网络，使用电脑的局域网地址访问。正常开发系统默认仅供本机联调，不自动开启公网服务。</p></section>
</template>
