import { useState, useEffect } from 'react'
import {
  Loader2, Plus, Pencil, Trash2, X, Users as UsersIcon, RefreshCw,
  Brain, ChevronLeft, Sparkles, Database,
} from 'lucide-react'
import {
  listUsers, createUser, updateUser, deleteUser,
  getMemoryFacts, getMemoryEpisodes,
  createMemoryFact, updateMemoryFact, deleteMemoryFact,
  createMemoryEpisode, updateMemoryEpisode, deleteMemoryEpisode,
} from '../api/baymd'

interface UserRow {
  id: string; username?: string; email?: string; role?: string;
  avatar?: string; emailVerified?: number; followupEnabled?: number; createTime?: string;
}
interface Fact { id: string; factType?: string; factText?: string; confidence?: number; createdAt?: string }
interface Episode { id: string; title?: string; summary?: string; topics?: string[]; createdAt?: string }

const ROLES = ['user', 'admin']
const FACT_TYPES = ['health', 'behavior', 'preference', 'goal']

/** 用户管理 — 用户增删改查 + 画像(记忆)管理,合并自原「用户记忆」板块 */
export default function UsersView() {
  const [users, setUsers] = useState<UserRow[]>([])
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState('')
  const [filter, setFilter] = useState('')
  const [editor, setEditor] = useState<null | { mode: 'add' | 'edit'; id?: string }>(null)
  const [form, setForm] = useState({ username: '', password: '', role: 'user', email: '' })

  // ===== 画像(记忆)详情状态 =====
  const [detail, setDetail] = useState<UserRow | null>(null)
  const [facts, setFacts] = useState<Fact[]>([])
  const [episodes, setEpisodes] = useState<Episode[]>([])
  const [memLoading, setMemLoading] = useState(false)
  const [memMsg, setMemMsg] = useState('')
  const [memEditor, setMemEditor] = useState<null | { kind: 'fact' | 'episode'; mode: 'add' | 'edit'; id?: string }>(null)
  const [memForm, setMemForm] = useState<{ factType: string; factText: string; confidence: string; title: string; summary: string; topics: string }>({
    factType: 'health', factText: '', confidence: '0.8', title: '', summary: '', topics: ''
  })

  const load = async () => {
    setLoading(true)
    try { setUsers(await listUsers(1, 500)) } catch { /* 忽略 */ } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])

  // ===== 用户增删改 =====
  const openAdd = () => {
    setForm({ username: '', password: '', role: 'user', email: '' })
    setEditor({ mode: 'add' })
  }
  const openEdit = (u: UserRow) => {
    setForm({ username: u.username || '', password: '', role: u.role || 'user', email: u.email || '' })
    setEditor({ mode: 'edit', id: u.id })
  }

  const save = async () => {
    setMsg('')
    try {
      let res: any
      if (editor?.mode === 'add') {
        if (!form.username.trim() || !form.password) { setMsg('用户名和密码必填'); return }
        res = await createUser({
          username: form.username.trim(), password: form.password,
          role: form.role, email: form.email.trim() || undefined,
        })
        if (!res.success) throw new Error(res.message)
      } else if (editor?.id) {
        res = await updateUser(editor.id, {
          username: form.username.trim() || undefined,
          password: form.password || undefined,
          role: form.role,
          email: form.email.trim(),
        })
        if (!res.success) throw new Error(res.message)
      }
      setEditor(null)
      setMsg('✅ 已保存')
      await load()
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }

  const del = async (u: UserRow) => {
    if (!window.confirm(`确定删除用户「${u.username}」？其记忆将一并删除。`)) return
    setMsg('')
    try {
      const res = await deleteUser(u.id)
      if (!res.success) throw new Error(res.message)
      setMsg('✅ 已删除')
      if (detail?.id === u.id) setDetail(null)
      await load()
    } catch (e: any) { setMsg(`删除失败: ${e?.message || ''}`) }
  }

  // ===== 画像(记忆)加载与增删改 =====
  const loadDetail = async (u: UserRow) => {
    setDetail(u)
    setMemLoading(true)
    setMemMsg('')
    try {
      const [f, e] = await Promise.all([getMemoryFacts(u.id), getMemoryEpisodes(u.id)])
      setFacts(f)
      setEpisodes(e)
    } catch (err: any) { setMemMsg(`加载画像失败: ${err?.message || ''}`) } finally { setMemLoading(false) }
  }

  const refreshMem = async () => {
    if (!detail) return
    const [f, e] = await Promise.all([getMemoryFacts(detail.id), getMemoryEpisodes(detail.id)])
    setFacts(f)
    setEpisodes(e)
  }

  const openAddMem = (kind: 'fact' | 'episode') => {
    setMemForm({ factType: 'health', factText: '', confidence: '0.8', title: '', summary: '', topics: '' })
    setMemEditor({ kind, mode: 'add' })
  }
  const openEditFact = (f: Fact) => {
    setMemForm({ factType: f.factType || 'health', factText: f.factText || '', confidence: String(f.confidence ?? 0.8), title: '', summary: '', topics: '' })
    setMemEditor({ kind: 'fact', mode: 'edit', id: f.id })
  }
  const openEditEpisode = (e: Episode) => {
    setMemForm({ factType: 'health', factText: '', confidence: '0.8', title: e.title || '', summary: e.summary || '', topics: (e.topics || []).join(',') })
    setMemEditor({ kind: 'episode', mode: 'edit', id: e.id })
  }

  const saveMem = async () => {
    if (!detail) return
    setMemMsg('')
    try {
      if (memEditor?.kind === 'fact') {
        const data = { userId: detail.id, factType: memForm.factType, factText: memForm.factText, confidence: parseFloat(memForm.confidence) }
        if (memEditor.mode === 'add') await createMemoryFact(data)
        else if (memEditor.id) await updateMemoryFact(memEditor.id, data)
      } else {
        const data = { userId: detail.id, title: memForm.title, summary: memForm.summary, topics: memForm.topics.split(',').map(s => s.trim()).filter(Boolean) }
        if (memEditor.mode === 'add') await createMemoryEpisode(data)
        else if (memEditor.id) await updateMemoryEpisode(memEditor.id, data)
      }
      setMemEditor(null)
      setMemMsg('✅ 已保存')
      await refreshMem()
    } catch (e: any) { setMemMsg(`保存失败: ${e?.message || ''}`) }
  }

  const delFact = async (id: string) => { await deleteMemoryFact(id); await refreshMem() }
  const delEpisode = async (id: string) => { await deleteMemoryEpisode(id); await refreshMem() }

  const filtered = users.filter(u =>
    !filter || (u.username || '').includes(filter) || (u.email || '').includes(filter)
  )

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center justify-between px-6 shrink-0 bg-white/70 backdrop-blur-md">
        <div className="flex items-center">
          <div className="w-1 h-6 rounded-full bg-accent mr-3" />
          <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">
            {detail ? `用户画像 · ${detail.username || detail.id}` : '用户管理'}
          </h1>
        </div>
        <div className="flex items-center gap-2">
          {!detail && (
            <>
              <input value={filter} onChange={e => setFilter(e.target.value)}
                placeholder="搜索用户名 / 邮箱..."
                className="px-3 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent w-48" />
              <button onClick={load} className="flex items-center gap-1 text-[11px] text-muted hover:text-accent">
                <RefreshCw className="w-3 h-3" /> 刷新
              </button>
              <button onClick={openAdd}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-accent text-white text-xs hover:bg-accent-dark transition-colors">
                <Plus className="w-3.5 h-3.5" /> 新增用户
              </button>
            </>
          )}
        </div>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6">
        <div className="max-w-4xl">
          {detail ? (
            // ===== 画像详情 =====
            <div>
              <button onClick={() => setDetail(null)}
                className="flex items-center gap-1 text-xs text-muted hover:text-text-primary mb-3">
                <ChevronLeft className="w-3.5 h-3.5" /> 返回用户列表
              </button>
              <div className="flex items-center gap-3 mb-3 bg-white border border-border/70 rounded-xl shadow-sm px-4 py-3">
                <img src={detail.avatar || '/baymax.png'} alt="头像"
                  className="w-9 h-9 rounded-full object-cover object-top ring-1 ring-border" />
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-text-primary">{detail.username || detail.id}</span>
                    {detail.role === 'admin' && <span className="text-[9px] px-1 py-0.5 rounded bg-vital/10 text-vital">admin</span>}
                  </div>
                  <div className="text-[11px] text-muted">
                    {detail.email ? detail.email : '未绑定邮箱'} · {facts.length} Fact · {episodes.length} Episode
                  </div>
                </div>
                <span className="ml-auto flex gap-2 shrink-0">
                  <button onClick={() => openAddMem('fact')}
                    className="flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-accent text-white text-[11px] hover:opacity-90">
                    <Plus className="w-3 h-3" /> 新增 Fact
                  </button>
                  <button onClick={() => openAddMem('episode')}
                    className="flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-accent text-white text-[11px] hover:opacity-90">
                    <Plus className="w-3 h-3" /> 新增 Episode
                  </button>
                </span>
              </div>

              {memLoading && <p className="text-xs text-muted flex items-center gap-1 mb-3"><Loader2 className="w-3 h-3 animate-spin" /> 加载中...</p>}

              {/* Facts */}
              <div className="mb-4">
                <h3 className="text-xs font-semibold text-muted mb-2 flex items-center gap-1">
                  <Sparkles className="w-3.5 h-3.5 text-accent" /> Fact（原子事实）
                </h3>
                {facts.length === 0 ? <p className="text-[11px] text-muted">无</p> : (
                  <div className="space-y-1.5">
                    {facts.map(f => (
                      <div key={f.id} className="bg-white border border-border/70 rounded-lg shadow-sm px-3 py-2 flex items-start justify-between">
                        <div className="min-w-0">
                          <div className="text-[11px] text-muted mb-0.5">[{f.factType || 'fact'}] 置信度 {f.confidence?.toFixed(2) ?? '-'}</div>
                          <p className="text-xs text-text-primary">{f.factText}</p>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0 ml-2">
                          <button onClick={() => openEditFact(f)} className="text-muted hover:text-accent"><Pencil className="w-3.5 h-3.5" /></button>
                          <button onClick={() => delFact(f.id)} className="text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Episodes */}
              <div>
                <h3 className="text-xs font-semibold text-muted mb-2 flex items-center gap-1">
                  <Brain className="w-3.5 h-3.5 text-accent" /> Episode（对话情节）
                </h3>
                {episodes.length === 0 ? <p className="text-[11px] text-muted">无</p> : (
                  <div className="space-y-1.5">
                    {episodes.map(x => (
                      <div key={x.id} className="bg-white border border-border/70 rounded-lg shadow-sm px-3 py-2 flex items-start justify-between">
                        <div className="min-w-0">
                          <div className="text-xs font-medium text-text-primary mb-0.5">{x.title || x.id}</div>
                          <p className="text-[11px] text-muted mb-0.5">{x.summary}</p>
                          {x.topics?.length ? <p className="text-[10px] text-accent">{(x.topics || []).join(' · ')}</p> : null}
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0 ml-2">
                          <button onClick={() => openEditEpisode(x)} className="text-muted hover:text-accent"><Pencil className="w-3.5 h-3.5" /></button>
                          <button onClick={() => delEpisode(x.id)} className="text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              {memMsg && <p className="text-[11px] text-accent mt-3">{memMsg}</p>}
            </div>
          ) : (
            // ===== 用户列表 =====
            <div>
              <div className="flex items-center gap-2 text-[11px] text-muted mb-3">
                <UsersIcon className="w-4 h-4 text-accent" /> 共 {filtered.length} 个用户
              </div>

              {loading ? (
                <div className="flex items-center gap-2 text-xs text-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
              ) : filtered.length === 0 ? (
                <p className="text-xs text-muted">暂无用户</p>
              ) : (
                <div className="bg-white border border-border/70 rounded-xl shadow-sm divide-y divide-border/60">
                  {/* 表头 */}
                  <div className="px-4 py-2 flex items-center gap-3 text-[10px] text-muted uppercase tracking-wider">
                    <span className="flex-1 min-w-0">用户</span>
                    <span className="w-16 text-center">角色</span>
                    <span className="w-32 text-center">邮箱</span>
                    <span className="w-14 text-center">随访</span>
                    <span className="w-24 text-center">画像</span>
                    <span className="w-20 text-center">操作</span>
                  </div>
                  {filtered.map(u => (
                    <div key={u.id} className="px-4 py-2.5 flex items-center gap-3 hover:bg-accent/5 transition-colors">
                      {/* 头像 + 用户名 */}
                      <div className="flex-1 min-w-0 flex items-center gap-3">
                        <img
                          src={u.avatar || '/baymax.png'}
                          alt="头像"
                          className="w-8 h-8 rounded-full object-cover object-top ring-1 ring-border shrink-0"
                        />
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-medium text-text-primary truncate">{u.username || u.id}</span>
                            {u.role === 'admin' && <span className="text-[9px] px-1 py-0.5 rounded bg-vital/10 text-vital">admin</span>}
                          </div>
                          <div className="text-[10px] text-muted truncate">ID: {u.id}</div>
                        </div>
                      </div>
                      {/* 角色 */}
                      <span className="w-16 text-center text-xs text-muted">{u.role || 'user'}</span>
                      {/* 邮箱 */}
                      <span className="w-32 text-center text-[11px] text-muted truncate" title={u.email || ''}>{u.email || '—'}</span>
                      {/* 随访 */}
                      <span className="w-14 text-center">
                        <span className={`text-[10px] px-1.5 py-0.5 rounded ${u.followupEnabled === 1 ? 'bg-accent/10 text-accent' : 'bg-muted/10 text-muted'}`}>
                          {u.followupEnabled === 1 ? '开启' : '关闭'}
                        </span>
                      </span>
                      {/* 画像 */}
                      <div className="w-24 flex items-center justify-center shrink-0">
                        <button onClick={() => loadDetail(u)}
                          className="flex items-center gap-1 px-2 py-1 rounded-md border border-accent/30 text-accent text-[10px] hover:bg-accent/10 transition-colors"
                          title="查看/管理该用户记忆画像">
                          <Brain className="w-3 h-3" /> 画像
                        </button>
                      </div>
                      {/* 操作 */}
                      <div className="w-20 flex items-center justify-center gap-1.5 shrink-0">
                        <button onClick={() => openEdit(u)} className="p-1.5 rounded text-muted hover:text-accent transition-colors" title="编辑">
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={() => del(u)} className="p-1.5 rounded text-muted hover:text-vital transition-colors" title="删除">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
            </div>
          )}
        </div>
      </div>

      {/* 用户编辑弹层 */}
      {editor && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setEditor(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[420px] p-5" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">{editor.mode === 'add' ? '新增用户' : '编辑用户'}</h3>
              <button onClick={() => setEditor(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3 text-xs">
              <label className="block">
                <span className="text-muted">用户名</span>
                <input value={form.username} onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
                  className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
              </label>
              <label className="block">
                <span className="text-muted">邮箱</span>
                <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                  placeholder="用于健康随访"
                  className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
              </label>
              <label className="block">
                <span className="text-muted">密码{editor.mode === 'edit' ? '（留空不修改）' : ''}</span>
                <input type="password" value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                  className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
              </label>
              <label className="block">
                <span className="text-muted">角色</span>
                <select value={form.role} onChange={e => setForm(f => ({ ...f, role: e.target.value }))}
                  className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent">
                  {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </label>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setEditor(null)}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">取消</button>
              <button onClick={save} className="px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:bg-accent-dark transition-colors">保存</button>
            </div>
          </div>
        </div>
      )}

      {/* 画像条目编辑弹层 */}
      {memEditor && detail && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setMemEditor(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[480px] max-h-[85vh] overflow-y-auto p-5" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">
                {memEditor.mode === 'add' ? '新增' : '编辑'} {memEditor.kind === 'fact' ? 'Fact' : 'Episode'}
                <span className="text-muted font-normal text-xs ml-2">用户: {detail.username || detail.id}</span>
              </h3>
              <button onClick={() => setMemEditor(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>

            {memEditor.kind === 'fact' ? (
              <div className="space-y-3">
                <label className="block text-xs">
                  <span className="text-muted">类型</span>
                  <select value={memForm.factType} onChange={e => setMemForm(f => ({ ...f, factType: e.target.value }))}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent">
                    {FACT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="text-muted">内容</span>
                  <textarea value={memForm.factText} onChange={e => setMemForm(f => ({ ...f, factText: e.target.value }))}
                    placeholder="如: 患有高血压 3 年"
                    className="mt-0.5 w-full h-20 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent resize-y" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">置信度（0~1）</span>
                  <input type="number" step="0.05" min="0" max="1" value={memForm.confidence}
                    onChange={e => setMemForm(f => ({ ...f, confidence: e.target.value }))}
                    className="mt-0.5 w-24 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
              </div>
            ) : (
              <div className="space-y-3">
                <label className="block text-xs">
                  <span className="text-muted">标题</span>
                  <input value={memForm.title} onChange={e => setMemForm(f => ({ ...f, title: e.target.value }))}
                    placeholder="如: 高血压用药咨询"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">摘要</span>
                  <textarea value={memForm.summary} onChange={e => setMemForm(f => ({ ...f, summary: e.target.value }))}
                    placeholder="情节摘要 1-2 句"
                    className="mt-0.5 w-full h-16 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent resize-y" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">标签（逗号分隔）</span>
                  <input value={memForm.topics} onChange={e => setMemForm(f => ({ ...f, topics: e.target.value }))}
                    placeholder="如: 高血压,用药"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
              </div>
            )}

            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setMemEditor(null)}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">取消</button>
              <button onClick={saveMem}
                className="px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
