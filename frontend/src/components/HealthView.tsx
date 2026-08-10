import { useState, useEffect } from 'react'
import { Loader2, RefreshCw, CheckCircle2, XCircle, Cpu, Clock } from 'lucide-react'
import { getHealth } from '../api/baymd'

interface HealthCheck { name: string; status: string; detail?: string; latencyMs?: number }
interface HealthData {
  system?: { name: string; startTime: number; uptimeSeconds: number; javaVersion: string; os: string };
  checks?: HealthCheck[];
  overall?: string;
}

/** 系统健康检查 — 一键检测 DB / Redis / 模型连通性 */
export default function HealthView() {
  const [data, setData] = useState<HealthData | null>(null)
  const [loading, setLoading] = useState(false)
  const [updatedAt, setUpdatedAt] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const d = await getHealth()
      setData(d)
      setUpdatedAt(new Date().toLocaleTimeString())
    } catch { /* 忽略 */ } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])

  const fmtUptime = (s: number) => {
    const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60)
    return d > 0 ? `${d}天${h}小时` : h > 0 ? `${h}小时${m}分` : `${m}分${Math.floor(s % 60)}秒`
  }

  const ok = data?.overall === 'HEALTHY'

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center justify-between px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="flex items-center">
          <div className="w-1 h-6 rounded-full bg-accent mr-3" />
          <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">系统健康</h1>
        </div>
        <div className="flex items-center gap-2 text-[11px] text-muted">
          {updatedAt && <span>更新于 {updatedAt}</span>}
          <button onClick={load} className="flex items-center gap-1 hover:text-accent transition-colors">
            <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} /> 检测
          </button>
        </div>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6">
        <div className="max-w-2xl space-y-5">
          {/* 总览 */}
          <div className={`rounded-xl border p-4 flex items-center gap-3 ${ok ? 'bg-green-500/5 border-green-500/30' : 'bg-vital/5 border-vital/30'}`}>
            {ok ? <CheckCircle2 className="w-6 h-6 text-green-600" /> : <XCircle className="w-6 h-6 text-vital" />}
            <div>
              <div className="text-sm font-semibold">{ok ? '系统健康' : '系统异常'}</div>
              <div className="text-[11px] text-muted">共 {(data?.checks || []).length} 项检查,全部通过</div>
            </div>
          </div>

          {/* 系统信息 */}
          {data?.system && (
            <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
              <div className="flex items-center gap-1.5 mb-3">
                <Cpu className="w-4 h-4 text-accent" />
                <h2 className="text-sm font-semibold">系统信息</h2>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="text-muted">应用</div><div className="text-text-primary">{data.system.name}</div>
                <div className="text-muted">运行时长</div>
                <div className="flex items-center gap-1 text-text-primary"><Clock className="w-3 h-3 text-muted" />{fmtUptime(data.system.uptimeSeconds)}</div>
                <div className="text-muted">启动时间</div>
                <div className="text-text-primary">{new Date(data.system.startTime).toLocaleString()}</div>
                <div className="text-muted">Java</div><div className="text-text-primary">{data.system.javaVersion}</div>
                <div className="text-muted">OS</div><div className="text-text-primary">{data.system.os}</div>
              </div>
            </div>
          )}

          {/* 组件检查 */}
          <div className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
            <h2 className="text-sm font-semibold mb-3">组件检查</h2>
            {loading && !data ? (
              <div className="flex items-center gap-2 text-xs text-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 检测中...</div>
            ) : (
              <div className="space-y-2">
                {(data?.checks || []).map(c => (
                  <div key={c.name} className="flex items-center justify-between px-3 py-2 rounded-lg border border-border/60 bg-white">
                    <div className="flex items-center gap-2">
                      <span role="status" className="flex items-center gap-1.5">
                        {c.status === 'OK'
                          ? <><span className="w-2 h-2 rounded-full bg-accent dot-pulse" aria-hidden /><span className="sr-only">正常</span></>
                          : <><span className="w-2 h-2 rounded-full bg-vital" aria-hidden /><span className="sr-only">异常</span></>}
                      </span>
                      <span className="text-xs font-medium">{c.name}</span>
                      {c.detail && c.detail !== 'OK' && <span className="text-[10px] text-muted truncate max-w-[200px]">{c.detail}</span>}
                    </div>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded ${c.status === 'OK' ? 'bg-accent/10 text-accent' : 'bg-vital/10 text-vital'}`}>
                      {c.status} {c.latencyMs != null ? `· ${c.latencyMs}ms` : ''}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
