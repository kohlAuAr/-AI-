import { reactive, watch } from 'vue';
import { request, errorMessage } from '../api';
import { business, businessMode } from '../prototype/business';

export type Purpose = 'CHAT' | 'EMBEDDING';
export type Protocol = 'NONE' | 'OLLAMA' | 'OPENAI';
export interface ModelChoice { protocol: Protocol; vendor: string; baseUrl: string; model: string; keyConfigured: boolean }
export interface ModelProfile { purpose: Purpose; revision: number; saved: boolean; tested: boolean; draft: ModelChoice; active: ModelChoice; activatedAt: string | null; activatedBy: number | null }
export interface ModelOverview { profiles: ModelProfile[]; allowedBaseUrls: string[]; secretStorageReady: boolean }
export const modelAdmin = reactive({ overview: null as ModelOverview | null, busy: false, loading: false, error: '', operation: '' });
export const canManageModels = () => businessMode && business.user?.role === 'PLATFORM_ADMIN';
let generation = 0;
function clear() { ++generation; modelAdmin.overview = null; modelAdmin.error = ''; modelAdmin.busy = false; modelAdmin.loading = false; modelAdmin.operation = ''; }
watch(() => [business.user?.id, business.user?.role], clear, { flush: 'sync' });
export function releaseModels() { clear(); }
export async function loadModels() {
  if (!canManageModels() || modelAdmin.busy) return;
  const ticket = ++generation; modelAdmin.loading = true; modelAdmin.error = '';
  try { const result = await request<ModelOverview>('/platform/models'); if (ticket === generation) modelAdmin.overview = result; }
  catch (error) { if (ticket === generation) modelAdmin.error = errorMessage(error); }
  finally { if (ticket === generation) modelAdmin.loading = false; }
}
export async function modelOperation<T>(path: string, body: unknown): Promise<T | undefined> {
  if (!canManageModels() || modelAdmin.busy || modelAdmin.loading) return;
  const ticket = generation; modelAdmin.busy = true; modelAdmin.error = ''; modelAdmin.operation = path;
  try { const result = await request<T>('/platform/models/' + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); return ticket === generation ? result : undefined; }
  catch (error) { if (ticket === generation) modelAdmin.error = errorMessage(error); }
  finally { if (ticket === generation) { modelAdmin.busy = false; modelAdmin.operation = ''; } }
}
