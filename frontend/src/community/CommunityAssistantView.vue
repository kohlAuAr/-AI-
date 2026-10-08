<script setup lang="ts">
import { computed } from 'vue';
import { business, businessMode, clubCatalog } from '../prototype/business';
import { state } from '../prototype/store';
import { publicPreview } from '../prototype/public';
import CommunityIcon from './CommunityIcon.vue';
const interests = computed(() => businessMode ? (business.profile?.id === business.user?.id ? business.profile?.interests ?? [] : []) : state.profile.interests);
const recommended = computed(() => clubCatalog.value.filter(c => c.recruiting && c.tags.some(t => interests.value.includes(t))).slice(0, 3));
</script>
<template>
  <div class="community-directory community-assistant-page">
    <div class="community-page-title"><h1>助手</h1><RouterLink to="/guide">使用说明<CommunityIcon name="chevron" /></RouterLink></div>
    <div class="community-assistant-intro"><span><CommunityIcon name="assistant" /></span><div><h2>需要了解什么？</h2><p>从招新条件、社团资料和你的兴趣开始。</p></div></div>
    <section class="community-assistant-section" aria-labelledby="assistant-interest-title">
      <div class="community-section-heading"><h2 id="assistant-interest-title">兴趣线索</h2><RouterLink to="/me">{{ businessMode ? '个人中心' : '编辑兴趣' }}<CommunityIcon name="chevron" /></RouterLink></div>
      <p v-if="businessMode && !business.user" class="community-section-description">登录并填写兴趣后，可以查看与你有共同标签的社团。<RouterLink to="/login" class="community-text-link">前往登录</RouterLink></p>
      <p v-else class="community-section-description">{{ businessMode ? '当前账号兴趣' : '当前使用本浏览器兴趣' }}：{{ interests.join('、') || '尚未选择' }}。仅按共同标签筛选，不是 AI 推荐。</p>
      <RouterLink v-for="club in recommended" :key="club.id" :to="`/clubs/${club.id}`" class="community-interest-row"><span class="community-small-cover" :class="`cover-${club.id}`"><CommunityIcon :name="club.id" /></span><div><h3>{{ club.name }}</h3><p>共同兴趣：{{ club.tags.filter(t => interests.includes(t)).join('、') }}</p></div><CommunityIcon name="chevron" /></RouterLink>
      <p v-if="!recommended.length" class="community-section-description">暂无匹配的招新社团，可以先浏览全部社团。</p>
    </section>
    <section class="community-assistant-section" aria-labelledby="assistant-knowledge-title">
      <div class="community-section-heading"><h2 id="assistant-knowledge-title">社团资料问答</h2></div>
      <p class="community-section-description">{{ publicPreview ? '纯前端预览暂不开放问答。先查看社团介绍与招新条件。' : '从已上传资料中检索答案，并查看原文出处。默认本地模式返回资料摘录。' }}</p>
      <template v-if="!publicPreview"><RouterLink to="/system/chat" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="assistant" /></span><div><strong>打开资料问答</strong><p>进入现有问答页面</p></div><CommunityIcon name="chevron" /></RouterLink><RouterLink to="/system/knowledge" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="read" /></span><div><strong>公开测试资料</strong><p>上传、查看和管理资料</p></div><CommunityIcon name="chevron" /></RouterLink></template>
      <RouterLink v-else to="/clubs?recruiting=true" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="join" /></span><div><strong>查看招新条件</strong><p>先了解，再申请</p></div><CommunityIcon name="chevron" /></RouterLink>
    </section>
    <section class="community-assistant-boundary"><h2>哪些事情仍由你决定？</h2><p>申请、报名和发布仍需本人确认。语义推荐、自动策划与工具调用尚未开放，这里不会用预设回答冒充 AI。</p></section>
    <p class="community-list-disclosure">虚构测试资料 · {{ publicPreview ? '纯前端原型，非真实报名' : '问答需要校园服务与 AI 服务运行' }}</p>
  </div>
</template>
