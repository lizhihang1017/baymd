import { useState, useEffect } from 'react'
import { Settings, Trash2, Shield, Activity, Mail } from 'lucide-react'
import { getSettings, clearMemory, getTraces, bindEmail, verifyEmail } from '../api/baymd'

export default function SettingsView() {
  const [settings, setSettings] = useState<any>(null)
  const [traces, setTraces] = useState<any[]>([])
  const [memoryCleared, setMemoryCleared] = useState(false)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [emailMsg, setEmailMsg] = useState('')

  useEffect(() => {
    getSettings().then(res => setSettings(res.data)).catch(() => {})
    getTraces(1, 5).then(res => setTraces(res.data?.records || [])).catch(() => {})
  }, [])

  const handleClearMemory = async () => {
    try {
      await clearMemory()
      setMemoryCleared(true)
      setTimeout(() => setMemoryCleared(false), 2000)
    } catch {}
  }

  const handleSendCode = async () => {
    setEmailMsg('')
    try {
      const res = await bindEmail(email)
      if (!res.success) throw new Error(res.message || '发送失败')
      setCodeSent(true)
      setEmailMsg('验证码已发送，请查收邮箱')
    } catch (e: any) {
      setEmailMsg(`发送失败: ${e?.message || '未知错误'}`)
    }
  }

  const handleVerify = async () => {
    setEmailMsg('')
    try {
      const res = await verifyEmail(email, code)
      if (!res.success) throw new Error(res.message || '验证失败')
      setEmailMsg(res.data === true ? '邮箱绑定成功' : '验证码错误')
      if (res.data === true) { setCode(''); setCodeSent(false) }
    } catch (e: any) {
      setEmailMsg(`验证失败: ${e?.message || '未知错误'}`)
    }
  }

  return (
    <div className="flex-1 flex flex-col bg-surface">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">系统设置</h1>
      </header>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* RAG Config */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Settings className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">RAG 配置</h2>
          </div>
          {settings?.rag ? (
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="text-muted">向量引擎</div>
              <div>{settings.rag.vector?.type || 'pg'}</div>
              <div className="text-muted">向量维度</div>
              <div>{settings.rag.default?.dimension || 1536}</div>
              <div className="text-muted">查询改写</div>
              <div>{settings.rag.queryRewrite?.enabled ? '开启' : '关闭'}</div>
              <div className="text-muted">限流</div>
              <div>{settings.rag.rateLimit?.global?.enabled ? '开启' : '关闭'}</div>
            </div>
          ) : (
            <p className="text-xs text-muted">加载中...</p>
          )}
        </section>

        {/* Memory */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-vital" />
            <h2 className="text-sm font-semibold">隐私与记忆</h2>
          </div>
          <button onClick={handleClearMemory}
            className="flex items-center gap-2 px-3 py-2 rounded-lg
              border border-vital text-vital text-xs hover:bg-vital/5 transition-colors">
            <Trash2 className="w-3.5 h-3.5" />
            {memoryCleared ? '记忆已清空' : '清空所有用户记忆'}
          </button>
          <p className="text-xs text-muted mt-2">
            清空后，系统将忘记所有关于您的事实和对话情节
          </p>
        </section>

        {/* Email Binding */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Mail className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">邮箱绑定（主动随访）</h2>
          </div>
          <div className="flex gap-2">
            <input
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="输入邮箱地址"
              className="flex-1 px-3 py-2 rounded-lg border border-border bg-white
                text-xs text-text-primary placeholder:text-muted focus:outline-none
                focus:ring-2 focus:ring-accent/20 focus:border-accent"
            />
            <button onClick={handleSendCode}
              className="px-3 py-2 rounded-lg bg-accent text-white text-xs hover:opacity-90
                transition-opacity disabled:opacity-40"
              disabled={!email.includes('@')}>
              发送验证码
            </button>
          </div>
          {codeSent && (
            <div className="flex gap-2 mt-2">
              <input
                value={code}
                onChange={e => setCode(e.target.value)}
                placeholder="6 位验证码"
                className="flex-1 px-3 py-2 rounded-lg border border-border bg-white
                  text-xs text-text-primary placeholder:text-muted focus:outline-none
                  focus:ring-2 focus:ring-accent/20 focus:border-accent"
              />
              <button onClick={handleVerify}
                className="px-3 py-2 rounded-lg border border-accent text-accent text-xs
                  hover:bg-accent/5 transition-colors">
                确认绑定
              </button>
            </div>
          )}
          {emailMsg && <p className="text-xs text-muted mt-2">{emailMsg}</p>}
          <p className="text-xs text-muted mt-2">
            绑定邮箱后可接收 BayMD 主动健康随访提醒（邮件不含病情细节）
          </p>
        </section>

        {/* Trace */}
        <section className="bg-panel border border-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Activity className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">最近链路</h2>
          </div>
          {traces.length > 0 ? (
            <div className="space-y-2">
              {traces.map((t: any) => (
                <div key={t.traceId} className="flex items-center justify-between text-xs py-1 border-b border-border last:border-0">
                  <span className="text-muted font-mono">{t.traceId?.slice(0, 8)}</span>
                  <span className="text-muted">{t.totalDurationMs}ms</span>
                  <span className={`${t.status === 'SUCCESS' ? 'text-accent' : 'text-vital'}`}>
                    {t.status}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-xs text-muted">暂无链路数据</p>
          )}
        </section>
      </div>
    </div>
  )
}
