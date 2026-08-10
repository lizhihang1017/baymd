import { useState, useEffect, useRef } from 'react'
import { ChevronUp, Loader2, X, Camera, KeyRound, User as UserIcon, LogOut, Brain, Sparkles, Trash2 } from 'lucide-react'
import { getAuthToken, setAuthToken, clearAuthToken, getMyMemory, deleteMyFact, deleteMyEpisode,
  uploadAvatar, changePassword, getCurrentUser } from '../api/baymd'

interface Fact { id: string; factType?: string; factText?: string; confidence?: number }
interface Episode { id: string; title?: string; summary?: string; topics?: string[] }
interface MemoryData { facts: Fact[]; episodes: Episode[] }

/** 用户端左下角头像菜单 — 修改密码/头像、查看/删除自己的画像、退出登录 */
export default function UserMenu({ onLogout }: { onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const [user, setUser] = useState<any>(null)
  const [memory, setMemory] = useState<MemoryData>({ facts: [], episodes: [] })
  const [panel, setPanel] = useState<'main' | 'password' | 'avatar' | 'memory'>('main')
  const [msg, setMsg] = useState('')
  const [loading, setLoading] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const isLoggedIn = !!getAuthToken()

  useEffect(() => {
    if (isLoggedIn) {
      getCurrentUser().then(u => setUser(u)).catch(() => {})
    }
  }, [isLoggedIn])

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const openPanel = async (p: 'main' | 'password' | 'avatar' | 'memory') => {
    setMsg('')
    if (p === 'memory') {
      setLoading(true)
      try {
        const d = await getMyMemory()
        setMemory(d)
      } catch (e: any) { setMsg(`加载画像失败: ${e?.message || ''}`) } finally { setLoading(false) }
    }
    setPanel(p)
  }

  // 改密码
  const [oldPwd, setOldPwd] = useState('')
  const [newPwd, setNewPwd] = useState('')
  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    setMsg('')
    try {
      const res = await changePassword(oldPwd, newPwd)
      if (!res.success) throw new Error(res.message || '修改失败')
      setMsg('✅ 密码已修改')
      setOldPwd(''); setNewPwd('')
    } catch (err: any) { setMsg(`修改失败: ${err?.message || ''}`) }
  }

  // 换头像
  const fileRef = useRef<HTMLInputElement>(null)
  const submitAvatar = async (f: File) => {
    setMsg('')
    try {
      const url = await uploadAvatar(f)
      setUser((u: any) => ({ ...u, avatar: url }))
      setMsg('✅ 头像已更新')
    } catch (err: any) { setMsg(`上传失败: ${err?.message || ''}`) }
  }

  // 删画像
  const delFact = async (id: string) => {
    await deleteMyFact(id)
    setMemory(m => ({ ...m, facts: m.facts.filter(f => f.id !== id) }))
  }
  const delEpisode = async (id: string) => {
    await deleteMyEpisode(id)
    setMemory(m => ({ ...m, episodes: m.episodes.filter(e => e.id !== id) }))
  }

  const logout = () => {
    clearAuthToken()
    setOpen(false)
    onLogout()  // 触发 App 重渲染 → 显示登录页
    window.location.href = '/'  // 退出后回首页
  }

  // 头像显示: 有 avatar 用 avatar,否则默认大白(裁上半身)
  const avatarSrc = user?.avatar || '/baymax.png'

  if (!isLoggedIn) return null

  return (
    <div ref={menuRef} className="relative">
      {/* 左下角头像按钮 */}
      <button onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-white/10 transition-colors w-full">
        <img src={avatarSrc} alt="头像"
          className="w-7 h-7 rounded-full object-cover object-top ring-2 ring-white/20" />
        <span className="flex-1 text-left text-sm text-white/80 truncate">{user?.username || '用户'}</span>
        <ChevronUp className={`w-3.5 h-3.5 text-white/40 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {/* 弹出菜单 */}
      {open && (
        <div className="absolute bottom-12 left-0 w-72 bg-white rounded-xl shadow-2xl overflow-hidden z-50 border border-u-border">
          {panel === 'main' && (
            <>
              <div className="flex items-center gap-3 p-4 border-b border-u-border">
                <img src={avatarSrc} alt="头像"
                  className="w-11 h-11 rounded-full object-cover object-top ring-2 ring-u-amber/40" />
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-u-text truncate">{user?.username || '用户'}</div>
                  <div className="text-[11px] text-u-muted">我的健康档案</div>
                </div>
              </div>
              <div className="py-1">
                <MenuItem icon={KeyRound} label="修改密码" onClick={() => openPanel('password')} />
                <MenuItem icon={Camera} label="更换头像" onClick={() => openPanel('avatar')} />
                <MenuItem icon={Brain} label="我的画像（记忆）" onClick={() => openPanel('memory')} />
              </div>
              <div className="border-t border-u-border py-1">
                <MenuItem icon={LogOut} label="退出登录" onClick={logout} danger />
              </div>
            </>
          )}

          {panel === 'password' && (
            <div className="p-4">
              <PanelHeader title="修改密码" onBack={() => setPanel('main')} />
              <form onSubmit={submitPassword} className="space-y-2 mt-3">
                <input type="password" value={oldPwd} onChange={e => setOldPwd(e.target.value)}
                  placeholder="原密码" autoComplete="current-password"
                  className="w-full px-3 py-2 rounded-lg border border-u-border text-sm focus:outline-none focus:border-u-muted" />
                <input type="password" value={newPwd} onChange={e => setNewPwd(e.target.value)}
                  placeholder="新密码" autoComplete="new-password"
                  className="w-full px-3 py-2 rounded-lg border border-u-border text-sm focus:outline-none focus:border-u-muted" />
                <button type="submit"
                  className="w-full py-2 rounded-lg bg-u-send text-white text-sm hover:bg-u-send-hover transition-colors">
                  确认修改
                </button>
              </form>
              {msg && <p className="text-[11px] text-accent-green mt-2">{msg}</p>}
            </div>
          )}

          {panel === 'avatar' && (
            <div className="p-4">
              <PanelHeader title="更换头像" onBack={() => setPanel('main')} />
              <div className="flex flex-col items-center gap-3 mt-3">
                <img src={avatarSrc} alt="当前头像"
                  className="w-20 h-20 rounded-full object-cover object-top ring-2 ring-u-amber/40" />
                <input ref={fileRef} type="file" accept="image/*" className="hidden"
                  onChange={e => { const f = e.target.files?.[0]; if (f) submitAvatar(f); e.target.value = '' }} />
                <button onClick={() => fileRef.current?.click()}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-u-send text-white text-sm hover:bg-u-send-hover transition-colors">
                  <Camera className="w-4 h-4" /> 选择图片
                </button>
                <p className="text-[10px] text-u-muted">支持 jpg / png / webp,最大 2MB</p>
                {msg && <p className="text-[11px] text-accent-green">{msg}</p>}
              </div>
            </div>
          )}

          {panel === 'memory' && (
            <div className="p-4 max-h-96 overflow-y-auto">
              <PanelHeader title="我的画像（记忆）" onBack={() => setPanel('main')} />
              <p className="text-[10px] text-u-muted mt-1 mb-3">系统从对话中记忆的关于您的信息 · 可与健康管家共享</p>
              {loading ? (
                <div className="flex items-center gap-2 text-xs text-u-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
              ) : (
                <div className="space-y-3">
                  {/* Facts */}
                  <div>
                    <div className="text-[11px] font-semibold text-u-text mb-1 flex items-center gap-1">
                      <Sparkles className="w-3 h-3 text-u-amber" /> 健康档案（{memory.facts.length}）
                    </div>
                    {memory.facts.length === 0 ? (
                      <p className="text-[11px] text-u-muted">暂无,多对话后会逐步积累</p>
                    ) : memory.facts.map(f => (
                      <div key={f.id} className="flex items-start justify-between gap-2 py-1.5 border-b border-u-border/50">
                        <div className="min-w-0">
                          <span className="text-[10px] text-u-muted">[{f.factType}]</span>
                          <p className="text-xs text-u-text">{f.factText}</p>
                        </div>
                        <button onClick={() => delFact(f.id)} className="text-u-muted hover:text-vital shrink-0">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                  {/* Episodes */}
                  <div>
                    <div className="text-[11px] font-semibold text-u-text mb-1 flex items-center gap-1">
                      <Brain className="w-3 h-3 text-u-amber" /> 对话情节（{memory.episodes.length}）
                    </div>
                    {memory.episodes.length === 0 ? (
                      <p className="text-[11px] text-u-muted">暂无</p>
                    ) : memory.episodes.map(e => (
                      <div key={e.id} className="flex items-start justify-between gap-2 py-1.5 border-b border-u-border/50">
                        <div className="min-w-0">
                          <div className="text-xs font-medium text-u-text">{e.title}</div>
                          <p className="text-[11px] text-u-muted truncate">{e.summary}</p>
                        </div>
                        <button onClick={() => delEpisode(e.id)} className="text-u-muted hover:text-vital shrink-0">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick, danger }: { icon: any; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick}
      className={`w-full flex items-center gap-2.5 px-4 py-2.5 text-sm transition-colors
        ${danger ? 'text-vital hover:bg-vital/5' : 'text-u-text hover:bg-u-bg'}`}>
      <Icon className="w-4 h-4 shrink-0" />
      {label}
    </button>
  )
}

function PanelHeader({ title, onBack }: { title: string; onBack: () => void }) {
  return (
    <div className="flex items-center justify-between">
      <h3 className="text-sm font-semibold text-u-text">{title}</h3>
      <button onClick={onBack} className="text-[11px] text-u-muted hover:text-u-text">← 返回</button>
    </div>
  )
}
