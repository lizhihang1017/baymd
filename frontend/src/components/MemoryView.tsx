import { useState, useEffect } from 'react'
import { Database, ChevronLeft, Trash2, Loader2, Sparkles, Plus, Pencil, X } from 'lucide-react'
import { getMemoryUsers, getMemoryFacts, getMemoryEpisodes,
  createMemoryFact, updateMemoryFact, deleteMemoryFact,
  createMemoryEpisode, updateMemoryEpisode, deleteMemoryEpisode,
  listUsers, createUser, updateUser, deleteUser } from '../api/baymd'

interface MemUser { userId: string; username?: string; email?: string; factCount: number; episodeCount: number; role?: string }
interface Fact { id: string; factType?: string; factText?: string; confidence?: number; createdAt?: string }
interface Episode { id: string; title?: string; summary?: string; topics?: string[]; createdAt?: string }

const FACT_TYPES = ['health', 'behavior', 'preference', 'goal']
const ROLES = ['user', 'admin']

/** 用户记忆管理 — 增删改查（Fact + Episode） */
export default function MemoryView() {
  const [users, setUsers] = useState<MemUser[]>([])
  const [selected, setSelected] = useState<MemUser | null>(null)
  const [facts, setFacts] = useState<Fact[]>([])
  const [episodes, setEpisodes] = useState<Episode[]>([])
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState('')
  // 记忆条目编辑弹层状态: {kind:'fact'|'episode', mode:'add'|'edit', id?:}
  const [editor, setEditor] = useState<null | { kind: 'fact' | 'episode'; mode: 'add' | 'edit'; id?: string }>(null)
  // 用户编辑弹层状态: {mode:'add'|'edit', id?, username?, role?}
  const [userEditor, setUserEditor] = useState<null | { mode: 'add' | 'edit'; id?: string; username?: string; role?: string }>(null)
  const [userForm, setUserForm] = useState<{ username: string; password: string; role: string }>({ username: '', password: '', role: 'user' })
  // 表单草稿
  const [form, setForm] = useState<{ factType: string; factText: string; confidence: string; title: string; summary: string; topics: string }>({
    factType: 'health', factText: '', confidence: '0.8', title: '', summary: '', topics: ''
  })

  const loadUsers = async () => {
    try {
      const all = await listUsers(1, 100)
      // 关联记忆计数（有记忆的才显示计数,无记忆显示 0）
      const mem = await getMemoryUsers()
      const countMap = new Map(mem.map((m: any) => [m.userId, m]))
      setUsers(all.map((u: any) => ({
        userId: u.id, username: u.username, email: u.email, role: u.role,
        factCount: countMap.get(u.id)?.factCount ?? 0,
        episodeCount: countMap.get(u.id)?.episodeCount ?? 0,
      })))
    } catch { /* 忽略 */ }
  }

  useEffect(() => { loadUsers() }, [])

  const loadUser = async (u: MemUser) => {
    setSelected(u)
    setLoading(true)
    setMsg('')
    try {
      const [f, e] = await Promise.all([getMemoryFacts(u.userId), getMemoryEpisodes(u.userId)])
      setFacts(f)
      setEpisodes(e)
    } finally { setLoading(false) }
  }

  const refresh = async () => {
    if (!selected) return
    const [f, e] = await Promise.all([getMemoryFacts(selected.userId), getMemoryEpisodes(selected.userId)])
    setFacts(f)
    setEpisodes(e)
    await loadUsers()
    const us = await getMemoryUsers()
    const updated = us.find((x: any) => x.userId === selected.userId)
    if (updated) setSelected(updated)
  }

  // ===== 用户增删改 =====
  const openAddUser = () => {
    setUserForm({ username: '', password: '', role: 'user' })
    setUserEditor({ mode: 'add' })
  }
  const openEditUser = (u: MemUser) => {
    setUserForm({ username: u.username || '', password: '', role: u.role || 'user' })
    setUserEditor({ mode: 'edit', id: u.userId, username: u.username })
  }
  const saveUser = async () => {
    setMsg('')
    try {
      if (userEditor?.mode === 'add') {
        if (!userForm.username.trim() || !userForm.password.trim()) { setMsg('用户名和密码不能为空'); return }
        const res = await createUser({ username: userForm.username.trim(), password: userForm.password, role: userForm.role })
        if (!res.success) throw new Error(res.message)
      } else if (userEditor?.id) {
        const res = await updateUser(userEditor.id, {
          username: userForm.username.trim() || undefined,
          password: userForm.password || undefined,
          role: userForm.role,
        })
        if (!res.success) throw new Error(res.message)
      }
      setUserEditor(null)
      setMsg('✅ 用户已保存')
      await loadUsers()
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }
  const delUser = async (u: MemUser) => {
    if (!window.confirm(`确定删除用户 ${u.username || u.userId}？其记忆将一并删除。`)) return
    setMsg('')
    try {
      const res = await deleteUser(u.userId)
      if (!res.success) throw new Error(res.message)
      setMsg('✅ 用户已删除')
      if (selected?.userId === u.userId) setSelected(null)
      await loadUsers()
    } catch (e: any) { setMsg(`删除失败: ${e?.message || ''}`) }
  }

  // ===== 增 =====
  const openAdd = (kind: 'fact' | 'episode') => {
    setForm({ factType: 'health', factText: '', confidence: '0.8', title: '', summary: '', topics: '' })
    setEditor({ kind, mode: 'add' })
  }

  // ===== 改 =====
  const openEditFact = (f: Fact) => {
    setForm({ factType: f.factType || 'health', factText: f.factText || '', confidence: String(f.confidence ?? 0.8), title: '', summary: '', topics: '' })
    setEditor({ kind: 'fact', mode: 'edit', id: f.id })
  }
  const openEditEpisode = (e: Episode) => {
    setForm({ factType: 'health', factText: '', confidence: '0.8', title: e.title || '', summary: e.summary || '', topics: (e.topics || []).join(',') })
    setEditor({ kind: 'episode', mode: 'edit', id: e.id })
  }

  const save = async () => {
    if (!selected) return
    setMsg('')
    try {
      if (editor?.kind === 'fact') {
        const data = { userId: selected.userId, factType: form.factType, factText: form.factText, confidence: parseFloat(form.confidence) }
        if (editor.mode === 'add') await createMemoryFact(data)
        else if (editor.id) await updateMemoryFact(editor.id, data)
      } else {
        const data = { userId: selected.userId, title: form.title, summary: form.summary, topics: form.topics.split(',').map(s => s.trim()).filter(Boolean) }
        if (editor.mode === 'add') await createMemoryEpisode(data)
        else if (editor.id) await updateMemoryEpisode(editor.id, data)
      }
      setEditor(null)
      setMsg('✅ 已保存')
      await refresh()
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }

  // ===== 删 =====
  const delFact = async (id: string) => {
    await deleteMemoryFact(id)
    await refresh()
  }
  const delEpisode = async (id: string) => {
    await deleteMemoryEpisode(id)
    await refresh()
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">用户记忆</h1>
        <span className="ml-3 text-[11px] text-muted">查看 / 新增 / 编辑 / 删除用户记忆（Fact + Episode）</span>
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
              <span className="ml-auto flex gap-2">
                <button onClick={() => openAdd('fact')}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-md bg-accent text-white text-[11px] hover:opacity-90">
                  <Plus className="w-3 h-3" /> 新增 Fact
                </button>
                <button onClick={() => openAdd('episode')}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-md bg-accent text-white text-[11px] hover:opacity-90">
                  <Plus className="w-3 h-3" /> 新增 Episode
                </button>
              </span>
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
                      <div className="flex items-center gap-1.5 shrink-0 ml-2">
                        <button onClick={() => openEditEpisode(x)} className="text-muted hover:text-accent"><Pencil className="w-3.5 h-3.5" /></button>
                        <button onClick={() => delEpisode(x.id)} className="text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
            {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
          </div>
        ) : (
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2 text-xs text-muted">
                <Database className="w-4 h-4 text-accent" />
                共 {users.length} 个用户
              </div>
              <button onClick={openAddUser}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">
                <Plus className="w-3.5 h-3.5" /> 新增用户
              </button>
            </div>
            {users.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-muted">
                <Database className="w-12 h-12 mb-3" />
                <p className="text-sm">暂无用户</p>
              </div>
            ) : (
              <div className="bg-panel border border-border rounded-xl divide-y divide-border">
                {users.map(u => (
                  <div key={u.userId} className="flex items-center justify-between px-4 py-2.5 hover:bg-accent/5 transition-colors">
                    <button onClick={() => loadUser(u)} className="flex-1 text-left min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-medium text-text-primary">{u.username || u.userId}</span>
                        {u.role === 'admin' && <span className="text-[10px] px-1 py-0.5 rounded bg-vital/10 text-vital">admin</span>}
                      </div>
                      {u.email && <div className="text-[10px] text-muted">{u.email}</div>}
                    </button>
                    <div className="flex items-center gap-3 shrink-0 ml-2">
                      <span className="flex gap-2 text-[11px]">
                        <span className="text-accent">{u.factCount} Fact</span>
                        <span className="text-muted">{u.episodeCount} Episode</span>
                      </span>
                      <button onClick={() => openEditUser(u)} className="text-muted hover:text-accent" title="编辑用户"><Pencil className="w-3.5 h-3.5" /></button>
                      <button onClick={() => delUser(u)} className="text-muted hover:text-vital" title="删除用户"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
          </div>
        )}
      </div>

      {/* 新增/编辑用户弹层 */}
      {userEditor && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
          onClick={() => setUserEditor(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[400px] p-5" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">{userEditor.mode === 'add' ? '新增用户' : '编辑用户'}</h3>
              <button onClick={() => setUserEditor(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <label className="block text-xs">
                <span className="text-muted">用户名</span>
                <input value={userForm.username} onChange={e => setUserForm(f => ({ ...f, username: e.target.value }))}
                  placeholder="登录用户名"
                  className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
              </label>
              <label className="block text-xs">
                <span className="text-muted">密码{userEditor.mode === 'edit' ? '（留空则不修改）' : ''}</span>
                <input type="password" value={userForm.password} onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))}
                  placeholder={userEditor.mode === 'edit' ? '留空保持原密码' : '登录密码'}
                  className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
              </label>
              <label className="block text-xs">
                <span className="text-muted">角色</span>
                <select value={userForm.role} onChange={e => setUserForm(f => ({ ...f, role: e.target.value }))}
                  className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent">
                  {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </label>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setUserEditor(null)}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">取消</button>
              <button onClick={saveUser}
                className="px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">保存</button>
            </div>
          </div>
        </div>
      )}

      {/* 新增/编辑弹层 */}
      {editor && selected && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
          onClick={() => setEditor(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[480px] max-h-[85vh] overflow-y-auto p-5"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">
                {editor.mode === 'add' ? '新增' : '编辑'} {editor.kind === 'fact' ? 'Fact' : 'Episode'}
                <span className="text-muted font-normal text-xs ml-2">用户: {selected.username || selected.userId}</span>
              </h3>
              <button onClick={() => setEditor(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>

            {editor.kind === 'fact' ? (
              <div className="space-y-3">
                <label className="block text-xs">
                  <span className="text-muted">类型</span>
                  <select value={form.factType} onChange={e => setForm(f => ({ ...f, factType: e.target.value }))}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent">
                    {FACT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="text-muted">内容</span>
                  <textarea value={form.factText} onChange={e => setForm(f => ({ ...f, factText: e.target.value }))}
                    placeholder="如: 患有高血压 3 年"
                    className="mt-0.5 w-full h-20 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent resize-y" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">置信度（0~1）</span>
                  <input type="number" step="0.05" min="0" max="1" value={form.confidence}
                    onChange={e => setForm(f => ({ ...f, confidence: e.target.value }))}
                    className="mt-0.5 w-24 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
              </div>
            ) : (
              <div className="space-y-3">
                <label className="block text-xs">
                  <span className="text-muted">标题</span>
                  <input value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                    placeholder="如: 高血压用药咨询"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">摘要</span>
                  <textarea value={form.summary} onChange={e => setForm(f => ({ ...f, summary: e.target.value }))}
                    placeholder="情节摘要 1-2 句"
                    className="mt-0.5 w-full h-16 px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent resize-y" />
                </label>
                <label className="block text-xs">
                  <span className="text-muted">标签（逗号分隔）</span>
                  <input value={form.topics} onChange={e => setForm(f => ({ ...f, topics: e.target.value }))}
                    placeholder="如: 高血压,用药"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                </label>
              </div>
            )}

            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setEditor(null)}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">取消</button>
              <button onClick={save}
                className="px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
