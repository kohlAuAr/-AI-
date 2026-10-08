<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { request, errorMessage, type Club } from '../api';

const clubs = ref<Club[]>([]);
const keyword = ref('');
const error = ref('');
const loading = ref(true);
const selected = ref<Club>();
const filtered = computed(() => clubs.value.filter(club => `${club.name} ${club.category} ${club.tags}`.toLowerCase().includes(keyword.value.toLowerCase())));
onMounted(async () => {
  try { clubs.value = await request<Club[]>('/clubs'); }
  catch (e) { error.value = errorMessage(e); }
  finally { loading.value = false; }
});
async function detail(id: number) {
  try { selected.value = await request<Club>(`/clubs/${id}`); }
  catch (e) { error.value = errorMessage(e); }
}
</script>

<template>
  <div class="page-heading"><div><h1>社团接口</h1><p class="intro">查看校园服务返回的公开社团资料。入社申请请使用学生端社团详情。</p></div></div>
  <section class="panel"><label class="field-label" for="club-search">查找社团</label><input id="club-search" v-model="keyword" class="search-input" placeholder="输入名称、类别或兴趣标签" /></section>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="loading" class="empty">正在读取社团资料…</p>
  <div class="club-grid"><article v-for="club in filtered" :key="club.id" class="panel club-card"><span class="badge neutral">{{ club.category }} · 示例</span><h2>{{ club.name }}</h2><p>{{ club.description }}</p><div class="tags"><span v-for="tag in club.tags.split(',')" :key="tag">{{ tag }}</span></div><div class="card-actions"><span class="muted">{{ club.campus }}</span><button class="button secondary" @click="detail(club.id)">查看详情</button></div></article></div>
  <p v-if="!loading && !error && !filtered.length" class="empty">没有符合条件的社团。</p>
  <section v-if="selected" class="panel"><div class="section-heading"><h2>{{ selected.name }}</h2><button class="button secondary" @click="selected = undefined">收起</button></div><p>{{ selected.description }}</p><p class="note">这是只读接口查看页。申请加入请前往学生端社团列表和详情页面。</p></section>
</template>
