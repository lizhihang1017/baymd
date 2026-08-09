import { useState, useEffect, useCallback } from 'react'
import { Users, MessageSquare, Activity, RefreshCw } from 'lucide-react'

interface Ops {
  onlineUsers: number; totalUsers: number; totalConversations: number;
  totalMessages: number; estimatedTokens: number; todayMessages: number; todayTokens: number;
}
interface TrendPoint { time: string; messages: number; chars: number }

export default function OpsView() {
  const [ops, setOps] = useState<Ops | null>(null)
  const [trends, setTrends] = useState<TrendPoint[]>([])
  const [updatedAt, setUpdatedAt] = useState('')

  const load = useCallback(async () => {
    try {
      const [o, t] = await Promise.all([
        fetch('/api/baymd/admin/ops/overview').then(r => r.json()),
        fetch('/api/baymd/admin/ops/trends?hours=24').then(r => r.json()),
      ])
      setOps(o.data || null)
      setTrends((t.data || []).map((x: any) => ({ time: x.time, messages: Number(x.messages) || 0, chars: Number(x.chars) || 0 })))
      setUpdatedAt(new Date().toLocaleTimeString())
    } catch { /* ignore */ }
  }, [])

  useEffect(() => {
    load()
    const timer = setInterval(load, 30_000) // 30s 自动刷新
    return () => clearInterval(timer)
  }, [load])

  const fmt = (n: number) => n >= 10000 ? `${(n / 10000).toFixed(1)}w` : n.toLocaleString()

  const cards: { label: string; value: string; icon: any; color: string }[] = ops ? [
    { label: '在线用户（5分钟）', value: fmt(ops.onlineUsers), icon: Users, color: 'text-accent' },
    { label: '总用户', value: fmt(ops.totalUsers), icon: Users, color: 'text-accent' },
    { label: '总会话', value: fmt(ops.totalConversations), icon: MessageSquare, color: 'text-accent' },
    { label: '总消息', value: fmt(ops.totalMessages), icon: MessageSquare, color: 'text-accent' },
    { label: 'Token 消耗（估算）', value: fmt(ops.estimatedTokens), icon: Activity, color: 'text-vital' },
    { label: '今日消息', value: fmt(ops.todayMessages), icon: MessageSquare, color: 'text-accent' },
    { label: '今日 Token', value: fmt(ops.todayTokens), icon: Activity, color: 'text-vital' },
  ] : []

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center justify-between px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">运营看板</h1>
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
            <div key={i} className="bg-panel border border-border rounded-xl p-4">
              <div className="flex items-center gap-1.5 mb-2">
                <c.icon className={`w-4 h-4 ${c.color}`} />
                <span className="text-[11px] text-muted">{c.label}</span>
              </div>
              <div className="text-2xl font-semibold text-text-primary">{c.value}</div>
            </div>
          ))}
        </div>

        {/* 消息趋势折线图（近 24h） */}
        <div className="bg-panel border border-border rounded-xl p-4">
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
          Token 消耗按消息内容长度估算（约 1.5 字符/token），实际以模型账单为准。
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
            <stop offset="0%" stopColor="#5C3D4E" stopOpacity="0.25" />
            <stop offset="100%" stopColor="#5C3D4E" stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* 横向网格线 */}
        {[0.25, 0.5, 0.75, 1].map(p => (
          <line key={p} x1={PAD} x2={W - PAD} y1={H - PAD - p * (H - PAD * 2)} y2={H - PAD - p * (H - PAD * 2)}
            stroke="#E8E6E3" strokeWidth="1" />
        ))}
        <polygon points={area} fill="url(#trendFill)" />
        <polyline points={pts} fill="none" stroke="#5C3D4E" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {data.map((d, i) => (
          <g key={i}>
            <circle cx={x(i)} cy={y(d.messages)} r="2.5" fill="#5C3D4E" />
            <title>{`${d.time}: ${d.messages} 条消息`}</title>
          </g>
        ))}
        {/* X 轴首尾时间 */}
        <text x={PAD} y={H - 6} fontSize="9" fill="#8899A6">{data[0].time}</text>
        <text x={W - PAD} y={H - 6} fontSize="9" fill="#8899A6" textAnchor="end">{data[data.length - 1].time}</text>
      </svg>
    </div>
  )
}
