import { computed, reactive, watch } from 'vue';
import { business, businessMode, clubCatalog } from '../prototype/business';
import { state, toggleFavorite, toast } from '../prototype/store';
import { request, errorMessage } from '../api';
export const favoriteState = reactive({ saving: false, error: '' });
export const favoriteClubIds = computed(() => businessMode ? (business.user ? clubCatalog.value.filter(c => business.favoriteIds.includes(c.backendId!)).map(c => c.id) : []) : state.favorites);
let generation = 0;
watch(() => business.user?.id, () => { ++generation; favoriteState.saving = false; favoriteState.error = ''; }, { flush: 'sync' });
export async function toggleClubFavorite(id: string) {
  if (!businessMode) { toggleFavorite(id); return true; }
  if (!business.user) { toast('请先登录后收藏社团'); return false; }
  const club = clubCatalog.value.find(c => c.id === id);
  if (!club?.backendId || favoriteState.saving || business.loading) return false;
  const userId = business.user.id, ticket = ++generation, remove = business.favoriteIds.includes(club.backendId);
  favoriteState.saving = true; favoriteState.error = '';
  try {
    await request(`/favorites/${club.backendId}`, { method: remove ? 'DELETE' : 'PUT' });
    if (ticket !== generation || userId !== business.user?.id) return false;
    business.favoriteIds = remove ? business.favoriteIds.filter(value => value !== club.backendId) : [...new Set([...business.favoriteIds, club.backendId])];
    toast(remove ? '已取消账号收藏' : '已收藏到当前账号'); return true;
  } catch (error) { if (ticket === generation) { favoriteState.error = `${errorMessage(error)}。请刷新记录核对收藏状态后重试。`; toast(favoriteState.error); } return false; }
  finally { if (ticket === generation) favoriteState.saving = false; }
}
