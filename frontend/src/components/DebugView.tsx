import { authHeaders } from '../api/baymd'
import { useState } from 'react'
import { Loader2, Bug, ChevronDown, ChevronRight } from 'lucide-react'

interface DebugResult {
  question: string;
  rewrittenQuestion?: string;
  subQuestions?: string[];
  intents?: { subQuestion: string; scores: { id: string; name: string; path?: string; kind?: string; score: number }[] }[];
  executor?: string;
  retrieval?: {
    kbContextLength?: number; mcpContextLength?: number; omittedEvidenceCount?: number;
    kbContextSnippet?: string; mcpContextSnippet?: string; error?: string;
  };
}

/** RAG 调试面板 — 输入问题,查看完整链路: 改写 → 意图 → 执行器 → 检索 */
export default function DebugView() {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<DebugResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState('')
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  const run = async () => {
    if (!question.trim()) { setMsg('请输入问题'); return }
    setLoading(true)
    setMsg('')
    try {
      const res = await fetch('/api/baymd/admin/rag/debug', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ question: question.trim() })
      })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setResult(json.data)
    } catch (e: any) {
      setMsg(`调试失败: ${e?.message || ''}`)
    } finally { setLoading(false) }
  }

  const toggle = (k: string) => setExpanded(e => ({ ...e, [k]: !e[k] }))

  const execColor = (ex?: string) => {
    if (!ex) return 'text-muted'
    if (ex.startsWith('RAG')) return 'text-accent'
    if (ex.startsWith('AGENT')) return 'text-amber-600'
    if (ex.startsWith('SYSTEM')) return 'text-vital'
    return 'text-muted'
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="w-1 h-6 rounded-full bg-accent mr-3" />
        <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">RAG 调试</h1>
      </header>
      <div className="flex-1 min-h-0 overflow-y-auto p-5">
        <div className="max-w-2xl">
          {/* 输入 */}
          <div className="flex gap-2 mb-4">
            <input value={question} onChange={e => setQuestion(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && run()}
              placeholder="输入要调试的问题，如: 感冒了吃什么药"
              className="flex-1 px-3 py-2 rounded-lg border border-border bg-white text-sm focus:outline-none focus:border-accent" />
            <button onClick={run} disabled={loading}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-accent text-white text-sm hover:opacity-90 disabled:opacity-50">
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Bug className="w-4 h-4" />}
              调试
            </button>
          </div>
          {msg && <p className="text-[11px] text-vital mb-3">{msg}</p>}

          {result && (
            <div className="space-y-4">
              {/* 1. 改写 */}
              <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
                <button onClick={() => toggle('rewrite')} className="flex items-center gap-1.5 w-full">
                  {expanded.rewrite ? <ChevronDown className="w-4 h-4 text-accent" /> : <ChevronRight className="w-4 h-4 text-accent" />}
                  <h2 className="text-sm font-semibold">① 查询改写</h2>
                </button>
                {expanded.rewrite !== false && (
                  <div className="mt-2 space-y-1.5 text-xs">
                    <div><span className="text-muted">改写后: </span><span className="text-text-primary">{result.rewrittenQuestion}</span></div>
                    {result.subQuestions && result.subQuestions.length > 0 && (
                      <div>
                        <span className="text-muted">子问题: </span>
                        {result.subQuestions.map((s, i) => <div key={i} className="text-text-primary pl-4">· {s}</div>)}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* 2. 意图 */}
              <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
                <button onClick={() => toggle('intent')} className="flex items-center gap-1.5 w-full">
                  {expanded.intent ? <ChevronDown className="w-4 h-4 text-accent" /> : <ChevronRight className="w-4 h-4 text-accent" />}
                  <h2 className="text-sm font-semibold">② 意图分类</h2>
                </button>
                {expanded.intent !== false && result.intents && result.intents.length > 0 && (
                  <div className="mt-2 space-y-2">
                    {result.intents.map((si, i) => (
                      <div key={i} className="bg-white border border-border/60 rounded-lg p-2">
                        <div className="text-[11px] text-muted mb-1">子问题: {si.subQuestion}</div>
                        <div className="space-y-0.5">
                          {si.scores.map(s => (
                            <div key={s.id} className="flex items-center justify-between text-[11px]">
                              <span className="flex items-center gap-1.5 min-w-0">
                                <span className={`shrink-0 px-1 py-0.5 rounded text-[9px] ${
                                  s.kind === 'SYSTEM' ? 'bg-vital/10 text-vital' : s.kind === 'MCP' ? 'bg-amber-500/10 text-amber-600' : 'bg-accent/10 text-accent'
                                }`}>{s.kind}</span>
                                <span className="text-text-primary truncate">{s.name}</span>
                                <span className="text-muted/60 font-mono truncate hidden sm:inline">{s.id}</span>
                              </span>
                              <span className="text-muted font-mono">{s.score.toFixed(2)}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* 3. 执行器 */}
              <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
                <div className="flex items-center gap-1.5">
                  <Bug className="w-4 h-4 text-accent" />
                  <h2 className="text-sm font-semibold">③ 执行器判定</h2>
                  <span className={`ml-2 text-xs font-semibold ${execColor(result.executor)}`}>{result.executor}</span>
                </div>
              </div>

              {/* 4. 检索 */}
              <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
                <button onClick={() => toggle('retrieval')} className="flex items-center gap-1.5 w-full">
                  {expanded.retrieval ? <ChevronDown className="w-4 h-4 text-accent" /> : <ChevronRight className="w-4 h-4 text-accent" />}
                  <h2 className="text-sm font-semibold">④ 检索证据</h2>
                </button>
                {expanded.retrieval !== false && result.retrieval && (
                  <div className="mt-2 space-y-2 text-xs">
                    {result.retrieval.error ? (
                      <p className="text-vital">{result.retrieval.error}</p>
                    ) : (
                      <>
                        <div className="flex gap-4 text-[11px] text-muted">
                          <span>KB 证据: {result.retrieval.kbContextLength ?? 0} 字符</span>
                          <span>MCP 证据: {result.retrieval.mcpContextLength ?? 0} 字符</span>
                          <span>截断: {result.retrieval.omittedEvidenceCount ?? 0} 条</span>
                        </div>
                        {result.retrieval.kbContextSnippet && (
                          <div>
                            <div className="text-[10px] text-muted mb-0.5">KB 证据片段</div>
                            <div className="bg-white border border-border/60 rounded-lg px-2 py-1.5 font-mono text-[11px] whitespace-pre-wrap max-h-48 overflow-y-auto">{result.retrieval.kbContextSnippet}</div>
                          </div>
                        )}
                        {result.retrieval.mcpContextSnippet && (
                          <div>
                            <div className="text-[10px] text-muted mb-0.5">MCP 证据片段</div>
                            <div className="bg-white border border-border/60 rounded-lg px-2 py-1.5 font-mono text-[11px] whitespace-pre-wrap max-h-48 overflow-y-auto">{result.retrieval.mcpContextSnippet}</div>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
