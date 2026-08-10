import { useState, useEffect, useRef, useCallback } from 'react'
import { Send, Brain, ThumbsUp, ThumbsDown, StopCircle, Paperclip, X, FileText } from 'lucide-react'

import { streamChat, getMessages, submitFeedback, uploadReport, getFollowup, markFollowupAnswered, Message } from '../api/baymd'
import Markdown from './Markdown'

interface Props {
  conversationId: string | null
  deepThinking: boolean
  onToggleDeepThinking: () => void
  onMessageSent?: () => void
  onConversationCreated?: (id: string) => void
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  messageId?: string
  emergency?: boolean
  citations?: { index: number; id: string; snippet: string }[]
  followUpQuestions?: string[]
}

export default function ChatView({ conversationId, deepThinking, onToggleDeepThinking, onMessageSent, onConversationCreated }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState('')
  const [thinking, setThinking] = useState('')
  const [reportId, setReportId] = useState<string | null>(null)
  const [reportName, setReportName] = useState('')
  const [uploading, setUploading] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const pendingFollowupRef = useRef<string | null>(null)
  const convCreatedRef = useRef<string | null>(null) // meta 刚创建的会话 id(跳过历史拉取)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, streaming, thinking])

  // 深链：?followupId=xxx → 预填随访问题
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const followupId = params.get('followupId')
    if (!followupId) return
    getFollowup(followupId).then(res => {
      const task = res?.data
      if (task?.question) {
        setInput(task.question)
        pendingFollowupRef.current = followupId
        window.history.replaceState({}, '', window.location.pathname)
      }
    }).catch(() => {})
  }, [])

  // Load history when conversation changes
  useEffect(() => {
    if (!conversationId) { setMessages([]); return }
    // 刚由 SSE meta 创建的会话: 本地已有消息,跳过历史拉取避免清空
    if (convCreatedRef.current === conversationId) {
      convCreatedRef.current = null
      return
    }
    getMessages(conversationId).then(msgs =>
      setMessages(msgs.map(m => ({
        id: m.id,
        role: m.role,
        content: m.content,
        thinking: m.thinkingContent,
        messageId: m.id
      })))
    ).catch(() => {})
  }, [conversationId])

  const doSend = useCallback((rawQuestion: string) => {
    if (!rawQuestion.trim() || loading) return
    const question = rawQuestion.trim()
    setInput('')
    setLoading(true)
    setStreaming('')
    setThinking('')

    const userMsg: ChatMessage = { id: Date.now().toString(), role: 'user', content: question }
    setMessages(prev => [...prev, userMsg])

    let content = ''
    let think = ''
    let metaReceived = false

    abortRef.current = streamChat(
      question, conversationId, deepThinking,
      (meta) => {
        metaReceived = true
        // 首次消息: 后端创建会话并返回 id,保存供后续轮次复用
        // 标记刚创建,避免 useEffect 拉历史清空本地消息
        if (!conversationId) convCreatedRef.current = meta.conversationId
        onConversationCreated?.(meta.conversationId)
      },
      (type, delta) => {
        if (type === 'think') { think += delta; setThinking(think) }
        else { content += delta; setStreaming(content) }
      },
      (payload) => {
        setLoading(false)
        onMessageSent?.()
        const assistantMsg: ChatMessage = {
          id: payload.messageId || Date.now().toString(),
          role: 'assistant',
          content: content || '',
          thinking: think || undefined,
          messageId: payload.messageId,
          emergency: payload.emergency === true,
          citations: payload.citations,
          followUpQuestions: payload.followUpQuestions
        }
        setMessages(prev => [...prev, assistantMsg])
        setStreaming('')
        setThinking('')
      },
      (err) => {
        setLoading(false)
        setStreaming('')
        setMessages(prev => [...prev, {
          id: Date.now().toString(), role: 'assistant',
          content: `请求失败: ${err.message}`
        }])
      },
      reportId
    )
    // 随访深链：用户回答后标记已答
    if (pendingFollowupRef.current) {
      const fid = pendingFollowupRef.current
      pendingFollowupRef.current = null
      markFollowupAnswered(fid).catch(() => {})
    }
  }, [loading, conversationId, deepThinking, reportId])

  const handleSend = useCallback(() => {
    doSend(input.trim())
  }, [input, loading, doSend])

  const handleUpload = useCallback(async (file: File) => {
    setUploading(true)
    try {
      const report = await uploadReport(file)
      setReportId(report.id)
      setReportName(report.fileName)
    } catch (e: any) {
      setMessages(prev => [...prev, {
        id: Date.now().toString(), role: 'assistant',
        content: `报告上传失败: ${e?.message || '未知错误'}`
      }])
    } finally {
      setUploading(false)
    }
  }, [])

  const handleStop = () => {
    abortRef.current?.abort()
    setLoading(false)
  }

  const handleFeedback = async (msgId: string | undefined, vote: 1 | -1) => {
    if (!msgId) return
    await submitFeedback(msgId, vote)
  }

  // ===== 大白跟随鼠标 =====
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })
  const [hovering, setHovering] = useState(false)
  const homeRef = useRef<HTMLDivElement>(null)

  const handleMouseMove = (e: React.MouseEvent) => {
    const rect = homeRef.current?.getBoundingClientRect()
    if (!rect) return
    const x = ((e.clientX - rect.left) / rect.width - 0.5) * 2
    const y = ((e.clientY - rect.top) / rect.height - 0.5) * 2
    setMousePos({ x, y })
  }

  return (
    <div className="flex-1 flex flex-col bg-u-bg">
      {/* Messages — 居中窄栏 */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
          {messages.length === 0 && (
            <div ref={homeRef}
              onMouseMove={handleMouseMove}
              onMouseEnter={() => setHovering(true)}
              onMouseLeave={() => { setHovering(false); setMousePos({ x: 0, y: 0 }) }}
              className="flex flex-col items-center justify-center h-[70vh] select-none">
              <div className="relative mb-5" style={{
                transform: hovering
                  ? `translate(${mousePos.x * 10}px, ${mousePos.y * 8}px) rotate(${mousePos.x * 3}deg)`
                  : 'translate(0,0) rotate(0deg)',
                transition: 'transform 0.25s cubic-bezier(0.34,1.56,0.64,1)',
                willChange: 'transform',
              }}>
                {/* 变身动画容器: 切换时旋转+缩放 */}
                <div key={deepThinking ? 'armor' : 'normal'}
                  className={`relative ${deepThinking ? 'baymax-transform' : ''}`}>
                  <img
                    src={deepThinking ? '/baymax-side.webp' : '/baymax.png'}
                    alt={deepThinking ? 'BayMD 深度思考模式(装甲大白)' : 'BayMD 健康管家'}
                    draggable={false}
                    className={`w-28 h-32 object-contain relative z-10 animate-clinical-pulse ${deepThinking ? 'drop-shadow-[0_0_18px_rgba(220,38,38,0.5)]' : ''}`}
                  />
                  {/* 盔甲模式下红色能量光环 */}
                  {deepThinking && (
                    <div className="absolute inset-0 -m-3 rounded-full bg-vital/15 blur-xl pointer-events-none" aria-hidden />
                  )}
                </div>
              </div>
              <h1 className="text-xl font-semibold text-u-text">
                BayMD 健康管家
                {deepThinking && <span className="ml-2 text-xs font-normal text-vital">· 深度思考模式</span>}
              </h1>
              <p className="text-sm text-u-muted mt-1.5">
                {loading ? (
                  <span className="flex items-center justify-center gap-1.5">
                    正在思考
                    <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0s' }} />
                    <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.15s' }} />
                    <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.3s' }} />
                  </span>
                ) : deepThinking ? '装甲已部署,将以更深入的推理为您解答' : '您的 24 小时健康助手'}
              </p>
            </div>
          )}

          {messages.map(msg => (
            <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[75%] px-4 py-3 leading-relaxed ${
                msg.role === 'user'
                  ? 'bg-u-user-bubble rounded-2xl rounded-br-md'
                  : msg.emergency
                    ? 'bg-white border-2 border-vital/70 rounded-2xl'
                    : 'bg-u-assistant-bubble border border-u-border rounded-2xl rounded-bl-md'
              }`}>
                {msg.emergency && (
                  <div className="flex items-center gap-1.5 mb-2 text-vital">
                    <span className="inline-block w-2 h-2 rounded-full bg-vital animate-clinical-pulse" />
                    <span className="text-xs font-semibold">紧急 · 请立即就医</span>
                  </div>
                )}
                {msg.thinking && (
                  <details className="mb-2">
                    <summary className="text-xs text-u-muted cursor-pointer">思考过程</summary>
                    <p className="text-xs text-u-muted mt-1 whitespace-pre-wrap">{msg.thinking}</p>
                  </details>
                )}
                <Markdown content={msg.content} onDark={msg.role === 'user'} />
                {msg.role === 'assistant' && msg.messageId && (
                  <div className="flex gap-1 mt-2 pt-2 border-t border-u-border">
                    <button onClick={() => handleFeedback(msg.messageId, 1)}
                      className="p-1 text-u-muted hover:text-u-amber transition-colors">
                      <ThumbsUp className="w-3.5 h-3.5" />
                    </button>
                    <button onClick={() => handleFeedback(msg.messageId, -1)}
                      className="p-1 text-u-muted hover:text-vital transition-colors">
                      <ThumbsDown className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
                {(msg.citations?.length || 0) > 0 && (
                  <details className="mt-2">
                    <summary className="text-xs text-u-muted cursor-pointer hover:text-u-text">
                      参考来源（{msg.citations!.length}）
                    </summary>
                    <ul className="text-xs text-u-muted mt-1 space-y-1">
                      {msg.citations!.map((c, i) => (
                        <li key={i} className="truncate" title={c.snippet || c.id}>
                          <span className="text-u-amber">[{c.index}]</span> {c.snippet || c.id}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
                {(msg.followUpQuestions?.length || 0) > 0 && (
                  <div className="mt-2 pt-2 border-t border-u-border space-y-1">
                    {msg.followUpQuestions!.map((q, i) => (
                      <button key={i} onClick={() => doSend(q)}
                        className="block w-full text-left text-xs text-u-amber hover:bg-u-amber/5
                          rounded-md px-2 py-1.5 transition-colors">
                        ↳ {q}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          {/* 等待答案: loading 且尚无流式内容时,显示大白思考气泡 */}
          {loading && !streaming && !thinking && (
            <div className="flex justify-start animate-fade-in">
              <div className="flex items-center gap-2.5 px-4 py-3 bg-u-assistant-bubble border border-u-border rounded-2xl rounded-bl-md">
                <img src={deepThinking ? '/baymax-side.webp' : '/baymax.png'} alt="思考中"
                  className={`w-7 h-8 object-contain ${deepThinking ? 'animate-clinical-pulse drop-shadow-[0_0_8px_rgba(220,38,38,0.4)]' : 'animate-clinical-pulse'}`} />
                <span className="text-xs text-u-muted flex items-center gap-1.5">
                  正在思考
                  <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0s' }} />
                  <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.15s' }} />
                  <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.3s' }} />
                </span>
              </div>
            </div>
          )}

          {/* Streaming */}
          {(streaming || thinking) && (
            <div className="flex justify-start">
              <div className="max-w-[75%] px-4 py-3 bg-u-assistant-bubble border border-u-border rounded-2xl rounded-bl-md">
                {thinking && (
                  <div className="mb-2 animate-fade-in">
                    <p className="text-xs text-u-amber font-medium mb-1 flex items-center gap-1.5">
                      <span className="inline-block w-2 h-2 rounded-full bg-u-amber animate-clinical-pulse" />
                      思考中
                      <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0s' }} />
                      <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.15s' }} />
                      <span className="thinking-dot bg-u-amber" style={{ animationDelay: '0.3s' }} />
                    </p>
                    <p className="text-xs text-u-muted whitespace-pre-wrap">{thinking}</p>
                  </div>
                )}
                {streaming && (
                  <div className="animate-fade-in">
                    <Markdown content={streaming} />
                    <span className="inline-block w-1.5 h-4 bg-u-muted ml-0.5 animate-clinical-pulse" />
                  </div>
                )}
              </div>
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* Input — ChatGPT 居中圆润输入框 */}
      <div className="px-4 pb-4 pt-2">
        <div className="max-w-3xl mx-auto">
          {reportId && (
            <div className="flex items-center gap-2 mb-2 px-3 py-1.5 rounded-full bg-white border border-u-border text-xs">
              <FileText className="w-3.5 h-3.5 text-u-amber shrink-0" />
              <span className="text-u-text truncate">已附加报告：{reportName}</span>
              <button onClick={() => { setReportId(null); setReportName('') }}
                className="ml-auto p-0.5 text-u-muted hover:text-vital transition-colors shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          <div className="flex items-end gap-2 bg-u-input border border-u-border rounded-[26px] px-3 py-2.5
            shadow-[0_4px_16px_rgba(0,0,0,0.06)] focus-within:border-u-muted transition-colors">
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={loading || uploading}
              title="上传化验单/检查报告"
              className="p-1.5 rounded-full text-u-muted hover:text-u-text transition-colors disabled:opacity-40 shrink-0">
              <Paperclip className="w-5 h-5" />
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,application/pdf"
              className="hidden"
              onChange={e => {
                const f = e.target.files?.[0]
                if (f) handleUpload(f)
                e.target.value = ''
              }}
            />
            <input
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !e.shiftKey && handleSend()}
              placeholder={uploading ? '报告上传中...' : '输入医疗问题...'}
              disabled={loading}
              className="flex-1 bg-transparent text-sm text-u-text placeholder:text-u-muted
                focus:outline-none disabled:opacity-50 px-1 py-0.5"
            />
            {/* 深度思考 */}
            <button
              onClick={onToggleDeepThinking}
              title="深度思考模式: 更慢但更深入"
              className={`flex items-center gap-1 px-2.5 py-1.5 rounded-full text-xs font-medium transition-colors shrink-0 ${
                deepThinking ? 'bg-u-amber/10 text-u-amber' : 'text-u-muted hover:text-u-text'
              }`}>
              <Brain className="w-4 h-4" />
              {deepThinking ? '深度思考' : '标准'}
            </button>
            {loading ? (
              <button onClick={handleStop}
                className="p-2 rounded-full bg-u-send text-white hover:bg-vital transition-colors shrink-0">
                <StopCircle className="w-5 h-5" />
              </button>
            ) : (
              <button onClick={handleSend} disabled={!input.trim()}
                className="p-2 rounded-full bg-u-send text-white hover:bg-u-send-hover
                  disabled:opacity-30 disabled:cursor-not-allowed transition-colors shrink-0">
                <Send className="w-4.5 h-4.5 w-5 h-5" />
              </button>
            )}
          </div>

          {/* 深度思考说明 */}
          <p className="text-center text-[10px] text-u-muted mt-2">BayMD 健康管家 · 回答仅供健康参考,紧急情况请及时就医</p>
        </div>
      </div>
    </div>
  )
}
