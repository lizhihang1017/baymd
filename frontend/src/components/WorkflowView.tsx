import { authHeaders } from '../api/baymd'
import { useState, useEffect } from 'react'
import { Loader2, RefreshCw, User, ShieldAlert, FileText, Brain, Zap, GitFork, Search, Bot, RefreshCcw, ArrowDown, ArrowLeft, CheckCircle2, Siren, MessageCircle, Sparkles, Cpu, Mail } from 'lucide-react'

interface NodeStat {
  nodeName?: string; nodeType?: string; calls: number;
  avgDurationMs?: number; avgdurationms?: number; failed: number;
  nodename?: string; nodetype?: string;
}

const fmtMs = (ms: number) => ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`

/** 工作流可视化 — 完整决策流程图: 从用户输入到最终输出,含所有分支 */
export default function WorkflowView() {
  const [stats, setStats] = useState<NodeStat[]>([])
  const [loading, setLoading] = useState(false)
  const [updatedAt, setUpdatedAt] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const res = await fetch('/api/baymd/admin/ops/workflow-stats', { headers: authHeaders() })
      const json = await res.json()
      setStats(json.data || [])
      setUpdatedAt(new Date().toLocaleTimeString())
    } catch { /* 忽略 */ } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])

  const statFor = (keys: string[]) => {
    const list = stats.filter(s => keys.includes(s.nodeName || s.nodename || ''))
    if (list.length === 0) return null
    const calls = list.reduce((a, s) => a + s.calls, 0)
    const avgMs = Math.round(list.reduce((a, s) => a + (s.avgDurationMs ?? s.avgdurationms ?? 0) * s.calls, 0) / Math.max(calls, 1))
    const failed = list.reduce((a, s) => a + s.failed, 0)
    return { calls, avgMs, failed }
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center justify-between px-6 shrink-0 bg-white/70 backdrop-blur-md">
        <div className="flex items-center">
          <div className="w-1 h-6 rounded-full bg-accent mr-3" />
          <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">RAG 工作流</h1>
        </div>
        <div className="flex items-center gap-2 text-[11px] text-muted">
          {updatedAt && <span>更新于 {updatedAt}</span>}
          <button onClick={load} className="flex items-center gap-1 hover:text-accent transition-colors">
            <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} /> 刷新
          </button>
        </div>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6">
        <div className="max-w-3xl mx-auto">
          <p className="text-[11px] text-muted mb-4 text-center">
            完整问答流程 · 主路径自上而下 · 分支为条件触发 · 颜色 = 模块
          </p>

          {/* ===== 01 用户输入 ===== */}
          <NodeMain num="01" title="用户输入" icon={User} color="#0891B2"
            desc="GET /rag/v3/chat (SSE) · 问题 + 可选报告上传" />

          <ArrowDownLine />

          {/* ===== 02 紧急分诊 ===== */}
          <NodeMain num="02" title="紧急分诊检测" icon={ShieldAlert} color="#DC2626"
            desc="关键词匹配 8 类红旗词(心血管/自杀/中毒…) · 零成本,最前置"
            stat={statFor(['emergency'])} />
          {/* 分支: 命中 */}
          <BranchRight label="命中红旗词" color="#DC2626">
            <NodeLeaf icon={Siren} color="#DC2626" title="紧急分支"
              desc="输出就医警告 + 心理援助热线 · 短路,结束本轮" />
            <EndMark />
          </BranchRight>
          {/* 分支: 未命中 */}
          <BranchLeft label="未命中 → 继续" color="#64748B">
            <div className="border-l-2 border-accent/40 pl-4 py-1" />
          </BranchLeft>

          {/* ===== 03 报告上下文 ===== */}
          <NodeMain num="03" title="报告上下文" icon={FileText} color="#D97706"
            desc="本会话上传过报告 → 报告内容拼入上下文(可选)" />

          <ArrowDownLine />

          {/* ===== 04 记忆加载 ===== */}
          <NodeMain num="04" title="记忆加载" icon={Brain} color="#059669"
            desc="短时历史(4轮) + 长时摘要 + 语义记忆(事实/情节/画像) → 注入 system" />

          <ArrowDownLine />

          {/* ===== 05 查询改写 ===== */}
          <NodeMain num="05" title="查询改写 + 拆分" icon={Zap} color="#7C3AED"
            desc="快模型 · 改写问题 + 拆分子问题"
            stat={statFor(['query-rewrite-and-split'])} />
          <BranchRight label="质量 &lt; 0.3" color="#D97706">
            <NodeLeaf icon={CheckCircle2} color="#D97706" title="回退"
              desc="子问题质量过低 → 使用归一化原问题" />
          </BranchRight>
          <BranchLeft label="通过 → 子问题列表" color="#64748B">
            <div className="border-l-2 border-accent/40 pl-4 py-1" />
          </BranchLeft>

          {/* ===== 06 意图分类 ===== */}
          <NodeMain num="06" title="意图分类" icon={GitFork} color="#059669"
            desc="快模型 · 树形意图打分 → top3 + 阈值 0.35"
            stat={statFor(['intent-resolve'])} />

          <ArrowDownLine />

          {/* ===== 07 执行器分发 ===== */}
          <NodeMain num="07" title="执行器分发" icon={Cpu} color="#D97706"
            desc="按优先级取第一个 supports()==true 的执行器" />
          <BranchRight label="意图歧义" color="#D97706">
            <NodeLeaf icon={MessageCircle} color="#D97706" title="澄清执行器"
              desc="候选≥2 且 LLM 确认歧义 → 输出引导选项 → 等用户回答 → 回到 01" />
            <ArrowBackLabel />
          </BranchRight>
          <BranchRight2 label="纯系统意图(问候/关于)" color="#059669">
            <NodeLeaf icon={Sparkles} color="#059669" title="系统直答"
              desc="直接闲聊回答,不走检索" />
            <EndMark />
          </BranchRight2>
          <BranchRight3 label="深度思考 / 含 MCP 意图" color="#7C3AED">
            <NodeLeaf icon={Bot} color="#7C3AED" title="Agent 执行器"
              desc="ReAct 循环 ≤5 轮 · LLM↔工具 ↔ 知识检索/计算器/MCP" />
            <EndMark />
          </BranchRight3>
          <BranchLeft label="兜底 → RAG" color="#0891B2">
            <div className="border-l-2 border-accent/40 pl-4 py-1" />
          </BranchLeft>

          {/* ===== 08 混合检索 ===== */}
          <NodeMain num="08" title="混合检索" icon={Search} color="#0891B2"
            desc="全局向量 + 意图定向 并行 · 去重 → Rerank → 证据截断"
            stat={statFor(['retrieval-engine', 'multi-channel-retrieval'])} />
          <BranchRight label="命中 MCP 意图" color="#7C3AED">
            <NodeLeaf icon={Cpu} color="#7C3AED" title="MCP 子分支"
              desc="参数提取(LLM) → 调用 MCP 工具取实时数据(受工具开关控制)" />
          </BranchRight>
          <BranchLeft label="KB 证据 → 拼入 user 消息" color="#64748B">
            <div className="border-l-2 border-accent/40 pl-4 py-1" />
          </BranchLeft>

          {/* ===== 09 LLM 生成 ===== */}
          <NodeMain num="09" title="LLM 流式生成" icon={Bot} color="#7C3AED"
            desc="主力模型 · system+记忆+历史+证据 → SSE 流式回答(附引用)"
            stat={statFor(['llm-stream-routing', 'llm-first-packet'])} />

          <ArrowDownLine />

          {/* ===== 10 异步增强 ===== */}
          <NodeMain num="10" title="异步增强(不阻塞)" icon={RefreshCcw} color="#059669"
            desc="回答完成后后台并行执行" />
          <div className="grid grid-cols-2 gap-2 pl-12 mt-2">
            <NodeMini icon={Brain} color="#059669" title="记忆抽取" desc="事实/情节 → 向量化 → 合并/画像" />
            <NodeMini icon={MessageCircle} color="#0891B2" title="追问生成" desc="≤3 个推荐追问" />
            <NodeMini icon={CheckCircle2} color="#D97706" title="质量评估" desc="LLM-as-Judge 4 维度打分" />
            <NodeMini icon={Mail} color="#DC2626" title="随访规划" desc="值得随访? → 建任务 → 到期邮件" />
          </div>
          <EndMark />
        </div>
      </div>
    </div>
  )
}

// ===================== 图元组件 =====================

function ArrowDownLine() {
  return <div className="flex justify-center py-1"><ArrowDown className="w-4 h-4 text-accent/60" /></div>
}

function ArrowBackLabel() {
  return (
    <div className="flex items-center gap-1 text-[10px] text-muted mt-1">
      <ArrowLeft className="w-3 h-3" /> 回到 01 重新走流程
    </div>
  )
}

function EndMark() {
  return (
    <div className="flex items-center gap-1.5 text-[10px] text-muted mt-2 pl-4">
      <span className="w-1.5 h-1.5 rounded-full bg-border" /> 结束本轮
    </div>
  )
}

function NodeMain({ num, title, icon: Icon, color, desc, stat }: {
  num: string; title: string; icon: any; color: string; desc: string;
  stat?: { calls: number; avgMs: number; failed: number } | null
}) {
  return (
    <div className="flex items-start gap-3 group">
      <div className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5"
        style={{ background: `${color}15`, border: `1px solid ${color}30` }}>
        <Icon className="w-4 h-4" style={{ color }} />
      </div>
      <div className="flex-1 bg-white border border-border/70 rounded-xl shadow-sm p-3 hover:shadow-md transition-shadow">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-[9px] font-mono font-bold px-1.5 py-0.5 rounded" style={{ background: `${color}15`, color }}>{num}</span>
            <span className="text-xs font-semibold text-text-primary">{title}</span>
          </div>
          {stat && (
            <div className="flex items-center gap-2 text-[10px]">
              <span className="text-accent font-medium">{stat.calls} 次</span>
              {stat.failed > 0 && <span className="text-vital">✕{stat.failed}</span>}
              <span className="text-muted font-mono">{fmtMs(stat.avgMs)}</span>
            </div>
          )}
        </div>
        <p className="text-[11px] text-muted mt-1 leading-snug">{desc}</p>
      </div>
    </div>
  )
}

function BranchRight({ label, color, children }: { label: string; color: string; children: React.ReactNode }) {
  return (
    <div className="ml-4 mt-1">
      <div className="flex items-center gap-1.5">
        <span className="w-px h-4 bg-border/60 ml-4" />
        <span className="text-[10px] font-medium px-2 py-0.5 rounded-full" style={{ color, background: `${color}12` }}>{label}</span>
      </div>
      <div className="ml-10 pl-3 border-l-2 border-dashed mt-1" style={{ borderColor: `${color}40` }}>
        {children}
      </div>
    </div>
  )
}

function BranchRight2({ label, color, children }: { label: string; color: string; children: React.ReactNode }) {
  return <BranchRight label={label} color={color}>{children}</BranchRight>
}
function BranchRight3({ label, color, children }: { label: string; color: string; children: React.ReactNode }) {
  return <BranchRight label={label} color={color}>{children}</BranchRight>
}

function BranchLeft({ label, color, children }: { label: string; color: string; children: React.ReactNode }) {
  return (
    <div className="ml-4 mt-1">
      <div className="flex items-center gap-1.5">
        <span className="text-[10px] font-medium px-2 py-0.5 rounded-full" style={{ color, background: `${color}10` }}>{label}</span>
        <span className="w-px h-4 bg-border/60" />
      </div>
      <div className="ml-3 pl-3 mt-1">
        {children}
      </div>
    </div>
  )
}

function NodeLeaf({ icon: Icon, color, title, desc }: { icon: any; color: string; title: string; desc: string }) {
  return (
    <div className="bg-white border border-border/70 rounded-lg p-2.5 flex items-start gap-2">
      <Icon className="w-3.5 h-3.5 mt-0.5 shrink-0" style={{ color }} />
      <div>
        <div className="text-[11px] font-medium text-text-primary">{title}</div>
        <div className="text-[10px] text-muted mt-0.5 leading-snug">{desc}</div>
      </div>
    </div>
  )
}

function NodeMini({ icon: Icon, color, title, desc }: { icon: any; color: string; title: string; desc: string }) {
  return (
    <div className="bg-white border border-border/70 rounded-lg p-2 flex items-start gap-2">
      <Icon className="w-3.5 h-3.5 mt-0.5 shrink-0" style={{ color }} />
      <div className="min-w-0">
        <div className="text-[11px] font-medium text-text-primary">{title}</div>
        <div className="text-[10px] text-muted mt-0.5 leading-snug">{desc}</div>
      </div>
    </div>
  )
}
