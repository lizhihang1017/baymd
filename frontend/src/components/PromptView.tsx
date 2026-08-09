import { useState, useEffect } from 'react'
import { Loader2, Save, RotateCcw } from 'lucide-react'
import { getPromptScenes, getConfig, saveConfig } from '../api/baymd'
import type { PromptSceneMeta } from '../api/baymd'

interface Draft {
  system: string
  user: string
  temperature: string
  maxTokens: string
  topP: string
}

function fmt(v: number | null | undefined): string {
  return v === null || v === undefined ? '' : String(v)
}

/** 独立「提示词」配置板块 — 每个 LLM 调用场景的 system/user 提示词 + 温度/最大token/topP 超参，默认预填当前内置值 */
export default function PromptView() {
  const [scenes, setScenes] = useState<PromptSceneMeta[]>([])
  const [active, setActive] = useState('')
  const [draft, setDraft] = useState<Record<string, Draft>>({})
  const [loaded, setLoaded] = useState(false)
  const [msg, setMsg] = useState('')
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoaded(false)
    try {
      const meta = await getPromptScenes()
      // 合并 DB 里已保存的覆盖（如果有）
      let saved: Record<string, any> = {}
      try {
        const p = await getConfig('prompt')
        if (p) saved = JSON.parse(p)
      } catch { /* 无保存配置 */ }

      const d: Record<string, Draft> = {}
      for (const s of meta) {
        const cur = saved[s.scene] && typeof saved[s.scene] === 'object'
          ? saved[s.scene] : {}
        d[s.scene] = {
          system: cur.system ?? s.defaultSystem ?? '',
          user: cur.user ?? s.defaultUser ?? '',
          temperature: cur.temperature !== undefined ? fmt(cur.temperature) : fmt(s.defaultTemperature),
          maxTokens: cur.max_tokens !== undefined ? fmt(cur.max_tokens) : fmt(s.defaultMaxTokens),
          topP: cur.top_p !== undefined ? fmt(cur.top_p) : fmt(s.defaultTopP),
        }
      }
      setScenes(meta)
      setDraft(d)
      setActive(meta[0]?.scene || '')
      setLoaded(true)
    } catch (e: any) {
      setMsg(`加载失败: ${e?.message || ''}`)
      setLoaded(true)
    }
  }

  useEffect(() => { load() }, [])

  const upd = (field: keyof Draft, value: string) => {
    setDraft(prev => ({ ...prev, [active]: { ...prev[active], [field]: value } }))
  }

  const resetScene = () => {
    const s = scenes.find(x => x.scene === active)
    if (!s) return
    setDraft(prev => ({
      ...prev,
      [active]: {
        system: s.defaultSystem ?? '',
        user: s.defaultUser ?? '',
        temperature: fmt(s.defaultTemperature),
        maxTokens: fmt(s.defaultMaxTokens),
        topP: fmt(s.defaultTopP),
      }
    }))
  }

  const save = async () => {
    // 只有改动过（与默认不同）或显式配置的场景才写入；system+user 都空且无超参 → 跳过
    const out: Record<string, any> = {}
    for (const s of scenes) {
      const d = draft[s.scene]
      if (!d) continue
      const sys = d.system.trim()
      const usr = d.user.trim()
      const hasText = sys !== '' || usr !== ''
      const hasParams = d.temperature !== '' || d.maxTokens !== '' || d.topP !== ''
      if (!hasText && !hasParams) continue
      const isDefault = sys === (s.defaultSystem ?? '') && usr === (s.defaultUser ?? '')
        && d.temperature === fmt(s.defaultTemperature) && d.maxTokens === fmt(s.defaultMaxTokens)
        && d.topP === fmt(s.defaultTopP)
      if (isDefault) continue

      const entry: Record<string, any> = {}
      if (sys) entry.system = sys
      if (usr) entry.user = usr
      if (d.temperature !== '') entry.temperature = parseFloat(d.temperature)
      if (d.maxTokens !== '') entry.max_tokens = parseInt(d.maxTokens, 10)
      if (d.topP !== '') entry.top_p = parseFloat(d.topP)
      out[s.scene] = entry
    }
    setSaving(true)
    try {
      const res = await saveConfig('prompt', JSON.stringify(out, null, 2))
      if (!res.success) throw new Error(res.message)
      setMsg(`✅ 已保存 ${Object.keys(out).length} 个场景（提示词 + 超参），重启后端后生效`)
    } catch (e: any) {
      setMsg(`保存失败: ${e?.message || ''}`)
    } finally {
      setSaving(false)
    }
  }

  const cur = draft[active]

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">LLM 提示词与超参数</h1>
        <span className="ml-3 text-[11px] text-muted">按 LLM 调用场景配置 system / user 提示词与温度 / 最大 token / topP · DB 覆盖，重启生效</span>
      </header>

      <div className="flex-1 min-h-0 flex overflow-hidden">
        {/* 左侧场景列表 */}
        <div className="w-52 shrink-0 border-r border-border overflow-y-auto p-2 space-y-1">
          {!loaded ? (
            <div className="flex items-center gap-2 text-xs text-muted p-2"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
          ) : scenes.map(s => (
            <button key={s.scene} onClick={() => setActive(s.scene)}
              className={`w-full text-left px-2.5 py-2 rounded-lg text-xs transition-colors ${
                active === s.scene ? 'bg-accent/10 text-accent' : 'text-muted hover:bg-accent/5 hover:text-text-primary'
              }`}>
              <div className="font-medium">{s.label}</div>
              <div className="text-[10px] text-muted/70 truncate">{s.scene}</div>
            </button>
          ))}
        </div>

        {/* 右侧编辑区 */}
        <div className="flex-1 min-h-0 overflow-y-auto p-5">
          {!loaded ? (
            <p className="text-xs text-muted">加载中...</p>
          ) : !cur ? null : (() => {
            const meta = scenes.find(s => s.scene === active)!
            return (
              <div className="max-w-4xl">
                <div className="flex items-start justify-between mb-1">
                  <div>
                    <h2 className="text-sm font-semibold">{meta.label}</h2>
                    <p className="text-[11px] text-muted mt-0.5">{meta.description}</p>
                  </div>
                  <button onClick={resetScene}
                    className="flex items-center gap-1 px-2 py-1 rounded-md border border-border text-[11px] text-muted hover:text-accent transition-colors shrink-0">
                    <RotateCcw className="w-3 h-3" /> 恢复默认
                  </button>
                </div>
                <p className="text-[11px] text-muted mb-3">
                  可用占位符：{(meta.slots || []).length
                    ? meta.slots.map(s => `{${s}}`).join('  ')
                    : '无'}
                </p>

                {/* 超参数 */}
                <div className="grid grid-cols-3 gap-3 mb-4">
                  <label className="block">
                    <span className="text-[11px] text-muted">温度 temperature（内置 {meta.defaultTemperature ?? '—'}）</span>
                    <input value={cur.temperature} onChange={e => upd('temperature', e.target.value)}
                      placeholder="留空=内置"
                      className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                  </label>
                  <label className="block">
                    <span className="text-[11px] text-muted">最大 token（内置 {meta.defaultMaxTokens ?? '—'}）</span>
                    <input value={cur.maxTokens} onChange={e => upd('maxTokens', e.target.value)}
                      placeholder="留空=内置"
                      className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                  </label>
                  <label className="block">
                    <span className="text-[11px] text-muted">topP（内置 {meta.defaultTopP ?? '—'}）</span>
                    <input value={cur.topP} onChange={e => upd('topP', e.target.value)}
                      placeholder="留空=内置"
                      className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                  </label>
                </div>

                {/* 提示词 */}
                <label className="block mb-1">
                  <span className="text-[11px] text-muted">系统提示词 system</span>
                  <textarea value={cur.system} onChange={e => upd('system', e.target.value)} spellCheck={false}
                    className="mt-0.5 w-full h-44 px-3 py-2 rounded-lg border border-border bg-white font-mono text-[11px] leading-relaxed focus:outline-none focus:border-accent resize-y" />
                </label>
                <label className="block mb-1">
                  <span className="text-[11px] text-muted">用户提示词 user</span>
                  <textarea value={cur.user} onChange={e => upd('user', e.target.value)} spellCheck={false}
                    className="mt-0.5 w-full h-32 px-3 py-2 rounded-lg border border-border bg-white font-mono text-[11px] leading-relaxed focus:outline-none focus:border-accent resize-y" />
                </label>

                <div className="flex items-center gap-3 mt-4">
                  <button onClick={save} disabled={saving}
                    className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90 disabled:opacity-50">
                    <Save className="w-3.5 h-3.5" /> {saving ? '保存中...' : '保存全部场景'}
                  </button>
                  <span className="text-[11px] text-muted">保存时仅写入与默认不同的场景</span>
                </div>
                {msg && <p className="text-[11px] text-accent mt-2">{msg}</p>}
              </div>
            )
          })()}
        </div>
      </div>
    </div>
  )
}
