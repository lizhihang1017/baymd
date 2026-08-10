import { authHeaders } from '../api/baymd'
import { useState, useEffect, useCallback, useRef } from 'react'
import { Users, MessageSquare, Activity, RefreshCw, Cpu } from 'lucide-react'

interface Ops {
  onlineUsers: number; totalUsers: number; totalConversations: number;
  totalMessages: number; estimatedTokens: number; todayMessages: number; todayTokens: number;
}
interface TrendPoint { time: string; messages: number; chars: number }
interface LlmStats {
  total: { calls: number; avg_duration_ms: number; failed: number };
  hourly: { time: string; calls: number; failed: number }[];
  recent: { runs: number; avg_duration_ms: number; success: number };
}

export default function OpsView() {
  const [ops, setOps] = useState<Ops | null>(null)
  const [trends, setTrends] = useState<TrendPoint[]>([])
  const [llm, setLlm] = useState<LlmStats | null>(null)
  const [updatedAt, setUpdatedAt] = useState('')

  const load = useCallback(async () => {
    try {
      const [o, t, l] = await Promise.all([
        fetch('/api/baymd/admin/ops/overview', { headers: authHeaders() }).then(r => r.json()),
        fetch('/api/baymd/admin/ops/trends?hours=24', { headers: authHeaders() }).then(r => r.json()),
        fetch('/api/baymd/admin/ops/llm-stats?hours=24', { headers: authHeaders() }).then(r => r.json()),
      ])
      setOps(o.data || null)
      setTrends((t.data || []).map((x: any) => ({ time: x.time, messages: Number(x.messages) || 0, chars: Number(x.chars) || 0 })))
      setLlm(l.data || null)
      setUpdatedAt(new Date().toLocaleTimeString())
    } catch { /* ignore */ }
  }, [])

  useEffect(() => {
    load()
    const timer = setInterval(load, 30_000) // 30s 自动刷新
    return () => clearInterval(timer)
  }, [load])

  const fmt = (n: number) => n >= 10000 ? `${(n / 10000).toFixed(1)}w` : n.toLocaleString()

/** 数字滚动动画 — 值变化时从旧值渐变到新值 */
function CountUp({ value, duration = 600 }: { value: string; duration?: number }) {
  const [display, setDisplay] = useState(value)
  const prevRef = useRef(value)

  useEffect(() => {
    const from = prevRef.current
    const to = value
    prevRef.current = to
    // 非纯数字(含单位/百分比)直接显示
    const fromNum = parseFloat(from.replace(/[^0-9.]/g, ''))
    const toNum = parseFloat(to.replace(/[^0-9.]/g, ''))
    if (isNaN(fromNum) || isNaN(toNum) || fromNum === toNum) {
      setDisplay(to)
      return
    }
    const suffix = to.replace(/[0-9.,]/g, '')
    const start = performance.now()
    let raf: number
    const tick = (now: number) => {
      const p = Math.min((now - start) / duration, 1)
      const eased = 1 - Math.pow(1 - p, 3) // easeOutCubic
      const val = fromNum + (toNum - fromNum) * eased
      setDisplay(Math.round(val).toLocaleString() + suffix)
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value, duration])

  return <>{display}</>
}

  const cards: { label: string; value: string; icon: any; color: string }[] = ops ? [
    { label: '在线用户（5分钟）', value: fmt(ops.onlineUsers), icon: Users, color: 'text-accent' },
    { label: '总用户', value: fmt(ops.totalUsers), icon: Users, color: 'text-accent' },
    { label: '总会话', value: fmt(ops.totalConversations), icon: MessageSquare, color: 'text-accent' },
    { label: '总消息', value: fmt(ops.totalMessages), icon: MessageSquare, color: 'text-accent' },
    { label: 'Token 消耗（估算）', value: fmt(ops.estimatedTokens), icon: Activity, color: 'text-vital' },
    { label: '今日消息', value: fmt(ops.todayMessages), icon: MessageSquare, color: 'text-accent' },
    { label: '今日 Token', value: fmt(ops.todayTokens), icon: Activity, color: 'text-vital' },
  ] : []

  const llmCards = llm ? [
    { label: 'LLM 调用次数', value: fmt(llm.total.calls), icon: Cpu, color: 'text-accent' },
    { label: 'LLM 平均耗时', value: `${(llm.total.avg_duration_ms / 1000).toFixed(2)}s`, icon: Cpu, color: 'text-accent' },
    { label: 'LLM 失败次数', value: fmt(llm.total.failed), icon: Cpu, color: llm.total.failed > 0 ? 'text-vital' : 'text-accent' },
    { label: '问答成功率', value: `${llm.recent.runs > 0 ? Math.round(llm.recent.success / llm.recent.runs * 100) : 100}%`, icon: Activity, color: 'text-accent' },
    { label: '问答平均耗时', value: `${(llm.recent.avg_duration_ms / 1000).toFixed(2)}s`, icon: Activity, color: 'text-accent' },
  ] : []

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center justify-between px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="flex items-center">
          <div className="w-1 h-6 rounded-full bg-accent mr-3" />
          <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">运营看板</h1>
        </div>
        <div className="flex items-center gap-2 text-[11px] text-muted">
          {updatedAt && <span>更新于 {updatedAt}</span>}
          <button onClick={load} className="flex items-center gap-1 hover:text-accent transition-colors">
            <RefreshCw className="w-3 h-3" /> 刷新
          </button>
        </div>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
        {/* KPI 卡片 */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {(cards || []).map((c, i) => (
            <div key={i} className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift stagger-item" style={{ animationDelay: `${i * 50}ms` }}>
              <div className="flex items-center gap-1.5 mb-2">
                <c.icon className={`w-4 h-4 ${c.color}`} />
                <span className="text-[11px] text-muted">{c.label}</span>
              </div>
              <div className="text-2xl font-semibold text-text-primary"><CountUp value={c.value} /></div>
            </div>
          ))}
        </div>

        {/* LLM 调用统计 */}
        {llm && (
          <>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
              {(llmCards || []).map((c, i) => (
                <div key={i} className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift stagger-item" style={{ animationDelay: `${i * 50}ms` }}>
                  <div className="flex items-center gap-1.5 mb-2">
                    <c.icon className={`w-4 h-4 ${c.color}`} />
                    <span className="text-[11px] text-muted">{c.label}</span>
                  </div>
                  <div className="text-2xl font-semibold text-text-primary"><CountUp value={c.value} /></div>
                </div>
              ))}
            </div>

            {/* LLM 调用量 + 错误率趋势 */}
            {llm.hourly.length >= 2 && (
              <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
                <div className="flex items-center gap-1.5 mb-3">
                  <Cpu className="w-4 h-4 text-accent" />
                  <h2 className="text-sm font-semibold">LLM 调用趋势（近 24 小时）</h2>
                </div>
                <LlmTrendChart data={llm.hourly} />
              </div>
            )}
          </>
        )}

        {/* 消息趋势折线图（近 24h） */}
        <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
          <div className="flex items-center gap-1.5 mb-3">
            <Activity className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">消息趋势（近 24 小时）</h2>
          </div>
          {trends.length < 2 ? (
            <p className="text-xs text-muted">暂无足够数据（至少 2 个数据点）</p>
          ) : (
            <TrendChart data={trends} />
          )}
        </div>

        <p className="text-[11px] text-muted">
          Token 消耗按消息内容长度估算（约 1.5 字符/token）;LLM 统计来自链路追踪数据,实际以模型账单为准。
        </p>
      </div>
    </div>
  )
}

/** 内联 SVG 折线图（消息数） */
function TrendChart({ data }: { data: TrendPoint[] }) {
  const W = 640, H = 180, PAD = 24
  const max = Math.max(...data.map(d => d.messages), 1)
  const x = (i: number) => PAD + (i / (data.length - 1)) * (W - PAD * 2)
  const y = (v: number) => H - PAD - (v / max) * (H - PAD * 2)
  const pts = data.map((d, i) => `${x(i).toFixed(1)},${y(d.messages).toFixed(1)}`).join(' ')
  const area = `${PAD},${H - PAD} ${pts} ${x(data.length - 1).toFixed(1)},${H - PAD}`

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full min-w-[480px]" role="img" aria-label="消息数趋势">
        <defs>
          <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#0891B2" stopOpacity="0.25" />
            <stop offset="100%" stopColor="#0891B2" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0.25, 0.5, 0.75, 1].map(p => (
          <line key={p} x1={PAD} x2={W - PAD} y1={H - PAD - p * (H - PAD * 2)} y2={H - PAD - p * (H - PAD * 2)}
            stroke="#CFFAFE" strokeWidth="1" />
        ))}
        <polygon points={area} fill="url(#trendFill)" />
        <polyline points={pts} fill="none" stroke="#0891B2" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {data.map((d, i) => (
          <g key={i}>
            <circle cx={x(i)} cy={y(d.messages)} r="2.5" fill="#0891B2" />
            <title>{`${d.time}: ${d.messages} 条消息`}</title>
          </g>
        ))}
        <text x={PAD} y={H - 6} fontSize="9" fill="#64748B">{data[0].time}</text>
        <text x={W - PAD} y={H - 6} fontSize="9" fill="#64748B" textAnchor="end">{data[data.length - 1].time}</text>
      </svg>
    </div>
  )
}

