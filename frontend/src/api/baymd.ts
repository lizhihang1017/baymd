const BASE = '/api/baymd';

// ===== 认证 =====

const TOKEN_KEY = 'baymd_admin_token'

export function getAuthToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/** 统一请求头: 若有登录 token 则带 Authorization */
export function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const token = getAuthToken()
  return {
    ...(extra || {}),
    ...(token ? { Authorization: token } : {}),
  }
}

export async function login(username: string, password: string) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  return res.json();
}

export async function register(username: string, password: string, email?: string) {
  const res = await fetch(`${BASE}/user/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, email })
  });
  return res.json();
}

export async function uploadAvatar(file: File): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${BASE}/user/avatar`, {
    method: 'POST', headers: authHeaders(), body: form
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '上传失败');
  return json.data;
}

export async function getMyMemory() {
  const res = await fetch(`${BASE}/user/memory`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? { facts: [], episodes: [] };
}

export async function deleteMyFact(id: string) {
  const res = await fetch(`${BASE}/user/memory/fact/${id}`, { method: 'DELETE', headers: authHeaders() });
  return res.json();
}

export async function deleteMyEpisode(id: string) {
  const res = await fetch(`${BASE}/user/memory/episode/${id}`, { method: 'DELETE', headers: authHeaders() });
  return res.json();
}

export async function changePassword(oldPassword: string, newPassword: string) {
  const res = await fetch(`${BASE}/user/password`, {
    method: 'PUT', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ oldPassword, newPassword })
  });
  return res.json();
}

export interface Conversation {
  conversationId: string;
  title: string;
  lastTime: string;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  thinkingContent?: string;
  createTime: string;
}

export interface CompletionPayload {
  messageId: string;
  title?: string;
  citations?: { index: number; id: string; snippet: string }[];
  followUpQuestions?: string[];
  emergency?: boolean;
}

export interface SSEMessageEvent {
  type: 'response' | 'think';
  delta: string;
}

export interface SSEMetaEvent {
  conversationId: string;
  taskId: string;
}

// ===== Current User =====

export interface CurrentUser {
  userId: string;
  username: string;
  role: string;
  avatar?: string;
}

// ===== 用户管理 (admin) =====

export async function listUsers(page = 1, size = 100) {
  const res = await fetch(`${BASE}/users?page=${page}&size=${size}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data?.records ?? [];
}

export async function createUser(data: { username: string; password: string; role: string; email?: string; avatar?: string }) {
  const res = await fetch(`${BASE}/users`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(data)
  });
  return res.json();
}

export async function updateUser(id: string, data: { username?: string; password?: string; role?: string; email?: string; avatar?: string }) {
  const res = await fetch(`${BASE}/users/${id}`, {
    method: 'PUT', headers: authHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(data)
  });
  return res.json();
}

export async function deleteUser(id: string) {
  const res = await fetch(`${BASE}/users/${id}`, { method: 'DELETE', headers: authHeaders() });
  return res.json();
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const res = await fetch(`${BASE}/user/me`, { headers: authHeaders() });
  const json = await res.json();
  return json.data;
}

// ===== Conversations =====

export async function listConversations(): Promise<Conversation[]> {
  const res = await fetch(`${BASE}/conversations`, { headers: authHeaders() });
  const json = await res.json();
  return json.data || [];
}

export async function getMessages(conversationId: string): Promise<Message[]> {
  const res = await fetch(`${BASE}/conversations/${conversationId}/messages`, { headers: authHeaders() });
  const json = await res.json();
  return json.data || [];
}

export async function exportConversation(conversationId: string) {
  const res = await fetch(`${BASE}/conversations/${conversationId}/export`, { headers: authHeaders() });
  return res.json();
}

export async function deleteConversation(conversationId: string) {
  await fetch(`${BASE}/conversations/${conversationId}`, { headers: authHeaders(), method: 'DELETE' });
}

export async function clearMemory() {
  const res = await fetch(`${BASE}/memory`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

// ===== Chat (SSE) =====

export function streamChat(
  question: string,
  conversationId: string | null,
  deepThinking: boolean,
  onMeta: (meta: SSEMetaEvent) => void,
  onDelta: (type: 'response' | 'think', delta: string) => void,
  onDone: (payload: CompletionPayload) => void,
  onError: (err: Error) => void,
  reportId?: string | null
): AbortController {
  const controller = new AbortController();
  const params = new URLSearchParams({ question });
  if (conversationId) params.set('conversationId', conversationId);
  if (deepThinking) params.set('deepThinking', 'true');
  if (reportId) params.set('reportId', reportId);

  fetch(`${BASE}/rag/v3/chat?${params.toString()}`, {
    signal: controller.signal,
    headers: { Accept: 'text/event-stream' }
  }).then(async response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const reader = response.body?.getReader();
    if (!reader) throw new Error('No response body');
    const decoder = new TextDecoder();
    let buffer = '';
    let finished = false;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const json = line.slice(5).trim();
          if (json === '[DONE]') continue;
          try {
            const data = JSON.parse(json);
            if (data.conversationId) onMeta(data as SSEMetaEvent);
            else if (data.type && data.delta) onDelta(data.type, data.delta);
            else if (data.messageId !== undefined) {
              finished = true;
              onDone(data as CompletionPayload);
            }
          } catch { /* ignore parse errors */ }
        }
      }
    }
    if (!finished) onDone({ messageId: '', title: '' });
  }).catch(err => {
    if (err.name !== 'AbortError') onError(err);
  });

  return controller;
}

// ===== Feedback =====

export async function submitFeedback(messageId: string, vote: 1 | -1) {
  await fetch(`${BASE}/conversations/messages/${messageId}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ vote })
  });
}

