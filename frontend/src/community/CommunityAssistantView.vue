<script setup lang="ts">
import { computed } from 'vue';
import { business, businessMode, clubCatalog } from '../prototype/business';
import { state } from '../prototype/store';
import { publicPreview } from '../prototype/public';
import { recommendationState, loadRecommendations } from './recommendations';
import CommunityIcon from './CommunityIcon.vue';
const interests = computed(() => businessMode ? (business.profile?.id === business.user?.id ? business.profile?.interests ?? [] : []) : state.profile.interests);
const recommended = computed(() => clubCatalog.value.filter(c => c.recruiting && c.tags.some(t => interests.value.includes(t))).slice(0, 3));
const interestDescription = computed(() => business.profile?.id === business.user?.id ? business.profile?.interestDescription || '' : '');
</script>
<template>
  <div class="community-directory community-assistant-page">
    <div class="community-page-title"><h1>助手</h1><RouterLink to="/guide">使用说明<CommunityIcon name="chevron" /></RouterLink></div>
    <div class="community-assistant-intro"><span><CommunityIcon name="assistant" /></span><div><h2>需要了解什么？</h2><p>从招新条件、社团资料和你的兴趣开始。</p></div></div>
    <section v-if="businessMode" class="community-assistant-section" aria-labelledby="assistant-semantic-title" :aria-busy="recommendationState.loading">
      <div class="community-section-heading"><h2 id="assistant-semantic-title">适合你的社团</h2><RouterLink to="/me">编辑兴趣<CommunityIcon name="chevron" /></RouterLink></div>
      <p v-if="!business.user" class="community-section-description">登录并填写兴趣描述后，可以获取社团推荐。<RouterLink to="/login" class="community-text-link">前往登录</RouterLink></p>
      <template v-else>
        <p class="community-interest-description">{{ interestDescription || '还没有填写兴趣描述。到“我的 → 编辑资料”，写下想学什么、喜欢哪些活动。' }}</p>
        <p v-if="interests.length" class="community-section-description">辅助标签：{{ interests.join('、') }}</p>
        <button type="button" class="community-button" :disabled="!interestDescription.trim() || recommendationState.loading" @click="loadRecommendations">{{ recommendationState.loading ? '正在匹配…' : recommendationState.requested ? '重新匹配' : '按我的兴趣推荐' }}</button>
        <p v-if="recommendationState.loading" class="community-section-description" role="status">正在比较兴趣描述与社团介绍，请稍候。</p>
        <p v-if="recommendationState.error" class="p-form-error" role="alert">{{ recommendationState.error }}。可重新尝试，或先浏览社团。</p>
        <div v-if="recommendationState.requested" aria-live="polite">
          <RouterLink v-for="club in recommendationState.items" :key="club.clubId" :to="`/clubs/${club.slug || club.clubId}`" class="community-semantic-row"><div><h3>{{ club.name }}</h3><p>匹配参考：{{ club.description }}</p><p>招新条件：{{ club.requirements || '请联系社团负责人' }}</p><p>{{ club.schedule || '时间待安排' }} · {{ club.place || '地点待安排' }}</p><small>语义相似度 {{ club.score.toFixed(3) }} · {{ club.bm25Score > 0 ? '文字线索命中' : '无直接文字命中' }}；按混合检索排序，不是录取概率</small></div><CommunityIcon name="chevron" /></RouterLink>
          <p v-if="!recommendationState.items.length" class="community-section-description">暂无可展示的匹配结果，可以补充兴趣描述，或浏览全部社团。</p>
        </div>
        <p class="community-section-description">综合社团名称、类别、简介和标签，使用语义匹配与 BM25 文字检索融合排序，最多展示 3 个招新社团。招新条件与时间仍需本人确认；未配置模型时不会展示模拟推荐。</p>
      </template>
    </section>
    <section v-else class="community-assistant-section" aria-labelledby="assistant-interest-title">
      <div class="community-section-heading"><h2 id="assistant-interest-title">兴趣线索</h2><RouterLink to="/me">编辑兴趣<CommunityIcon name="chevron" /></RouterLink></div>
      <p class="community-section-description">当前使用本浏览器兴趣：{{ interests.join('、') || '尚未选择' }}。仅按共同标签筛选，不是 AI 推荐。</p>
      <RouterLink v-for="club in recommended" :key="club.id" :to="`/clubs/${club.id}`" class="community-interest-row"><span class="community-small-cover" :class="`cover-${club.id}`"><CommunityIcon :name="club.id" /></span><div><h3>{{ club.name }}</h3><p>共同兴趣：{{ club.tags.filter(t => interests.includes(t)).join('、') }}</p></div><CommunityIcon name="chevron" /></RouterLink>
      <p v-if="!recommended.length" class="community-section-description">暂无匹配的招新社团，可以先浏览全部社团。</p>
    </section>
    <section class="community-assistant-section" aria-labelledby="assistant-knowledge-title">
      <div class="community-section-heading"><h2 id="assistant-knowledge-title">社团资料问答</h2></div>
      <p class="community-section-description">{{ publicPreview ? '纯前端预览暂不开放问答。先查看社团介绍与招新条件。' : '从已上传资料中检索答案，并查看原文出处。默认本地模式返回资料摘录。' }}</p>
      <template v-if="!publicPreview"><RouterLink to="/system/chat" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="assistant" /></span><div><strong>打开资料问答</strong><p>进入现有问答页面</p></div><CommunityIcon name="chevron" /></RouterLink><RouterLink to="/system/knowledge" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="read" /></span><div><strong>公开测试资料</strong><p>上传、查看和管理资料</p></div><CommunityIcon name="chevron" /></RouterLink></template>
      <RouterLink v-else to="/clubs?recruiting=true" class="community-feature-link"><span class="community-feature-icon"><CommunityIcon name="join" /></span><div><strong>查看招新条件</strong><p>先了解，再申请</p></div><CommunityIcon name="chevron" /></RouterLink>
    </section>
    <section class="community-assistant-boundary"><h2>哪些事情仍由你决定？</h2><p>申请、报名和发布仍需本人确认。自动策划与工具调用尚未开放；推荐只提供参考，不自动替你报名。</p></section>
    <p class="community-list-disclosure">虚构测试资料 · {{ publicPreview ? '纯前端原型，非真实报名' : '问答需要校园服务与 AI 服务运行' }}</p>
  </div>
</template>
