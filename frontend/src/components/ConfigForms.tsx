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
interface CandidateRow { id: string; provider: string; model: string; priority: number; supportsThinking: boolean; dimension?: number }
interface ModelGroupRow { defaultModel: string; fastModel?: string; deepThinkingModel?: string; candidates: CandidateRow[] }
interface AiForm {
  providers: ProviderRow[]
  chat: ModelGroupRow
  embedding: ModelGroupRow
  rerank: ModelGroupRow
}

const AI_DEFAULT: AiForm = {
  providers: [{ name: 'bailian', url: '', apiKey: '' }],
  chat: {
    defaultModel: 'qwen3.7-plus',
    candidates: [{ id: 'qwen3.7-plus', provider: 'bailian', model: 'qwen3.7-plus', priority: 1, supportsThinking: true }],
  },
  embedding: {
    defaultModel: 'text-embedding-v4',
    candidates: [{ id: 'text-embedding-v4', provider: 'bailian', model: 'text-embedding-v4', priority: 1 }],
  },
  rerank: {
    defaultModel: 'qwen3-rerank',
    candidates: [{ id: 'qwen3-rerank', provider: 'bailian', model: 'qwen3-rerank', priority: 1 }],
  },
}

/** 模型组编辑区块 — 默认模型 + 候选列表（chat 可选快模型/思考开关,embedding 可选维度） */
function ModelGroupSection({ title, desc, group, onChange, showFast, showThinking, showDimension }: {
  title: string
  desc: string
  group: ModelGroupRow
  onChange: (g: ModelGroupRow) => void
  showFast?: boolean
  showThinking?: boolean
  showDimension?: boolean
}) {
  const setGroup = (patch: Partial<ModelGroupRow>) => onChange({ ...group, ...patch })
  const setCand = (i: number, patch: Partial<CandidateRow>) => {
    const a = [...group.candidates]; a[i] = { ...a[i], ...patch }; onChange({ ...group, candidates: a })
  }
  const addCand = () => onChange({ ...group, candidates: [...group.candidates,
    { id: '', provider: 'bailian', model: '', priority: 1, supportsThinking: false }] })
  const delCand = (i: number) => onChange({ ...group, candidates: group.candidates.filter((_, k) => k !== i) })

  return (
    <div className="pt-3 mt-1 border-t border-border/60 first:border-t-0 first:pt-1">
      <div className="flex items-baseline justify-between mb-1">
        <span className="text-xs font-semibold">{title}</span>
        <span className="text-[10px] text-muted">{desc}</span>
      </div>
      <Field label="默认模型">
        <Txt value={group.defaultModel} onChange={v => setGroup({ defaultModel: v })} placeholder="如 qwen3.7-plus" />
      </Field>
      {showFast && (
        <Field label="快速模型（改写/意图）">
          <Txt value={group.fastModel ?? ''} onChange={v => setGroup({ fastModel: v })} placeholder="如 qwen-plus-2025-07-28" />
        </Field>
      )}
      {showThinking && (
        <Field label="深度思考模型">
          <Txt value={group.deepThinkingModel ?? ''} onChange={v => setGroup({ deepThinkingModel: v })} placeholder="如 qwen3.7-plus" />
        </Field>
      )}

      <div className="flex items-center justify-between mt-1.5 mb-0.5">
        <span className="text-[11px] text-muted">候选模型</span>
        <button onClick={addCand} className="text-[11px] text-accent hover:underline flex items-center gap-0.5">
          <Plus className="w-3 h-3" /> 添加
        </button>
      </div>
      {group.candidates.map((cd, i) => (
        <div key={i} className="flex items-center gap-1.5 py-1">
          <Txt value={cd.id} placeholder="id" onChange={v => setCand(i, { id: v })} />
          <Txt value={cd.provider} placeholder="provider" onChange={v => setCand(i, { provider: v })} />
          <Txt value={cd.model} placeholder="model" onChange={v => setCand(i, { model: v })} />
          {showDimension && (
            <Txt value={cd.dimension ? String(cd.dimension) : ''} placeholder="dim" onChange={v => setCand(i, { dimension: v ? Number(v) : undefined })} />
          )}
          <Num value={cd.priority} onChange={v => setCand(i, { priority: v })} />
          {showThinking && (
            <button onClick={() => setCand(i, { supportsThinking: !cd.supportsThinking })}
              className={`shrink-0 text-[10px] px-1.5 py-0.5 rounded ${cd.supportsThinking ? 'bg-accent/10 text-accent' : 'bg-surface border border-border text-muted'}`}>
              思考
            </button>
          )}
          <button onClick={() => delCand(i)} className="text-muted hover:text-vital shrink-0">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      ))}
    </div>
  )
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
        const loadGroup = (g: any, def: ModelGroupRow): ModelGroupRow => ({
          defaultModel: g?.defaultModel ?? def.defaultModel,
          fastModel: g?.fastModel ?? def.fastModel,
          deepThinkingModel: g?.deepThinkingModel ?? def.deepThinkingModel,
          candidates: (g?.candidates?.length ? g.candidates : def.candidates).map((c: any) => ({
            id: c.id, provider: c.provider || '', model: c.model || '',
            priority: c.priority ?? 1, supportsThinking: !!c.supportsThinking, dimension: c.dimension,
          })),
        })
        // 兼容旧版扁平结构（chat 字段曾在顶层）: 若 p.chat 缺失,用顶层字段兜底
        const chatDef: ModelGroupRow = p.chat ? p.chat : {
          defaultModel: p.defaultModel, fastModel: p.fastModel,
          deepThinkingModel: p.deepThinkingModel, candidates: p.candidates,
        }
        setCfg({
          providers: providers.length ? providers : AI_DEFAULT.providers,
          chat: loadGroup(chatDef, AI_DEFAULT.chat),
          embedding: loadGroup(p.embedding, AI_DEFAULT.embedding),
          rerank: loadGroup(p.rerank, AI_DEFAULT.rerank),
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
      const buildGroup = (g: ModelGroupRow) => ({
        defaultModel: g.defaultModel || undefined,
        candidates: g.candidates.filter(c => c.id),
      })
      const payload = {
        providers,
        chat: {
          ...buildGroup(cfg.chat),
          fastModel: cfg.chat.fastModel || undefined,
        },
        embedding: buildGroup(cfg.embedding),
        rerank: buildGroup(cfg.rerank),
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

      {/* Chat 模型组 */}
      <ModelGroupSection title="对话模型（chat）" desc="问答主力 / 深度思考 / 快模型（改写·意图）"
        group={cfg.chat} showFast showThinking
        onChange={g => setCfg(c => ({ ...c, chat: g }))} />

      {/* Embedding 模型组 */}
      <ModelGroupSection title="嵌入模型（embedding）" desc="文档 / 记忆向量化,维度需与库表一致（1024）"
        group={cfg.embedding} showDimension
        onChange={g => setCfg(c => ({ ...c, embedding: g }))} />

      {/* Rerank 模型组 */}
      <ModelGroupSection title="重排模型（rerank）" desc="检索结果精排;候选含 rerank-noop 时自动降级（仅 RRF 融合）"
        group={cfg.rerank}
        onChange={g => setCfg(c => ({ ...c, rerank: g }))} />
    </FormShell>
  )
}
