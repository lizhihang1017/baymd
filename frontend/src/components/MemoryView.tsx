import { useState, useEffect } from 'react'
import { Database, ChevronLeft, Trash2, Loader2, Sparkles } from 'lucide-react'

interface MemUser { userId: string; username?: string; email?: string; factCount: number; episodeCount: number }
interface Fact { id: string; factType?: string; factText?: string; confidence?: number; createdAt?: string }
interface Episode { id: string; title?: string; summary?: string; topics?: string[]; createdAt?: string }

export default function MemoryView() {
  const [users, setUsers] = useState<MemUser[]>([])
  const [selected, setSelected] = useState<MemUser | null>(null)
  const [facts, setFacts] = useState<Fact[]>([])
  const [episodes, setEpisodes] = useState<Episode[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    fetch('/api/baymd/admin/memory/users').then(r => r.json())
      .then(j => setUsers(j.data || [])).catch(() => {})
  }, [])

  const loadUser = async (u: MemUser) => {
    setSelected(u)
    setLoading(true)
    try {
      const [f, e] = await Promise.all([
        fetch(`/api/baymd/admin/memory/facts?userId=${u.userId}`).then(r => r.json()),
        fetch(`/api/baymd/admin/memory/episodes?userId=${u.userId}`).then(r => r.json()),
      ])
      setFacts(f.data || [])
      setEpisodes(e.data || [])
    } finally { setLoading(false) }
  }

  const delFact = async (id: string) => {
    await fetch(`/api/baymd/admin/memory/fact/${id}`, { method: 'DELETE' })
    setFacts(prev => prev.filter(f => f.id !== id))
  }
  const delEpisode = async (id: string) => {
    await fetch(`/api/baymd/admin/memory/episode/${id}`, { method: 'DELETE' })
    setEpisodes(prev => prev.filter(x => x.id !== id))
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">用户记忆</h1>
      </header>
      <div className="flex-1 min-h-0 overflow-y-auto p-6">
        {selected ? (
          <div>
            <button onClick={() => setSelected(null)}
              className="flex items-center gap-1 text-xs text-muted hover:text-text-primary mb-3">
              <ChevronLeft className="w-3.5 h-3.5" /> 返回用户列表
            </button>
            <div className="flex items-center gap-2 mb-3">
              <Sparkles className="w-4 h-4 text-accent" />
              <h2 className="text-sm font-semibold">{selected.username || selected.userId}</h2>
              <span className="text-xs text-muted">{facts.length} Fact · {episodes.length} Episode</span>
            </div>
            {loading && <p className="text-xs text-muted flex items-center gap-1"><Loader2 className="w-3 h-3 animate-spin" /> 加载中...</p>}

            {/* Facts */}
            <div className="mb-4">
              <h3 className="text-xs font-semibold text-muted mb-2">Fact（原子事实）</h3>
              {facts.length === 0 ? <p className="text-[11px] text-muted">无</p> : (
                <div className="space-y-1.5">
                  {facts.map(f => (
                    <div key={f.id} className="bg-panel border border-border rounded-lg px-3 py-2 flex items-start justify-between">
                      <div className="min-w-0">
                        <div className="text-[11px] text-muted mb-0.5">
                          [{f.factType || 'fact'}] 置信度 {f.confidence?.toFixed(2) ?? '-'}
                        </div>
                        <p className="text-xs text-text-primary">{f.factText}</p>
                      </div>
                      <button onClick={() => delFact(f.id)} className="text-muted hover:text-vital shrink-0 ml-2"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Episodes */}
            <div>
              <h3 className="text-xs font-semibold text-muted mb-2">Episode（对话情节）</h3>
              {episodes.length === 0 ? <p className="text-[11px] text-muted">无</p> : (
                <div className="space-y-1.5">
                  {episodes.map(x => (
                    <div key={x.id} className="bg-panel border border-border rounded-lg px-3 py-2 flex items-start justify-between">
                      <div className="min-w-0">
                        <div className="text-xs font-medium text-text-primary mb-0.5">{x.title || x.id}</div>
                        <p className="text-[11px] text-muted mb-0.5">{x.summary}</p>
                        {x.topics?.length ? <p className="text-[10px] text-accent">{(x.topics || []).join(' · ')}</p> : null}
                      </div>
                      <button onClick={() => delEpisode(x.id)} className="text-muted hover:text-vital shrink-0 ml-2"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div>
            {users.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-muted">
                <Database className="w-12 h-12 mb-3" />
                <p className="text-sm">暂无用户记忆</p>
                <p className="text-xs mt-1">用户完成对话后自动生成记忆</p>
              </div>
            ) : (
              <div className="bg-panel border border-border rounded-xl divide-y divide-border">
                {users.map(u => (
                  <button key={u.userId} onClick={() => loadUser(u)}
                    className="w-full flex items-center justify-between px-4 py-2.5 hover:bg-accent/5 transition-colors text-left">
                    <div>
                      <div className="text-xs font-medium text-text-primary">{u.username || u.userId}</div>
                      {u.email && <div className="text-[10px] text-muted">{u.email}</div>}
                    </div>
                    <div className="flex gap-2 text-[11px]">
                      <span className="text-accent">{u.factCount} Fact</span>
                      <span className="text-muted">{u.episodeCount} Episode</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
