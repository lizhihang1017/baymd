import { useState } from 'react'
import { HeartPulse, Loader2, User, Lock, Mail } from 'lucide-react'
import { login, register, setAuthToken } from '../api/baymd'

/** 全站登录门 — 未登录访问任何路径都显示此页(用户端风格) */
export default function UserLoginGate({ onSuccess }: { onSuccess: () => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!username.trim() || !password) { setError('请输入用户名和密码'); return }
    if (mode === 'register') {
      if (password !== confirm) { setError('两次输入的密码不一致'); return }
      if (!email.trim() || !/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(email.trim())) { setError('请填写有效的邮箱地址'); return }
    }
    setLoading(true)
    try {
      if (mode === 'register') {
        const res = await register(username.trim(), password, email.trim())
        if (!res.success) throw new Error(res.message || '注册失败')
      }
      const loginRes = await login(username.trim(), password)
      if (!loginRes.success) throw new Error(loginRes.message || '登录失败')
      setAuthToken(loginRes.data.token)
      onSuccess()
    } catch (err: any) {
      setError(err?.message || '操作失败')
    } finally { setLoading(false) }
  }

  return (
    <div className="h-screen flex bg-u-bg">
      <div className="m-auto w-full max-w-sm px-6">
        {/* 品牌 */}
        <div className="flex flex-col items-center mb-8">
          <img src="/baymax.png" alt="BayMD"
            className="w-20 h-24 object-contain mb-3 animate-clinical-pulse" />
          <h1 className="text-xl font-semibold text-u-text">BayMD 健康管家</h1>
          <p className="text-xs text-u-muted mt-1">您的 24 小时健康助手 · 支持用户名或邮箱登录</p>
        </div>

        {/* 表单 */}
        <div className="bg-white rounded-2xl shadow-lg border border-u-border p-6 space-y-4">
          <div className="flex rounded-lg bg-u-bg p-0.5">
            {(['login', 'register'] as const).map(m => (
              <button key={m} onClick={() => { setMode(m); setError('') }}
                className={`flex-1 py-2 rounded-md text-sm font-medium transition-colors ${
                  mode === m ? 'bg-white shadow-sm text-u-text' : 'text-u-muted hover:text-u-text'
                }`}>
                {m === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="space-y-3">
            <label className="block">
              <span className="text-[11px] text-u-muted">用户名</span>
              <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
                <User className="w-4 h-4 text-u-muted shrink-0" />
                <input value={username} onChange={e => setUsername(e.target.value)}
                  autoComplete="username" spellCheck={false} autoFocus
                  className="flex-1 bg-transparent py-2.5 text-sm text-u-text outline-none" />
              </div>
            </label>
            {mode === 'register' && (
              <label className="block">
                <span className="text-[11px] text-u-muted">邮箱 <span className="text-vital">*</span></span>
                <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
                  <Mail className="w-4 h-4 text-u-muted shrink-0" />
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                    autoComplete="email" spellCheck={false}
                    placeholder="用于接收健康随访提醒"
                    className="flex-1 bg-transparent py-2.5 text-sm text-u-text outline-none placeholder:text-u-muted" />
                </div>
              </label>
            )}
            <label className="block">
              <span className="text-[11px] text-u-muted">密码</span>
              <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
                <Lock className="w-4 h-4 text-u-muted shrink-0" />
                <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  className="flex-1 bg-transparent py-2.5 text-sm text-u-text outline-none" />
              </div>
            </label>
            {mode === 'register' && (
              <label className="block">
                <span className="text-[11px] text-u-muted">确认密码</span>
                <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
                  <Lock className="w-4 h-4 text-u-muted shrink-0" />
                  <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)}
                    autoComplete="new-password"
                    className="flex-1 bg-transparent py-2.5 text-sm text-u-text outline-none" />
                </div>
              </label>
            )}

            {error && <p className="text-[11px] text-vital bg-vital/5 rounded-lg px-3 py-2" role="alert">{error}</p>}

            <button type="submit" disabled={loading}
              className="w-full py-2.5 rounded-lg bg-u-send text-white text-sm font-medium hover:bg-u-send-hover
                disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
              {loading && <Loader2 className="w-4 h-4 animate-spin" />}
              {mode === 'login' ? '登录' : '注册并登录'}
            </button>
          </form>

          <p className="text-center text-[10px] text-u-muted">
            BayMD 健康管家 · 回答仅供健康参考,紧急情况请及时就医
          </p>
        </div>
      </div>
    </div>
  )
}
