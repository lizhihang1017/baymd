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
