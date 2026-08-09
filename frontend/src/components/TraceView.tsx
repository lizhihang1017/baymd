import { useState, useEffect } from 'react'
import { ChevronLeft, ChevronRight, Loader2, Activity, ChevronDown } from 'lucide-react'
import { getTraces, getTraceDetail } from '../api/baymd'
import type { TraceDetail, TraceNode, LlmCallDetail } from '../api/baymd'

/** 解析 extraData JSON（容错） */
function parseExtra(raw?: string | null): any {
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}

/** 链路追踪 — 每条 trace 的列表 + 瀑布图 + 节点详情 */
export default function TraceView() {
  const [traces, setTraces] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [detail, setDetail] = useState<TraceDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const load = async (p: number) => {
    setLoading(true)
    try {
      const res = await getTraces(p, pageSize)
      setTraces(res.data?.records || [])
      setTotal(res.data?.total || 0)
    } catch { /* 忽略 */ } finally { setLoading(false) }
  }

  useEffect(() => { load(1) }, [])

  const openDetail = async (traceId: string) => {
    setDetailLoading(true)
    try {
      setDetail(await getTraceDetail(traceId))
    } catch { /* 忽略 */ } finally { setDetailLoading(false) }
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">链路追踪</h1>
        <span className="ml-3 text-[11px] text-muted">每次问答的完整调用链路 · 节点耗时 / 状态 / 错误</span>
      </header>

      <div className="flex-1 min-h-0 flex overflow-hidden">
        {/* 左侧 trace 列表 */}
        <div className="w-80 shrink-0 border-r border-border flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {loading ? (
              <div className="flex items-center gap-2 text-xs text-muted p-2"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
            ) : traces.length === 0 ? (
              <p className="text-xs text-muted p-2">暂无链路数据</p>
            ) : traces.map(t => (
              <button key={t.traceId} onClick={() => openDetail(t.traceId)}
                className={`w-full text-left px-3 py-2 rounded-lg border transition-colors ${
                  detail?.run.traceId === t.traceId
                    ? 'border-accent bg-accent/10'
                    : 'border-transparent hover:bg-accent/5'
                }`}>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] text-muted">{t.traceId?.slice(0, 12)}</span>
                  <span className="flex items-center gap-2 text-[11px]">
                    <span className="text-muted">{(t.durationMs ?? 0) >= 1000 ? `${(t.durationMs / 1000).toFixed(2)}s` : `${t.durationMs ?? 0}ms`}</span>
                    <span className={`${t.status === 'SUCCESS' ? 'text-accent' : 'text-vital'}`}>{t.status}</span>
                  </span>
                </div>
                <div className="text-[10px] text-muted mt-0.5">
                  {t.traceName || 'rag-stream-chat'} · {t.startTime ? new Date(t.startTime).toLocaleString() : ''}
                </div>
              </button>
            ))}
          </div>
          {/* 分页 */}
          <div className="border-t border-border flex items-center justify-between px-3 py-2 shrink-0">
            <span className="text-[11px] text-muted">共 {total} 条</span>
            <div className="flex items-center gap-1">
              <button onClick={() => { const p = page - 1; setPage(p); load(p) }} disabled={page <= 1}
                className="p-1 rounded text-muted hover:text-text-primary disabled:opacity-30"><ChevronLeft className="w-3.5 h-3.5" /></button>
              <span className="text-[11px] text-muted">{page}/{totalPages}</span>
              <button onClick={() => { const p = page + 1; setPage(p); load(p) }} disabled={page >= totalPages}
                className="p-1 rounded text-muted hover:text-text-primary disabled:opacity-30"><ChevronRight className="w-3.5 h-3.5" /></button>
            </div>
          </div>
        </div>

        {/* 右侧详情 */}
        <div className="flex-1 min-h-0 overflow-y-auto p-5">
          {detailLoading ? (
            <div className="flex items-center gap-2 text-xs text-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载详情...</div>
          ) : detail ? (
            <TraceDetailPanel detail={detail} onBack={() => setDetail(null)} />
          ) : (
            <div className="flex flex-col items-center justify-center h-full text-muted">
              <Activity className="w-12 h-12 mb-3" />
              <p className="text-sm">选择左侧一条 trace 查看详情</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/** trace 详情: 头部 + 用户问题/回答 + 瀑布图 + 节点明细(LLM 输入输出) */
function TraceDetailPanel({ detail, onBack }: { detail: TraceDetail; onBack: () => void }) {
  const { run, nodes } = detail
  const maxDur = Math.max(run.durationMs || 0, ...nodes.map(n => n.durationMs || 0), 1)
  const fmt = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`)
  const runExtra = parseExtra(run.extraData)
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  return (
    <div className="max-w-3xl">
      <div className="flex items-center gap-2 mb-3">
        <button onClick={onBack} className="p-1 rounded-md text-muted hover:text-text-primary hover:bg-accent/5"><ChevronLeft className="w-4 h-4" /></button>
        <div>
          <div className="flex items-center gap-2 text-sm">
            <span className="font-mono text-muted text-xs">{run.traceId}</span>
            <span className={`text-xs font-semibold ${run.status === 'SUCCESS' ? 'text-accent' : 'text-vital'}`}>{run.status}</span>
            <span className="text-xs text-muted">{fmt(run.durationMs || 0)}</span>
          </div>
          <div className="text-[11px] text-muted">
            {run.traceName || 'rag-stream-chat'} · {run.startTime ? new Date(run.startTime).toLocaleString() : ''}
          </div>
        </div>
      </div>

      {run.errorMessage && (
        <div className="bg-vital/5 border border-vital/30 rounded-lg px-3 py-2 mb-3">
          <span className="text-[11px] text-vital">错误: {run.errorMessage}</span>
        </div>
      )}

      {/* 用户问题 + 最终回答 */}
      {runExtra && (runExtra.question || runExtra.answer) && (
        <div className="bg-panel border border-border rounded-xl p-4 mb-4">
          <h3 className="text-xs font-semibold text-muted mb-2">用户问题 / 最终回答</h3>
          <div className="space-y-2">
            {runExtra.question && (
              <div>
                <div className="text-[10px] text-muted mb-0.5">Q（用户输入）</div>
                <div className="text-xs bg-white border border-border/60 rounded-lg px-3 py-2 whitespace-pre-wrap">{runExtra.question}</div>
              </div>
            )}
            {runExtra.answer && (
              <div>
                <div className="text-[10px] text-muted mb-0.5">A（最终回答）</div>
                <div className="text-xs bg-white border border-border/60 rounded-lg px-3 py-2 whitespace-pre-wrap max-h-64 overflow-y-auto">{runExtra.answer}</div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 瀑布图 */}
      <div className="bg-panel border border-border rounded-xl p-4 mb-4 overflow-x-auto">
        <h3 className="text-xs font-semibold text-muted mb-3">调用瀑布图</h3>
        <div className="space-y-0.5 min-w-[480px]">
          {nodes.map((n, i) => (
            <TraceBar key={n.nodeId || i} node={n} maxDur={maxDur} fmt={fmt} />
          ))}
        </div>
      </div>

      {/* 节点明细 */}
      <div className="bg-panel border border-border rounded-xl p-4">
        <h3 className="text-xs font-semibold text-muted mb-3">节点明细（{nodes.length}）</h3>
        <div className="space-y-1">
          {nodes.map((n, i) => {
            const extra = parseExtra(n.extraData)
            const isLlm = (n.nodeType === 'LLM_ROUTING' || n.nodeType === 'LLM_PROVIDER') && extra
            const key = n.nodeId || String(i)
            const open = !!expanded[key]
            return (
              <div key={key} className="rounded-lg border border-border/60 bg-white overflow-hidden">
                <div className="flex items-start justify-between px-3 py-2">
                  <div className="min-w-0">
                    <div className="text-xs text-text-primary" style={{ paddingLeft: n.depth * 12 }}>
                      {n.nodeName}
                    </div>
                    {n.errorMessage && <div className="text-[11px] text-vital mt-0.5" style={{ paddingLeft: n.depth * 12 }}>{n.errorMessage}</div>}
                  </div>
                  <div className="flex items-center gap-2 shrink-0 ml-2">
                    <span className="text-[10px] text-muted font-mono">{n.nodeType || ''}</span>
                    <span className="text-[11px] text-muted">{fmt(n.durationMs || 0)}</span>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded ${n.status === 'SUCCESS' ? 'bg-accent/10 text-accent' : 'bg-vital/10 text-vital'}`}>{n.status}</span>
                    {isLlm && (
                      <button onClick={() => setExpanded(e => ({ ...e, [key]: !open }))}
                        className="flex items-center gap-0.5 text-[10px] text-accent hover:underline">
                        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
                        {open ? '收起' : '查看输入/输出'}
                      </button>
                    )}
                  </div>
                </div>
                {isLlm && open && <LlmCallDetailView detail={extra as LlmCallDetail} />}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/** LLM 调用详情: 完整输入 prompt（按角色分块）+ 输出 */
function LlmCallDetailView({ detail }: { detail: LlmCallDetail }) {
  return (
    <div className="px-3 pb-3 border-t border-border/60 bg-surface/40">
      {/* 输入 messages */}
      {detail.input && detail.input.length > 0 && (
        <div className="mt-2">
          <div className="text-[10px] text-muted mb-1">输入（完整 Prompt,{detail.input.length} 条消息）</div>
          <div className="space-y-1.5">
            {detail.input.map((m, i) => (
              <div key={i} className="bg-white border border-border/60 rounded-lg overflow-hidden">
                <div className="px-2 py-0.5 text-[10px] font-medium"
                  style={{ color: m.role === 'system' ? '#6366f1' : m.role === 'assistant' ? '#0ea5e9' : '#10b981' }}>
                  {m.role}
                </div>
                <div className="px-2 pb-1.5 text-[11px] font-mono whitespace-pre-wrap max-h-56 overflow-y-auto">{m.content}</div>
              </div>
            ))}
          </div>
        </div>
      )}
      {/* 超参 */}
      {(detail.temperature !== undefined || detail.maxTokens !== undefined || detail.topP !== undefined) && (
        <div className="flex gap-3 mt-2 text-[10px] text-muted">
          {detail.temperature !== undefined && <span>temperature: {detail.temperature}</span>}
          {detail.maxTokens !== undefined && <span>maxTokens: {detail.maxTokens}</span>}
          {detail.topP !== undefined && <span>topP: {detail.topP}</span>}
          {detail.thinking !== undefined && <span>thinking: {String(detail.thinking)}</span>}
        </div>
      )}
      {/* 输出 */}
      <div className="mt-2">
        <div className="text-[10px] text-muted mb-1">输出</div>
        <div className="bg-white border border-border/60 rounded-lg px-2 py-1.5 text-[11px] font-mono whitespace-pre-wrap max-h-56 overflow-y-auto">
          {detail.output || '（空）'}
        </div>
      </div>
    </div>
  )
}

function TraceBar({ node, maxDur, fmt }: { node: TraceNode; maxDur: number; fmt: (ms: number) => string }) {
  const err = node.status !== 'SUCCESS'
  const pct = Math.max((node.durationMs || 0) / maxDur * 100, 0.5)
  return (
    <div className="flex items-center gap-2 text-xs">
      <div className="w-44 shrink-0 flex items-center justify-end gap-1.5" style={{ paddingRight: node.depth * 10 }}>
        <span className={`truncate ${err ? 'text-vital' : 'text-text-primary'}`} title={node.nodeName}>{node.nodeName}</span>
        <span className="text-[10px] text-muted shrink-0">{fmt(node.durationMs || 0)}</span>
      </div>
      <div className="h-4 flex-1 bg-accent/5 rounded-sm relative">
        <div className={`h-full rounded-sm ${err ? 'bg-vital' : 'bg-accent/70'}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
