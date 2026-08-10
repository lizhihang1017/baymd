import { useState } from 'react'
import { HeartPulse, Loader2, Lock, User } from 'lucide-react'
import { login, setAuthToken } from '../api/baymd'

/** 管理后台登录页 — 默认账号 admin / admin */
export default function LoginPage({ onSuccess }: { onSuccess: () => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) { setError('请输入用户名和密码'); return }
    setLoading(true)
    setError('')
    try {
      const res = await login(username.trim(), password)
      if (!res.success) throw new Error(res.message || '登录失败')
      setAuthToken(res.data.token)
      onSuccess()
    } catch (err: any) {
      setError(err?.message || '登录失败,请检查用户名和密码')
    } finally { setLoading(false) }
  }

  return (
    <div className="h-screen flex bg-bg-deep relative overflow-hidden">
      {/* 背景光晕 */}
      <div className="absolute -top-32 -right-32 w-96 h-96 rounded-full bg-accent/10 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-32 -left-32 w-96 h-96 rounded-full bg-accent-green/10 blur-3xl pointer-events-none" />

      <div className="relative m-auto w-full max-w-sm px-6">
        {/* 品牌 */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-14 h-14 rounded-2xl bg-accent/15 border border-accent/30 flex items-center justify-center mb-3">
            <HeartPulse className="w-7 h-7 text-accent-bright" />
          </div>
          <h1 className="text-xl font-semibold text-white tracking-wide">BayMD 管理控制台</h1>
          <p className="text-[11px] text-white/35 mt-1">AI 私人医生 · 管理员登录</p>
          {/* 心电线 */}
          <svg viewBox="0 0 120 16" className="w-28 h-4 mt-2 opacity-60" aria-hidden>
            <polyline className="ekg-line" fill="none" stroke="#22D3EE" strokeWidth="1.5"
              points="0,8 20,8 30,8 36,8 42,2 48,14 54,8 70,8 78,8 120,8" />
          </svg>
        </div>

        {/* 表单 */}
        <form onSubmit={submit} className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-6 space-y-4">
          <label className="block">
            <span className="text-[11px] text-white/50">用户名</span>
            <div className="mt-1 flex items-center gap-2 bg-white/8 border border-white/10 rounded-lg px-3 focus-within:border-accent/50 transition-colors">
              <User className="w-4 h-4 text-white/30 shrink-0" />
              <input value={username} onChange={e => setUsername(e.target.value)}
                autoComplete="username" spellCheck={false}
                className="flex-1 bg-transparent py-2.5 text-sm text-white placeholder-white/25 outline-none"
                placeholder="admin" />
            </div>
          </label>
          <label className="block">
            <span className="text-[11px] text-white/50">密码</span>
            <div className="mt-1 flex items-center gap-2 bg-white/8 border border-white/10 rounded-lg px-3 focus-within:border-accent/50 transition-colors">
              <Lock className="w-4 h-4 text-white/30 shrink-0" />
              <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                autoComplete="current-password"
                className="flex-1 bg-transparent py-2.5 text-sm text-white placeholder-white/25 outline-none"
                placeholder="••••••" />
            </div>
          </label>

          {error && <p className="text-[11px] text-vital bg-vital/10 rounded-lg px-3 py-2" role="alert">{error}</p>}

          <button type="submit" disabled={loading}
            className="w-full py-2.5 rounded-lg bg-accent text-white text-sm font-medium hover:bg-accent-dark transition-colors
              disabled:opacity-50 flex items-center justify-center gap-2">
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
            {loading ? '登录中...' : '登录'}
          </button>

          <p className="text-center text-[10px] text-white/25">默认账号 admin / admin</p>
        </form>
      </div>
    </div>
  )
}
