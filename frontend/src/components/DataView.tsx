import { useState, useEffect } from 'react'
import { Loader2, ThumbsUp, ThumbsDown, FileText, Mail, RefreshCw, Trash2, Play, X, Send } from 'lucide-react'
import { getFollowupTasks, getFollowupStats, triggerFollowup, cancelFollowup, deleteFollowup,
  getFeedback, getFeedbackStats, deleteFeedback,
  getReports, getReportDetail, deleteReport } from '../api/baymd'

type Tab = 'followup' | 'feedback' | 'report'

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'text-amber-600 bg-amber-500/10',
  SENT: 'text-accent bg-accent/10',
  ANSWERED: 'text-green-600 bg-green-500/10',
  CANCELLED: 'text-muted bg-muted/10',
  EXPIRED: 'text-vital bg-vital/10',
}

/** 数据管理 — 随访任务 / 用户反馈 / 体检报告 */
export default function DataView() {
  const [tab, setTab] = useState<Tab>('followup')
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState('')

  // 随访
  const [tasks, setTasks] = useState<any[]>([])
  const [taskStats, setTaskStats] = useState<any>({})
  const [taskFilter, setTaskFilter] = useState('')
  // 反馈
  const [feedback, setFeedback] = useState<any[]>([])
  const [fbStats, setFbStats] = useState<any>({})
  const [fbFilter, setFbFilter] = useState<number | undefined>(undefined)
  // 报告
  const [reports, setReports] = useState<any[]>([])
  const [reportDetail, setReportDetail] = useState<any>(null)

  const load = async () => {
    setLoading(true)
    try {
      if (tab === 'followup') {
        const [t, s] = await Promise.all([getFollowupTasks(taskFilter || undefined), getFollowupStats()])
        setTasks(t); setTaskStats(s)
      } else if (tab === 'feedback') {
        const [f, s] = await Promise.all([getFeedback(fbFilter), getFeedbackStats()])
        setFeedback(f); setFbStats(s)
      } else {
        setReports(await getReports())
      }
    } catch { /* 忽略 */ } finally { setLoading(false) }
  }

  useEffect(() => { load() }, [tab, taskFilter, fbFilter])

  const act = async (fn: () => Promise<any>, okMsg: string) => {
    setMsg('')
    try {
      const res = await fn()
      if (!res.success) throw new Error(res.message)
      setMsg(okMsg)
      await load()
    } catch (e: any) { setMsg(`操作失败: ${e?.message || ''}`) }
  }

  const openReport = async (id: string) => {
    setReportDetail(await getReportDetail(id))
  }

  const TABS: { id: Tab; label: string }[] = [
    { id: 'followup', label: '随访任务' },
    { id: 'feedback', label: '用户反馈' },
    { id: 'report', label: '体检报告' },
  ]

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center justify-between px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="flex items-center">
          <div className="w-1 h-6 rounded-full bg-accent mr-3" />
          <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">数据管理</h1>
        </div>
        <button onClick={load} className="flex items-center gap-1 text-[11px] text-muted hover:text-accent">
          <RefreshCw className="w-3 h-3" /> 刷新
        </button>
      </header>

      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        {/* 子 tab */}
        <div className="border-b border-border flex gap-1 px-4 shrink-0">
          {TABS.map(t => (
            <button key={t.id} onClick={() => { setTab(t.id); setMsg('') }}
              className={`px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                tab === t.id ? 'border-accent text-accent' : 'border-transparent text-muted hover:text-text-primary'}`}>
              {t.label}
            </button>
          ))}
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto p-5">
          {/* ===== 随访任务 ===== */}
          {tab === 'followup' && (
            <div className="max-w-4xl">
              <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                <div className="flex items-center gap-2 text-[11px] text-muted flex-wrap">
                  {(taskStats.byStatus || []).map((s: any) => (
                    <span key={s.status} className="px-2 py-0.5 rounded bg-panel border border-border">
                      {s.status}: <b>{s.cnt}</b>
                    </span>
                  ))}
                  <span className="px-2 py-0.5 rounded bg-panel border border-border">总计: <b>{taskStats.total ?? 0}</b></span>
                </div>
                <select value={taskFilter} onChange={e => setTaskFilter(e.target.value)}
                  className="px-2 py-1 rounded-md border border-border text-xs bg-white">
                  <option value="">全部状态</option>
                  <option value="PENDING">PENDING</option>
                  <option value="SENT">SENT</option>
                  <option value="ANSWERED">ANSWERED</option>
                  <option value="CANCELLED">CANCELLED</option>
                  <option value="EXPIRED">EXPIRED</option>
                </select>
              </div>
              {loading ? <Loader2 className="w-4 h-4 animate-spin text-muted" /> : tasks.length === 0 ? (
                <p className="text-xs text-muted">暂无随访任务（对话后会由 LLM 自动规划生成）</p>
              ) : (
                <div className="bg-white border border-border/70 rounded-xl shadow-sm divide-y divide-border/60 card-lift">
                  {tasks.map(t => (
                    <div key={t.id} className="px-4 py-2.5 flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Mail className="w-3.5 h-3.5 text-muted shrink-0" />
                          <span className="text-xs font-medium">{t.username || t.userid}</span>
                          {t.topic && <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent/10 text-accent">{t.topic}</span>}
                          <span className={`text-[10px] px-1.5 py-0.5 rounded ${STATUS_COLOR[t.status] || 'text-muted bg-muted/10'}`}>{t.status}</span>
                        </div>
                        <p className="text-[11px] text-text-primary mt-0.5 truncate">{t.question}</p>
                        <div className="text-[10px] text-muted mt-0.5">
                          触发: {t.triggerTime ? new Date(t.triggerTime).toLocaleString() : '-'}
                          {t.sentTime ? ` · 发送: ${new Date(t.sentTime).toLocaleString()}` : ''}
                        </div>
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        {t.status !== 'CANCELLED' && (
                          <button onClick={() => act(() => triggerFollowup(t.id), '✅ 已触发')} title="手动触发"
                            className="p-1.5 rounded text-muted hover:text-accent"><Play className="w-3.5 h-3.5" /></button>
                        )}
                        {t.status === 'PENDING' && (
                          <button onClick={() => act(() => cancelFollowup(t.id), '✅ 已取消')} title="取消"
                            className="p-1.5 rounded text-muted hover:text-amber-600"><X className="w-3.5 h-3.5" /></button>
                        )}
                        <button onClick={() => act(() => deleteFollowup(t.id), '✅ 已删除')} title="删除"
                          className="p-1.5 rounded text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ===== 用户反馈 ===== */}
          {tab === 'feedback' && (
            <div className="max-w-4xl">
              <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                <div className="flex items-center gap-3 text-[11px] text-muted">
                  {(fbStats.byVote || []).map((s: any) => (
                    <span key={s.vote} className="flex items-center gap-1">
                      {s.vote === 1 ? <ThumbsUp className="w-3 h-3 text-accent" /> : <ThumbsDown className="w-3 h-3 text-vital" />}
                      <b>{s.cnt}</b>
                    </span>
                  ))}
                  <span className="text-muted/70">踩的原因: {(fbStats.byReason || []).map((r: any) => `${r.reason}(${r.cnt})`).join(' · ') || '无'}</span>
                </div>
                <select value={fbFilter ?? ''} onChange={e => setFbFilter(e.target.value === '' ? undefined : Number(e.target.value))}
                  className="px-2 py-1 rounded-md border border-border text-xs bg-white">
                  <option value="">全部</option>
                  <option value={1}>点赞</option>
                  <option value={0}>踩</option>
                </select>
              </div>
              {loading ? <Loader2 className="w-4 h-4 animate-spin text-muted" /> : feedback.length === 0 ? (
                <p className="text-xs text-muted">暂无反馈</p>
              ) : (
                <div className="bg-white border border-border/70 rounded-xl shadow-sm divide-y divide-border/60 card-lift">
                  {feedback.map(f => (
                    <div key={f.id} className="px-4 py-2.5 flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          {f.vote === 1 ? <ThumbsUp className="w-3.5 h-3.5 text-accent" /> : <ThumbsDown className="w-3.5 h-3.5 text-vital" />}
                          <span className="text-xs font-medium">{f.username || f.userid}</span>
                          {f.reason && <span className="text-[10px] px-1.5 py-0.5 rounded bg-vital/10 text-vital">{f.reason}</span>}
                          <span className="text-[10px] text-muted">{f.createTime ? new Date(f.createTime).toLocaleString() : ''}</span>
                        </div>
                        {f.answerSnippet && <p className="text-[11px] text-muted mt-1 line-clamp-2">回答: {f.answerSnippet}</p>}
                        {f.comment && <p className="text-[11px] text-text-primary mt-0.5">评论: {f.comment}</p>}
                      </div>
                      <button onClick={() => act(() => deleteFeedback(f.id), '✅ 已删除')}
                        className="p-1.5 rounded text-muted hover:text-vital shrink-0"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ===== 体检报告 ===== */}
          {tab === 'report' && (
            <div className="max-w-4xl">
              {loading ? <Loader2 className="w-4 h-4 animate-spin text-muted" /> : reports.length === 0 ? (
                <p className="text-xs text-muted">暂无报告</p>
              ) : (
                <div className="bg-white border border-border/70 rounded-xl shadow-sm divide-y divide-border/60 card-lift">
                  {reports.map(r => (
                    <div key={r.id} className="px-4 py-2.5 flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <FileText className="w-3.5 h-3.5 text-accent" />
                          <span className="text-xs font-medium">{r.fileName}</span>
                          <span className="text-[10px] text-muted">{r.username || r.userid}</span>
                          <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                            r.parseStatus === 'SUCCESS' ? 'bg-accent/10 text-accent'
                            : r.parseStatus === 'FAILED' ? 'bg-vital/10 text-vital' : 'text-muted bg-muted/10'}`}>
                            {r.parseStatus}
                          </span>
                        </div>
                        <div className="text-[10px] text-muted mt-0.5">{new Date(r.createTime).toLocaleString()}</div>
                        {r.errorMessage && <div className="text-[11px] text-vital mt-0.5 truncate">{r.errorMessage}</div>}
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button onClick={() => openReport(r.id)} title="查看详情"
                          className="p-1.5 rounded text-muted hover:text-accent"><Send className="w-3.5 h-3.5" /></button>
                        <button onClick={() => act(() => deleteReport(r.id), '✅ 已删除')} title="删除"
                          className="p-1.5 rounded text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
        </div>
      </div>

      {/* 报告详情弹层 */}
      {reportDetail && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setReportDetail(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[720px] max-h-[85vh] overflow-y-auto p-5" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">{reportDetail.fileName}</h3>
              <button onClick={() => setReportDetail(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3 text-xs">
              <div className="text-muted">状态: <b className="text-text-primary">{reportDetail.parseStatus}</b></div>
              {reportDetail.structured && (
                <div>
                  <div className="text-muted mb-1">结构化指标</div>
                  <pre className="bg-surface border border-border rounded-lg p-3 font-mono text-[11px] overflow-x-auto max-h-64">
                    {typeof reportDetail.structured === 'string' ? reportDetail.structured : JSON.stringify(reportDetail.structured, null, 2)}
                  </pre>
                </div>
              )}
              {reportDetail.rawText && (
                <div>
                  <div className="text-muted mb-1">原文（{reportDetail.rawText.length} 字符）</div>
                  <pre className="bg-surface border border-border rounded-lg p-3 font-mono text-[11px] whitespace-pre-wrap max-h-64 overflow-y-auto">{reportDetail.rawText}</pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
