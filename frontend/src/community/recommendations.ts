import { computed, reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';

export interface Recommendation { clubId: number; slug: string | null; name: string; description: string; requirements: string; schedule: string; place: string; score: number; bm25Score: number; fusionScore: number }
interface Result { method: string; items: Recommendation[] }
export const recommendationState = reactive({ items: [] as Recommendation[], loading: false, error: '', requested: false });
let generation = 0;
const currentInterest = computed(() => business.user && business.profile?.id === business.user.id ? business.profile.interestDescription || '' : '');
watch(() => [business.user?.id, currentInterest.value], () => {
  ++generation; recommendationState.items = []; recommendationState.error = ''; recommendationState.requested = false; recommendationState.loading = false;
}, { flush: 'sync' });
export async function loadRecommendations() {
  if (!businessMode || recommendationState.loading) return;
  const userId = business.user?.id, interest = currentInterest.value, ticket = ++generation;
  recommendationState.items = []; recommendationState.error = ''; recommendationState.requested = false;
  if (!userId || !interest.trim()) { recommendationState.error = '请先登录，并在个人资料中填写兴趣描述'; return; }
  recommendationState.loading = true;
  try {
    const result = await request<Result>('/ai/recommendations', { method: 'POST' });
    if (ticket !== generation || userId !== business.user?.id || interest !== currentInterest.value) return;
    recommendationState.items = result.items; recommendationState.requested = true;
  } catch (error) {
    if (ticket === generation && userId === business.user?.id) recommendationState.error = errorMessage(error);
  } finally { if (ticket === generation) recommendationState.loading = false; }
}