/** LLM 调用量（柱状）+ 错误率（折线）双轴图 */
function LlmTrendChart({ data }: { data: { time: string; calls: number; failed: number }[] }) {
  const W = 640, H = 180, PAD = 24
  const maxCalls = Math.max(...data.map(d => d.calls), 1)
  const x = (i: number) => PAD + (i / (data.length - 1)) * (W - PAD * 2)
  const barW = Math.max((W - PAD * 2) / data.length * 0.5, 2)
  const yCalls = (v: number) => H - PAD - (v / maxCalls) * (H - PAD * 2)
  const yErr = (v: number) => H - PAD - (v / 100) * (H - PAD * 2)
  const errPts = data.map((d, i) => {
    const rate = d.calls > 0 ? d.failed / d.calls * 100 : 0
    return `${x(i).toFixed(1)},${yErr(rate).toFixed(1)}`
  }).join(' ')

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full min-w-[480px]" role="img" aria-label="LLM 调用趋势">
        {[0.25, 0.5, 0.75, 1].map(p => (
          <line key={p} x1={PAD} x2={W - PAD} y1={H - PAD - p * (H - PAD * 2)} y2={H - PAD - p * (H - PAD * 2)}
            stroke="#CFFAFE" strokeWidth="1" />
        ))}
        {/* 调用量柱状 */}
        {data.map((d, i) => (
          <g key={i}>
            <rect x={x(i) - barW / 2} y={yCalls(d.calls)} width={barW} height={Math.max(H - PAD - yCalls(d.calls), 1)}
              fill="#0891B2" opacity="0.75" rx="1">
              <title>{`${d.time}: ${d.calls} 次调用`}</title>
            </rect>
          </g>
        ))}
        {/* 错误率折线 */}
        <polyline points={errPts} fill="none" stroke="#DC2626" strokeWidth="1.5" strokeLinejoin="round" />
        {data.map((d, i) => {
          const rate = d.calls > 0 ? d.failed / d.calls * 100 : 0
          return (
            <g key={`e${i}`}>
              <circle cx={x(i)} cy={yErr(rate)} r="2" fill={rate > 0 ? '#C0392B' : '#8899A6'} />
              <title>{`${d.time}: 错误率 ${rate.toFixed(0)}%`}</title>
            </g>
          )
        })}
        <text x={PAD} y={H - 6} fontSize="9" fill="#64748B">{data[0].time}</text>
        <text x={W - PAD} y={H - 6} fontSize="9" fill="#64748B" textAnchor="end">{data[data.length - 1].time}</text>
        {/* 图例 */}
        <text x={W - 140} y={14} fontSize="9" fill="#0891B2">■ 调用量</text>
        <text x={W - 70} y={14} fontSize="9" fill="#C0392B">— 错误率%</text>
      </svg>
    </div>
  )
}
