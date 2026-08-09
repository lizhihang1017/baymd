import { useState } from 'react'
import { Plus, Trash2, Save, RefreshCw } from 'lucide-react'
import { getConfig, saveConfig } from '../api/baymd'

// ===================== 通用表单控件 =====================

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex items-center justify-between gap-3 py-1.5 text-xs border-b border-border/60 last:border-0">
      <span className="text-muted shrink-0">{label}</span>
      {children}
    </label>
  )
}

function Num({ value, onChange, step = 1 }: { value: number; onChange: (v: number) => void; step?: number }) {
  return (
    <input type="number" step={step} value={value}
      onChange={e => onChange(Number(e.target.value))}
      className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right
        focus:outline-none focus:border-accent" />
  )
}

function Txt({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <input type="text" value={value} placeholder={placeholder}
      onChange={e => onChange(e.target.value)}
      className="flex-1 min-w-0 px-2 py-1 rounded-md border border-border bg-white text-xs
        focus:outline-none focus:border-accent" />
  )
}

function Sel({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: [string, string][] }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      className="w-28 px-2 py-1 rounded-md border border-border bg-white text-xs
        focus:outline-none focus:border-accent">
      {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
    </select>
  )
}

function FormShell({ title, desc, children, onSave, onLoad, msg }: {
  title: string; desc: string; children: React.ReactNode;
  onSave: () => void; onLoad: () => void; msg: string
}) {
  return (
    <div className="mt-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold">{title}</span>
        <div className="flex gap-1.5">
          <button onClick={onLoad} title="从数据库加载当前配置"
            className="px-2 py-1 rounded-md border border-border text-xs text-muted
              hover:text-accent hover:border-accent/40 transition-colors flex items-center gap-1">
            <RefreshCw className="w-3 h-3" /> 加载
          </button>
          <button onClick={onSave} title="保存到数据库，重启生效"
            className="px-2 py-1 rounded-md bg-accent text-white text-xs hover:opacity-90
              transition-opacity flex items-center gap-1">
            <Save className="w-3 h-3" /> 保存
          </button>
        </div>
      </div>
      <p className="text-[11px] text-muted mb-1">{desc}</p>
      <div className="bg-white rounded-lg border border-border px-3 py-1">
        {children}
      </div>
      {msg && <p className="text-[11px] text-muted mt-1">{msg}</p>}
    </div>
  )
}

// ===================== RAG 超参表单 =====================

const MEMORY_STRATEGIES: [string, string][] = [
  ['none', '无记忆'],
  ['sliding_window', '滑动窗口'],
  ['summary_compression', '摘要压缩'],
  ['semantic', '语义记忆'],
]

interface RagForm {
  queryRewrite: { enabled: boolean }
  memory: { strategy: string; historyKeepTurns: number; summaryStartTurns: number; summaryEnabled: boolean }
  search: { defaultTopK: number; vectorGlobalConfidenceThreshold: number; intentDirectedMinIntentScore: number }
  react: { enabled: boolean; maxIterations: number }
}

const RAG_DEFAULT: RagForm = {
  queryRewrite: { enabled: true },
  memory: { strategy: 'sliding_window', historyKeepTurns: 4, summaryStartTurns: 5, summaryEnabled: true },
  search: { defaultTopK: 10, vectorGlobalConfidenceThreshold: 0.6, intentDirectedMinIntentScore: 0.4 },
  react: { enabled: true, maxIterations: 5 },
}