// ===== Report (报告解读) =====

export interface MedicalReport {
  id: string;
  fileName: string;
  parseStatus: string;
  errorMessage?: string;
}

export async function uploadReport(file: File): Promise<MedicalReport> {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${BASE}/rag/report/upload`, { headers: authHeaders(), method: 'POST', body: form });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '上传失败');
  return json.data;
}

// ===== Knowledge Base =====

export async function listKnowledgeBases() {
  const res = await fetch(`${BASE}/knowledge-base`);
  return res.json();
}

export async function listDocuments(kbId: string) {
  const res = await fetch(`${BASE}/knowledge-base/${kbId}/docs`);
  return res.json();
}

export async function listChunkStrategies() {
  const res = await fetch(`${BASE}/knowledge-base/chunk-strategies`);
  const json = await res.json();
  return json.data || [];
}

export async function uploadDocument(kbId: string, file: File, chunkStrategy?: string, chunkConfig?: Record<string, any>) {
  const form = new FormData();
  form.append('file', file);
  form.append('sourceType', 'file');
  form.append('processMode', 'chunk');
  form.append('chunkStrategy', chunkStrategy || 'fixed_size');
  if (chunkConfig) form.append('chunkConfig', JSON.stringify(chunkConfig));
  const res = await fetch(`${BASE}/knowledge-base/${kbId}/docs/upload`, { method: 'POST', body: form });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '上传失败');
  return json.data;
}

export async function chunkDocument(docId: string) {
  await fetch(`${BASE}/knowledge-base/docs/${docId}/chunk`, { method: 'POST' });
}

export async function deleteDocument(docId: string) {
  const res = await fetch(`${BASE}/knowledge-base/docs/${docId}`, { method: 'DELETE' });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '删除失败');
}

export async function previewChunk(file: File, chunkStrategy: string, chunkConfig?: Record<string, any>) {
  const form = new FormData();
  form.append('file', file);
  form.append('chunkStrategy', chunkStrategy || 'fixed_size');
  if (chunkConfig) form.append('chunkConfig', JSON.stringify(chunkConfig));
  const res = await fetch(`${BASE}/knowledge-base/chunk-preview`, { method: 'POST', body: form });
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '预览失败');
  return json.data;
}

export async function getDocumentContent(docId: string): Promise<string> {
  const res = await fetch(`${BASE}/knowledge-base/docs/${docId}/content`);
  const json = await res.json();
  if (!json.success) throw new Error(json.message || '读取失败');
  return json.data || '';
}

export interface DocChunk { chunkIndex?: number; index?: number; content?: string; charCount?: number; tokenCount?: number }

export async function listChunks(docId: string, page: number = 1, size: number = 50) {
  const res = await fetch(`${BASE}/knowledge-base/docs/${docId}/chunks?page=${page}&size=${size}`);
  const json = await res.json();
  return json.data;
}

// ===== Settings =====

export async function getSettings() {
  const res = await fetch(`${BASE}/rag/settings`);
  return res.json();
}

// ===== 意图树 (admin) =====

export interface IntentTreeNode {
  id: string;
  intentCode: string;
  name: string;
  level: number;          // 1=DOMAIN 2=CATEGORY 3=TOPIC
  parentCode?: string | null;
  description?: string;
  examples?: string;
  collectionName?: string;
  topK?: number;
  kind?: number;          // 0=KB 1=SYSTEM 2=MCP
  sortOrder?: number;
  enabled?: number;
  mcpToolId?: string;
  promptSnippet?: string;
  children?: IntentTreeNode[];
}

export async function getIntentTree(): Promise<IntentTreeNode[]> {
  const res = await fetch(`${BASE}/intent-tree/trees`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function createIntentNode(data: Record<string, any>) {
  const res = await fetch(`${BASE}/intent-tree`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function updateIntentNode(id: string, data: Record<string, any>) {
  const res = await fetch(`${BASE}/intent-tree/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function deleteIntentNode(id: string) {
  const res = await fetch(`${BASE}/intent-tree/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

// ===== 数据管理 (admin): 随访/反馈/报告 =====

export async function getFollowupTasks(status?: string, limit = 100) {
  const qs = new URLSearchParams({ limit: String(limit) })
  if (status) qs.set('status', status)
  const res = await fetch(`${BASE}/admin/followup/tasks?${qs}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function getFollowupStats() {
  const res = await fetch(`${BASE}/admin/followup/stats`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? {};
}

export async function triggerFollowup(id: string) {
  const res = await fetch(`${BASE}/admin/followup/tasks/${id}/trigger`, { headers: authHeaders(), method: 'POST' });
  return res.json();
}

export async function cancelFollowup(id: string) {
  const res = await fetch(`${BASE}/admin/followup/tasks/${id}/cancel`, { headers: authHeaders(), method: 'POST' });
  return res.json();
}

export async function deleteFollowup(id: string) {
  const res = await fetch(`${BASE}/admin/followup/tasks/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

export async function getFeedback(vote?: number, limit = 100) {
  const qs = new URLSearchParams({ limit: String(limit) })
  if (vote !== undefined) qs.set('vote', String(vote))
  const res = await fetch(`${BASE}/admin/feedback?${qs}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function getFeedbackStats() {
  const res = await fetch(`${BASE}/admin/feedback/stats`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? {};
}

export async function deleteFeedback(id: string) {
  const res = await fetch(`${BASE}/admin/feedback/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

export async function getReports(limit = 100) {
  const res = await fetch(`${BASE}/admin/reports?limit=${limit}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function getReportDetail(id: string) {
  const res = await fetch(`${BASE}/admin/reports/${id}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data;
}

export async function deleteReport(id: string) {
  const res = await fetch(`${BASE}/admin/reports/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

// ===== 系统健康检查 (admin) =====

export async function getHealth() {
  const res = await fetch(`${BASE}/admin/health`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? null;
}

// ===== Trace =====

export async function getTraces(page: number = 1, size: number = 10) {
  const res = await fetch(`${BASE}/rag/traces/runs?page=${page}&size=${size}`, { headers: authHeaders() });
  return res.json();
}

// ===== Runtime Config (admin) =====

export async function getConfig(section: string): Promise<string | null> {
  const res = await fetch(`${BASE}/admin/config/${section}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? null;
}

export async function saveConfig(section: string, json: string) {
  const res = await fetch(`${BASE}/admin/config/${section}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: json
  });
  return res.json();
}

// ===== 工具管理 (admin) =====

export interface ToolItem {
  name: string;
  label: string;
  source: 'local' | 'mcp';
  description: string;
  enabled: boolean;
  type: string;
}

// ===== 用户记忆 CRUD (admin) =====

export async function getMemoryUsers() {
  const res = await fetch(`${BASE}/admin/memory/users`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function getMemoryFacts(userId: string) {
  const res = await fetch(`${BASE}/admin/memory/facts?userId=${encodeURIComponent(userId)}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function getMemoryEpisodes(userId: string) {
  const res = await fetch(`${BASE}/admin/memory/episodes?userId=${encodeURIComponent(userId)}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function createMemoryFact(data: { userId: string; factType: string; factText: string; confidence?: number }) {
  const res = await fetch(`${BASE}/admin/memory/fact`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function updateMemoryFact(id: string, data: { factType?: string; factText?: string; confidence?: number }) {
  const res = await fetch(`${BASE}/admin/memory/fact/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function deleteMemoryFact(id: string) {
  const res = await fetch(`${BASE}/admin/memory/fact/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

export async function createMemoryEpisode(data: { userId: string; title: string; summary: string; topics: string[] }) {
  const res = await fetch(`${BASE}/admin/memory/episode`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function updateMemoryEpisode(id: string, data: { title?: string; summary?: string; topics?: string[] }) {
  const res = await fetch(`${BASE}/admin/memory/episode/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data)
  });
  return res.json();
}

export async function deleteMemoryEpisode(id: string) {
  const res = await fetch(`${BASE}/admin/memory/episode/${id}`, { headers: authHeaders(), method: 'DELETE' });
  return res.json();
}

export async function getTools(): Promise<ToolItem[]> {
  const res = await fetch(`${BASE}/admin/tools`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function saveTools(enabled: string[]) {
  const res = await fetch(`${BASE}/admin/tools`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled })
  });
  return res.json();
}

// ===== MCP Server 管理 (admin) =====

export interface McpServerStatus {
  name: string;
  url: string;
  connected: boolean;
  toolCount: number;
  error: string | null;
  apiKey?: string;
}

export async function getMcpServers(): Promise<McpServerStatus[]> {
  const res = await fetch(`${BASE}/admin/mcp/servers`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export async function addMcpServer(name: string, url: string, apiKey?: string): Promise<string[]> {
  const res = await fetch(`${BASE}/admin/mcp/servers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, url, apiKey })
  });
  return res.json();
}

export async function testMcpServer(name: string, url: string, apiKey?: string): Promise<string[]> {
  const res = await fetch(`${BASE}/admin/mcp/servers/test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, url, apiKey })
  });
  return res.json();
}

export async function deleteMcpServer(name: string) {
  const res = await fetch(`${BASE}/admin/mcp/servers/${encodeURIComponent(name)}`, {
    method: 'DELETE'
  });
  return res.json();
}

/** 场景化提示词目录 — 每个 LLM 调用场景的元信息 + 默认提示词文本 + 默认超参 + 当前覆盖 */
export async function getPromptScenes() {
  const res = await fetch(`${BASE}/admin/config/prompt-scenes`, { headers: authHeaders() });
  const json = await res.json();
  return json.data ?? [];
}

export interface PromptSceneMeta {
  scene: string;
  label: string;
  description: string;
  slots: string[];
  defaultSystem: string;
  defaultUser: string;
  defaultTemperature: number | null;
  defaultMaxTokens: number | null;
  defaultTopP: number | null;
  current: {
    system: string | null;
    user: string | null;
    temperature: number | null;
    maxTokens: number | null;
    topP: number | null;
  } | null;
}

export interface TraceNode {
  nodeId: string;
  parentNodeId: string | null;
  depth: number;
  nodeName: string;
  nodeType?: string;
  status: string;
  durationMs: number;
  startTime?: string;
  errorMessage?: string | null;
  extraData?: string | null;
}

export interface TraceDetail {
  run: {
    traceId: string;
    status: string;
    durationMs: number;
    startTime?: string;
    errorMessage?: string | null;
    traceName?: string;
    extraData?: string | null;
  };
  nodes: TraceNode[];
}

/** LLM 调用输入输出详情（extraData 解析后） */
export interface LlmCallDetail {
  input?: { role: string; content: string }[];
  output?: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  thinking?: boolean;
}

export async function getTraceDetail(traceId: string): Promise<TraceDetail> {
  const res = await fetch(`${BASE}/rag/traces/runs/${traceId}`, { headers: authHeaders() });
  const json = await res.json();
  return json.data;
}

// ===== FollowUp (主动随访) =====

export async function bindEmail(email: string) {
  const res = await fetch(`${BASE}/user/email/bind?email=${encodeURIComponent(email)}`, { headers: authHeaders(), method: 'POST' });
  return res.json();
}

export async function verifyEmail(email: string, code: string) {
  const res = await fetch(
    `${BASE}/user/email/verify?email=${encodeURIComponent(email)}&code=${encodeURIComponent(code)}`,
    { method: 'POST' }
  );
  return res.json();
}

export async function getFollowup(id: string) {
  const res = await fetch(`${BASE}/followup/${id}`);
  return res.json();
}

export async function markFollowupAnswered(id: string) {
  await fetch(`${BASE}/followup/${id}/answered`, { method: 'POST' });
}
