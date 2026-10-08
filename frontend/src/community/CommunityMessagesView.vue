<script setup lang="ts">
import { useRouter } from 'vue-router';
import { business } from '../prototype/business';
import { myNotices, notificationState as inbox, unreadNotices, refreshNotifications, readNotice, readAllNotices, type Notice } from './notifications';
import CommunityIcon from './CommunityIcon.vue';
const router = useRouter();
async function open(notice: Notice) { if (await readNotice(notice.id)) await router.push(notice.targetPath); }
function time(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }); }
</script>
<template>
  <section class="community-directory">
    <header class="community-page-title"><h1>消息</h1><button type="button" class="community-text-button" :disabled="!business.user || inbox.loading || inbox.saving" @click="refreshNotifications(0)">刷新</button></header>
    <template v-if="!business.user"><div class="community-list-message"><h2>登录后查看消息</h2><p>入社审核结果与活动报名通知会保存在你的账号中。</p><RouterLink to="/login" class="community-button">去登录</RouterLink></div></template>
    <template v-else>
      <div class="community-message-toolbar"><span role="status" aria-atomic="true">{{ inbox.loading ? '正在读取消息…' : `共 ${inbox.total} 条消息，${unreadNotices} 条未读` }}</span><button type="button" class="community-text-button" :disabled="!unreadNotices || inbox.loading || inbox.saving" @click="readAllNotices">全部已读</button></div>
      <div v-if="inbox.error" class="community-list-message" role="alert"><h2>消息暂时无法读取</h2><p>{{ inbox.error }}</p><button class="community-button" type="button" @click="refreshNotifications(0)">重新加载</button></div>
      <p v-if="inbox.writeError" class="community-message-error community-error" role="alert">{{ inbox.writeError }}，请重试。</p>
      <div v-if="!inbox.loading && !inbox.error && !myNotices.length" class="community-list-message"><h2>{{ inbox.page ? '这一页没有消息' : '暂时没有消息' }}</h2><p>新的审核结果和报名变更会在这里通知你。</p></div>
      <article v-for="notice in myNotices" :key="notice.id" class="community-message-row">
        <div class="community-message-symbol"><CommunityIcon :name="notice.type.startsWith('APPLICATION') ? 'join' : 'calendar'" /></div>
        <div class="community-message-body"><div class="community-message-heading"><h2>{{ notice.title }}</h2><span class="community-message-status" :class="{ unread: !notice.readAt }">{{ notice.readAt ? '已读' : '未读' }}</span></div><p>{{ notice.content }}</p><time :datetime="notice.createdAt">{{ time(notice.createdAt) }}</time><div class="community-message-actions"><button class="community-text-button" type="button" :disabled="inbox.saving" @click="open(notice)">查看相关记录<CommunityIcon name="chevron" /></button><button v-if="!notice.readAt" class="community-text-button" type="button" :disabled="inbox.saving" @click="readNotice(notice.id)">标为已读</button></div></div>
      </article>
      <div v-if="!inbox.error && (inbox.page > 0 || inbox.hasMore)" class="community-message-pagination"><button type="button" class="community-text-button" :disabled="!inbox.page || inbox.loading || inbox.saving" @click="refreshNotifications(inbox.page - 1)">上一页</button><span>第 {{ inbox.page + 1 }} 页</span><button type="button" class="community-text-button" :disabled="!inbox.hasMore || inbox.loading || inbox.saving" @click="refreshNotifications(inbox.page + 1)">下一页</button></div>
    </template>
    <p class="community-list-disclosure">仅记录功能接入后的业务通知。历史消息保留当时结果，当前状态以相关记录为准。</p>
  </section>
</template>