export function RagConfigForm() {
  const [cfg, setCfg] = useState<RagForm>(RAG_DEFAULT)
  const [msg, setMsg] = useState('')
  const set = (path: (c: RagForm) => RagForm) => setCfg(path)

  const load = async () => {
    try {
      const existing = await getConfig('rag')
      if (existing) { setCfg({ ...RAG_DEFAULT, ...JSON.parse(existing) }); setMsg('已加载数据库配置') }
      else setMsg('数据库暂无 rag 配置，显示默认值')
    } catch { setMsg('加载失败') }
  }
  const save = async () => {
    try {
      const res = await saveConfig('rag', JSON.stringify(cfg))
      if (!res.success) throw new Error(res.message || '保存失败')
      setMsg('✅ 已保存，重启后端后生效')
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }

  return (
    <FormShell title="RAG 超参数" desc="查询改写 / 记忆 / 检索 / ReAct" onSave={save} onLoad={load} msg={msg}>
      <Field label="查询改写">
        <Sel value={String(cfg.queryRewrite.enabled)} onChange={v => set(c => ({ ...c, queryRewrite: { enabled: v === 'true' } }))}
          options={[['true', '开启'], ['false', '关闭']]} />
      </Field>
      <Field label="记忆策略">
        <Sel value={cfg.memory.strategy} onChange={v => set(c => ({ ...c, memory: { ...c.memory, strategy: v } }))}
          options={MEMORY_STRATEGIES} />
      </Field>
      <Field label="历史保留轮数">
        <Num value={cfg.memory.historyKeepTurns} onChange={v => set(c => ({ ...c, memory: { ...c.memory, historyKeepTurns: v } }))} />
      </Field>
      <Field label="摘要开始轮数">
        <Num value={cfg.memory.summaryStartTurns} onChange={v => set(c => ({ ...c, memory: { ...c.memory, summaryStartTurns: v } }))} />
      </Field>
      <Field label="摘要压缩">
        <Sel value={String(cfg.memory.summaryEnabled)} onChange={v => set(c => ({ ...c, memory: { ...c.memory, summaryEnabled: v === 'true' } }))}
          options={[['true', '开启'], ['false', '关闭']]} />
      </Field>
      <Field label="检索默认 TopK">
        <Num value={cfg.search.defaultTopK} onChange={v => set(c => ({ ...c, search: { ...c.search, defaultTopK: v } }))} />
      </Field>
      <Field label="全局置信度阈值">
        <Num value={cfg.search.vectorGlobalConfidenceThreshold} step={0.05}
          onChange={v => set(c => ({ ...c, search: { ...c.search, vectorGlobalConfidenceThreshold: v } }))} />
      </Field>
      <Field label="意图定向最低分">
        <Num value={cfg.search.intentDirectedMinIntentScore} step={0.05}
          onChange={v => set(c => ({ ...c, search: { ...c.search, intentDirectedMinIntentScore: v } }))} />
      </Field>
      <Field label="ReAct 模式">
        <Sel value={String(cfg.react.enabled)} onChange={v => set(c => ({ ...c, react: { ...c.react, enabled: v === 'true' } }))}
          options={[['true', '开启'], ['false', '关闭']]} />
      </Field>
      <Field label="ReAct 最大迭代">
        <Num value={cfg.react.maxIterations} onChange={v => set(c => ({ ...c, react: { ...c.react, maxIterations: v } }))} />
      </Field>
    </FormShell>
  )
}

// ===================== AI / 模型配置表单 =====================

interface ProviderRow { name: string; url: string; apiKey: string }
interface CandidateRow { id: string; provider: string; model: string; priority: number; supportsThinking: boolean }
interface AiForm {
  providers: ProviderRow[]
  defaultModel: string
  deepThinkingModel: string
  fastModel: string
  candidates: CandidateRow[]
}

const AI_DEFAULT: AiForm = {
  providers: [{ name: 'bailian', url: '', apiKey: '' }],
  defaultModel: 'qwen3.7-plus',
  deepThinkingModel: 'qwen3.7-plus',
  fastModel: 'qwen-plus-2025-07-28',
  candidates: [{ id: 'qwen3.7-plus', provider: 'bailian', model: 'qwen3.7-plus', priority: 1, supportsThinking: true }],
}

export function AiConfigForm() {
  const [cfg, setCfg] = useState<AiForm>(AI_DEFAULT)
  const [msg, setMsg] = useState('')

  const load = async () => {
    try {
      const existing = await getConfig('ai')
      if (existing) {
        const p = JSON.parse(existing)
        // 反序列化 providers map → 行数组
        const providers = Object.entries(p.providers || {}).map(([name, v]: any) => ({
          name, url: v.url || '', apiKey: v.apiKey || ''
        }))
        setCfg({
          providers: providers.length ? providers : AI_DEFAULT.providers,
          defaultModel: p.chat?.defaultModel ?? AI_DEFAULT.defaultModel,
          deepThinkingModel: p.chat?.deepThinkingModel ?? AI_DEFAULT.deepThinkingModel,
          fastModel: p.chat?.fastModel ?? AI_DEFAULT.fastModel,
          candidates: p.chat?.candidates || AI_DEFAULT.candidates,
        })
        setMsg('已加载数据库配置')
      } else setMsg('数据库暂无 ai 配置，显示默认值')
    } catch { setMsg('加载失败') }
  }

  const save = async () => {
    try {
      // providers 行 → map
      const providers: Record<string, any> = {}
      cfg.providers.forEach(p => {
        if (p.name) providers[p.name] = { url: p.url || undefined, apiKey: p.apiKey || undefined }
      })
      const payload = {
        providers,
        chat: {
          defaultModel: cfg.defaultModel || undefined,
          deepThinkingModel: cfg.deepThinkingModel || undefined,
          fastModel: cfg.fastModel || undefined,
          candidates: cfg.candidates.filter(c => c.id),
        },
      }
      const res = await saveConfig('ai', JSON.stringify(payload))
      if (!res.success) throw new Error(res.message || '保存失败')
      setMsg('✅ 已保存，重启后端后生效')
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }

  return (
    <FormShell title="AI / 模型配置" desc="模型供应商 API Key / 模型候选 / 默认模型" onSave={save} onLoad={load} msg={msg}>
      {/* 供应商 */}
      <div className="py-1.5 border-b border-border/60">
        <div className="flex items-center justify-between mb-1">
          <span className="text-xs text-muted">模型供应商</span>
          <button onClick={() => setCfg(c => ({ ...c, providers: [...c.providers, { name: '', url: '', apiKey: '' }] }))}
            className="text-[11px] text-accent hover:underline flex items-center gap-0.5">
            <Plus className="w-3 h-3" /> 添加
          </button>
        </div>
        {cfg.providers.map((p, i) => (
          <div key={i} className="flex items-center gap-1.5 py-1">
            <Txt value={p.name} placeholder="名称" onChange={v => setCfg(c => {
              const a = [...c.providers]; a[i] = { ...a[i], name: v }; return { ...c, providers: a }
            })} />
            <Txt value={p.url} placeholder="Base URL" onChange={v => setCfg(c => {
              const a = [...c.providers]; a[i] = { ...a[i], url: v }; return { ...c, providers: a }
            })} />
            <Txt value={p.apiKey} placeholder="API Key" onChange={v => setCfg(c => {
              const a = [...c.providers]; a[i] = { ...a[i], apiKey: v }; return { ...c, providers: a }
            })} />
            <button onClick={() => setCfg(c => ({ ...c, providers: c.providers.filter((_, k) => k !== i) }))}
              className="text-muted hover:text-vital shrink-0">
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>

      {/* 默认模型 */}
      <div className="pt-1">
        <Field label="默认模型">
          <Txt value={cfg.defaultModel} onChange={v => setCfg(c => ({ ...c, defaultModel: v }))} placeholder="如 qwen3.7-plus" />
        </Field>
        <Field label="深度思考模型">
          <Txt value={cfg.deepThinkingModel} onChange={v => setCfg(c => ({ ...c, deepThinkingModel: v }))} placeholder="如 qwen3.7-plus" />
        </Field>
        <Field label="快速模型（改写/意图）">
          <Txt value={cfg.fastModel} onChange={v => setCfg(c => ({ ...c, fastModel: v }))} placeholder="如 qwen-plus-2025-07-28" />
        </Field>
      </div>

      {/* 候选模型 */}
      <div className="py-1.5">
        <div className="flex items-center justify-between mb-1">
          <span className="text-xs text-muted">模型候选</span>
          <button onClick={() => setCfg(c => ({ ...c, candidates: [...c.candidates, { id: '', provider: 'bailian', model: '', priority: 1, supportsThinking: false }] }))}
            className="text-[11px] text-accent hover:underline flex items-center gap-0.5">
            <Plus className="w-3 h-3" /> 添加
          </button>
        </div>
        {cfg.candidates.map((cd, i) => (
          <div key={i} className="flex items-center gap-1.5 py-1">
            <Txt value={cd.id} placeholder="id" onChange={v => setCfg(c => {
              const a = [...c.candidates]; a[i] = { ...a[i], id: v }; return { ...c, candidates: a }
            })} />
            <Txt value={cd.provider} placeholder="provider" onChange={v => setCfg(c => {
              const a = [...c.candidates]; a[i] = { ...a[i], provider: v }; return { ...c, candidates: a }
            })} />
            <Txt value={cd.model} placeholder="model" onChange={v => setCfg(c => {
              const a = [...c.candidates]; a[i] = { ...a[i], model: v }; return { ...c, candidates: a }
            })} />
            <Num value={cd.priority} onChange={v => setCfg(c => {
              const a = [...c.candidates]; a[i] = { ...a[i], priority: v }; return { ...c, candidates: a }
            })} />
            <button onClick={() => setCfg(c => ({ ...c, candidates: c.candidates.filter((_, k) => k !== i) }))}
              className="text-muted hover:text-vital shrink-0">
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>
    </FormShell>
  )
}
