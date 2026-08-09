const BASE = '/api/baymd';

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

export async function getCurrentUser(): Promise<CurrentUser> {
  const res = await fetch(`${BASE}/user/me`);
  const json = await res.json();
  return json.data;
}

// ===== Conversations =====

export async function listConversations(): Promise<Conversation[]> {
  const res = await fetch(`${BASE}/conversations`);
  const json = await res.json();
  return json.data || [];
}

export async function getMessages(conversationId: string): Promise<Message[]> {
  const res = await fetch(`${BASE}/conversations/${conversationId}/messages`);
  const json = await res.json();
  return json.data || [];
}

export async function exportConversation(conversationId: string) {
  const res = await fetch(`${BASE}/conversations/${conversationId}/export`);
  return res.json();
}

export async function deleteConversation(conversationId: string) {
  await fetch(`${BASE}/conversations/${conversationId}`, { method: 'DELETE' });
}

export async function clearMemory() {
  const res = await fetch(`${BASE}/memory`, { method: 'DELETE' });
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
  const res = await fetch(`${BASE}/rag/report/upload`, { method: 'POST', body: form });
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

// ===== Trace =====

export async function getTraces(page: number = 1, size: number = 10) {
  const res = await fetch(`${BASE}/rag/traces/runs?page=${page}&size=${size}`);
  return res.json();
}

// ===== Runtime Config (admin) =====

export async function getConfig(section: string): Promise<string | null> {
  const res = await fetch(`${BASE}/admin/config/${section}`);
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

export async function getTools(): Promise<ToolItem[]> {
  const res = await fetch(`${BASE}/admin/tools`);
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
  const res = await fetch(`${BASE}/admin/mcp/servers`);
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
  const res = await fetch(`${BASE}/admin/config/prompt-scenes`);
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
}

export interface TraceDetail {
  run: {
    traceId: string;
    status: string;
    durationMs: number;
    startTime?: string;
    errorMessage?: string | null;
    traceName?: string;
  };
  nodes: TraceNode[];
}

export async function getTraceDetail(traceId: string): Promise<TraceDetail> {
  const res = await fetch(`${BASE}/rag/traces/runs/${traceId}`);
  const json = await res.json();
  return json.data;
}

// ===== FollowUp (主动随访) =====

export async function bindEmail(email: string) {
  const res = await fetch(`${BASE}/user/email/bind?email=${encodeURIComponent(email)}`, { method: 'POST' });
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
