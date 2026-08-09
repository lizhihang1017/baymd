import { useState, useEffect } from 'react'
import { Settings, Trash2, Shield, Activity, ChevronLeft, Loader2, SlidersHorizontal } from 'lucide-react'
import { getSettings, clearMemory, getTraces, getTraceDetail } from '../api/baymd'
import { AiConfigForm, RagConfigForm } from './ConfigForms'
import type { TraceDetail, TraceNode } from '../api/baymd'

export default function SettingsView() {
  const [settings, setSettings] = useState<any>(null)
  const [traces, setTraces] = useState<any[]>([])
  const [memoryCleared, setMemoryCleared] = useState(false)
  const [selectedTrace, setSelectedTrace] = useState<TraceDetail | null>(null)
  const [traceLoading, setTraceLoading] = useState(false)

  const handleTraceClick = async (traceId: string) => {
    setTraceLoading(true)
    try {
      const detail = await getTraceDetail(traceId)
      setSelectedTrace(detail)
    } catch { /* ignore */ } finally {
      setTraceLoading(false)
    }
  }

  useEffect(() => {
    getSettings().then(res => setSettings(res.data)).catch(() => {})
    getTraces(1, 5).then(res => setTraces(res.data?.records || [])).catch(() => {})
  }, [])

  const handleClearMemory = async () => {
    try {
      await clearMemory()
      setMemoryCleared(true)
      setTimeout(() => setMemoryCleared(false), 2000)
    } catch {}
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">系统设置</h1>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
        {/* RAG Config */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Settings className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">RAG 配置</h2>
          </div>
          {settings?.rag ? (
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="text-muted">向量引擎</div>
              <div>{settings.rag.vector?.type || 'pg'}</div>
              <div className="text-muted">向量维度</div>
              <div>{settings.rag.default?.dimension || 1536}</div>
              <div className="text-muted">查询改写</div>
              <div>{settings.rag.queryRewrite?.enabled ? '开启' : '关闭'}</div>
              <div className="text-muted">限流</div>
              <div>{settings.rag.rateLimit?.global?.enabled ? '开启' : '关闭'}</div>
            </div>
          ) : (
            <p className="text-xs text-muted">加载中...</p>
          )}
        </section>

        {/* Memory */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-vital" />
            <h2 className="text-sm font-semibold">隐私与记忆</h2>
          </div>
          <button onClick={handleClearMemory}
            className="flex items-center gap-2 px-3 py-2 rounded-lg
              border border-vital text-vital text-xs hover:bg-vital/5 transition-colors">
            <Trash2 className="w-3.5 h-3.5" />
            {memoryCleared ? '记忆已清空' : '清空所有用户记忆'}
          </button>
          <p className="text-xs text-muted mt-2">
            清空后，系统将忘记所有关于您的事实和对话情节
          </p>
        </section>

        {/* Runtime Config */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-1">
            <SlidersHorizontal className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">系统配置（DB 覆盖 · 重启生效）</h2>
          </div>
          <p className="text-[11px] text-muted mb-2">表单化编辑模型供应商 / RAG 超参数，保存到数据库，重启后端后生效。留空字段保持 yaml 默认值。</p>
          <AiConfigForm />
          <RagConfigForm />
        </section>

        {/* Trace */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Activity className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">链路追踪</h2>
          </div>

          {selectedTrace ? (
            <TraceWaterfall detail={selectedTrace} onBack={() => setSelectedTrace(null)} />
          ) : (
            <div className="space-y-1">
              {traces.length > 0 ? (
                traces.map((t: any) => (
                  <button key={t.traceId} onClick={() => handleTraceClick(t.traceId)}
                    className="w-full flex items-center justify-between text-xs py-1.5 px-2
                      rounded-md border-b border-border last:border-0
                      hover:bg-accent/5 transition-colors cursor-pointer">
                    <span className="text-muted font-mono">{t.traceId?.slice(0, 8)}</span>
                    <span className="flex items-center gap-2">
                      <span className="text-muted">{t.totalDurationMs}ms</span>
                      <span className={`${t.status === 'SUCCESS' ? 'text-accent' : 'text-vital'}`}>{t.status}</span>
                    </span>
                  </button>
                ))
              ) : (
                <p className="text-xs text-muted">暂无链路数据</p>
              )}
            </div>
          )}
          {traceLoading && (
            <div className="flex items-center gap-2 mt-3 text-xs text-muted">
              <Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载链路详情...
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

/** 链路瀑布图 — 用 trace 节点的父子层级 + 耗时渲染横向时间条 */
function TraceWaterfall({ detail, onBack }: { detail: TraceDetail; onBack: () => void }) {
  const { run, nodes } = detail
  const maxDur = Math.max(run.durationMs || 0, ...nodes.map(n => n.durationMs || 0), 1)
  const fmt = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`)

  return (
    <div>
      {/* 头部 */}
      <div className="flex items-center gap-2 mb-3">
        <button onClick={onBack}
          className="p-1 rounded-md text-muted hover:text-text-primary hover:bg-accent/5 transition-colors">
          <ChevronLeft className="w-4 h-4" />
        </button>
        <div className="min-w-0">
          <div className="flex items-center gap-2 text-xs">
            <span className="font-mono text-muted">{run.traceId?.slice(0, 12)}</span>
            <span className={`${run.status === 'SUCCESS' ? 'text-accent' : 'text-vital'} font-semibold`}>{run.status}</span>
            <span className="text-muted">{fmt(run.durationMs || 0)}</span>
          </div>
          <div className="text-[10px] text-muted truncate">
            {run.traceName || 'rag-stream-chat'} · {run.startTime ? new Date(run.startTime).toLocaleString() : ''}
          </div>
        </div>
      </div>

      {/* 瀑布图 */}
      <div className="space-y-0.5 overflow-x-auto">
        {nodes.map((n: TraceNode, i: number) => (
          <TraceBar key={n.nodeId || i} node={n} maxDur={maxDur} fmt={fmt} />
        ))}
      </div>
    </div>
  )
}

function TraceBar({ node, maxDur, fmt }: { node: TraceNode; maxDur: number; fmt: (ms: number) => string }) {
  const err = node.status !== 'SUCCESS'
  const pct = Math.max((node.durationMs || 0) / maxDur * 100, 0.5)
  return (
    <div className="flex items-center gap-2 text-xs">
      <div className="w-44 shrink-0 flex items-center justify-end gap-1.5"
        style={{ paddingRight: node.depth * 10 }}>
        <span className={`truncate ${err ? 'text-vital' : 'text-text-primary'}`} title={node.nodeName}>
          {node.nodeName}
        </span>
        <span className="text-[10px] text-muted shrink-0">{fmt(node.durationMs || 0)}</span>
      </div>
      <div className="h-4 flex-1 bg-accent/5 rounded-sm relative">
        <div className={`h-full rounded-sm ${err ? 'bg-vital' : 'bg-accent/70'}`}
          style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
