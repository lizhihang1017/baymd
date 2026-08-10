import { useState } from 'react'
import { HeartPulse, Loader2, X, User, Lock } from 'lucide-react'
import { login, register, setAuthToken } from '../api/baymd'

/** 用户端登录/注册弹层 */
export default function UserAuth({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!username.trim() || !password) { setError('请输入用户名和密码'); return }
    if (mode === 'register') {
      if (password !== confirm) { setError('两次输入的密码不一致'); return }
      setLoading(true)
      try {
        const res = await register(username.trim(), password)
        if (!res.success) throw new Error(res.message || '注册失败')
        // 注册成功后自动登录
        const loginRes = await login(username.trim(), password)
        if (!loginRes.success) throw new Error(loginRes.message || '自动登录失败')
        setAuthToken(loginRes.data.token)
        onSuccess()
      } catch (err: any) {
        setError(err?.message || '操作失败')
      } finally { setLoading(false) }
    } else {
      setLoading(true)
      try {
        const res = await login(username.trim(), password)
        if (!res.success) throw new Error(res.message || '登录失败')
        setAuthToken(res.data.token)
        onSuccess()
      } catch (err: any) {
        setError(err?.message || '登录失败')
      } finally { setLoading(false) }
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-80 p-6" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <HeartPulse className="w-5 h-5 text-u-amber" />
            <h2 className="text-sm font-semibold text-u-text">
              {mode === 'login' ? '登录' : '注册账号'}
            </h2>
          </div>
          <button onClick={onClose} className="text-u-muted hover:text-u-text transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={submit} className="space-y-3">
          <label className="block">
            <span className="text-[11px] text-u-muted">用户名</span>
            <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
              <User className="w-4 h-4 text-u-muted shrink-0" />
              <input value={username} onChange={e => setUsername(e.target.value)}
                autoComplete="username" spellCheck={false}
                className="flex-1 bg-transparent py-2 text-sm text-u-text outline-none" />
            </div>
          </label>
          <label className="block">
            <span className="text-[11px] text-u-muted">密码</span>
            <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
              <Lock className="w-4 h-4 text-u-muted shrink-0" />
              <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                className="flex-1 bg-transparent py-2 text-sm text-u-text outline-none" />
            </div>
          </label>
          {mode === 'register' && (
            <label className="block">
              <span className="text-[11px] text-u-muted">确认密码</span>
              <div className="mt-1 flex items-center gap-2 border border-u-border rounded-lg px-3 focus-within:border-u-muted transition-colors">
                <Lock className="w-4 h-4 text-u-muted shrink-0" />
                <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)}
                  autoComplete="new-password"
                  className="flex-1 bg-transparent py-2 text-sm text-u-text outline-none" />
              </div>
            </label>
          )}

          {error && <p className="text-[11px] text-vital bg-vital/5 rounded-lg px-3 py-2" role="alert">{error}</p>}

          <button type="submit" disabled={loading}
            className="w-full py-2.5 rounded-lg bg-u-send text-white text-sm font-medium hover:bg-u-send-hover
              disabled:opacity-50 transition-colors flex items-center justify-center gap-2">
            {loading && <Loader2 className="w-4 h-4 animate-spin" />}
            {mode === 'login' ? '登录' : '注册'}
          </button>

          <p className="text-center text-[11px] text-u-muted">
            {mode === 'login' ? (
              <>还没有账号? <button type="button" onClick={() => { setMode('register'); setError('') }} className="text-u-amber hover:underline">注册</button></>
            ) : (
              <>已有账号? <button type="button" onClick={() => { setMode('login'); setError('') }} className="text-u-amber hover:underline">登录</button></>
            )}
          </p>
        </form>
      </div>
    </div>
  )
}
