export interface ModuleStatus {
  key: string;
  name: string;
  status: 'READY' | 'SAMPLE' | 'PLANNED';
  description: string;
}
export interface SystemInfo { name: string; security: string; modules: ModuleStatus[] }
export interface AiStatus { mode: 'LOCAL' | 'OPENAI'; knowledgeDocuments: number; retrieval: string; sessionStore: string; scope: string; agent: string }
export interface Club { id: number; name: string; category: string; description: string; tags: string; campus: string; demo: boolean }
export interface Activity { id: number; clubId: number; title: string; location: string; startTime: string; capacity: number; status: string; demo: boolean }
export interface KnowledgeDocument { id: number; name: string; chunkCount: number; embeddingVersion: string; createdAt: string; scope: string }
export interface Citation { documentId: number; documentName: string; chunkNumber: number; excerpt: string; score: number }
export interface Turn { question: string; answer: string; references: Citation[]; mode: string; retrieval: string; createdAt: string }
export interface Reply extends Omit<Turn, 'question'> { conversationId: string }

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
    // Obtain a fresh token: login/logout rotate it. HttpOnly session cookies stay in the browser.
    const sessionResponse = await fetch('/api/auth/session', { credentials: 'same-origin' });
    if (!sessionResponse.ok) throw new Error('无法获取安全令牌，请检查校园服务');
    const session = await sessionResponse.json() as { csrfHeader: string; csrfToken: string };
    headers.set(session.csrfHeader, session.csrfToken);
  }
  const response = await fetch(`/api${path}`, { ...options, headers, credentials: 'same-origin' });
  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    try { const error = await response.json(); message = error.detail || error.message || message; }
    catch { /* The proxy may return plain text when a backend is stopped. */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求未完成，请检查后端服务';
}

export function displayTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}
