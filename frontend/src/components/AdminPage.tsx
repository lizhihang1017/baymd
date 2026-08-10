import { useState } from 'react'
import {
  Activity, BookOpen, Settings, MessageSquareText, Wrench, GitBranch,
  GitFork, Bug, Database, HeartPulse, Workflow, Users as UsersIcon,
} from 'lucide-react'
import OpsView from './OpsView'
import KnowledgeView from './KnowledgeView'
import SettingsView from './SettingsView'
import PromptView from './PromptView'
import ToolsView from './ToolsView'
import TraceView from './TraceView'
import IntentTreeView from './IntentTreeView'
import DebugView from './DebugView'
import DataView from './DataView'
import HealthView from './HealthView'
import WorkflowView from './WorkflowView'
import UsersView from './UsersView'

type Tab = 'ops' | 'workflow' | 'kb' | 'users' | 'intent' | 'prompt' | 'tools' | 'trace' | 'debug' | 'data' | 'health' | 'settings'

interface TabDef { id: Tab; label: string; icon: any }
interface GroupDef { title: string; tabs: TabDef[] }

const GROUPS: GroupDef[] = [
  {
    title: '观测',
    tabs: [
      { id: 'ops', label: '运营看板', icon: Activity },
      { id: 'workflow', label: '工作流', icon: Workflow },
      { id: 'trace', label: '链路追踪', icon: GitBranch },
      { id: 'health', label: '系统健康', icon: HeartPulse },
    ],
  },
  {
    title: '内容',
    tabs: [
      { id: 'kb', label: '知识库', icon: BookOpen },
      { id: 'users', label: '用户管理', icon: UsersIcon },
      { id: 'intent', label: '意图树', icon: GitFork },
      { id: 'data', label: '数据管理', icon: Database },
    ],
  },
  {
    title: '配置',
    tabs: [
      { id: 'prompt', label: '提示词', icon: MessageSquareText },
      { id: 'tools', label: '工具', icon: Wrench },
      { id: 'debug', label: '调试', icon: Bug },
      { id: 'settings', label: '系统设置', icon: Settings },
    ],
  },
]

/** 管理后台 — 深色医疗监护侧边栏 + 心电线签名 + 淡入内容区 */
export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('ops')

  return (
    <div className="h-screen flex bg-surface">
      {/* ===== 左侧导航（深蓝监护台） ===== */}
      <aside className="w-52 shrink-0 bg-bg-deep flex flex-col relative overflow-hidden">
        {/* 顶部光晕 */}
        <div className="absolute -top-24 -left-16 w-64 h-64 rounded-full bg-accent/10 blur-3xl pointer-events-none" />

        {/* 品牌 + 心电线签名 */}
        <div className="relative h-16 flex items-center gap-2.5 px-4 border-b border-white/5 shrink-0">
          <div className="w-8 h-8 rounded-lg bg-accent/15 border border-accent/30 flex items-center justify-center shrink-0">
            <HeartPulse className="w-4.5 h-4.5 w-5 h-5 text-accent-bright" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-white leading-tight tracking-wide">BayMD</div>
            <div className="text-[10px] text-white/35 leading-tight">AI 私人医生 · 控制台</div>
          </div>
          {/* 心电线 */}
          <svg viewBox="0 0 80 20" className="absolute right-2 bottom-2 w-20 h-5 opacity-70" aria-hidden>
            <polyline className="ekg-line" fill="none" stroke="#22D3EE" strokeWidth="1.5"
              points="0,10 15,10 20,10 25,10 30,2 35,18 40,10 55,10 60,10 80,10" />
          </svg>
        </div>

        {/* 分组导航 */}
        <nav className="relative flex-1 overflow-y-auto py-3 px-2 space-y-4">
          {GROUPS.map(group => (
            <div key={group.title}>
              <div className="px-2 mb-1.5 text-[10px] font-medium uppercase tracking-wider text-white/25">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.tabs.map(t => {
                  const active = tab === t.id
                  return (
                    <button key={t.id} onClick={() => setTab(t.id)}
                      className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-xs transition-all duration-200 relative ${
                        active
                          ? 'bg-accent/15 text-white font-medium'
                          : 'text-white/45 hover:text-white/90 hover:bg-white/5'
                      }`}>
                      {active && (
                        <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full bg-accent-bright shadow-[0_0_6px_rgba(94,234,212,0.7)] nav-active" />
                      )}
                      <t.icon className={`w-4 h-4 transition-colors ${active ? 'text-accent-bright' : ''}`} />
                      {t.label}
                    </button>
                  )
                })}
              </div>
            </div>
          ))}
        </nav>

        {/* 底部 */}
        <div className="relative px-4 py-3 border-t border-white/5 shrink-0">
          <div className="flex items-center gap-1.5 text-[10px] text-white/30">
            <span className="w-1.5 h-1.5 rounded-full bg-accent dot-pulse" />
            系统在线 · 管理员会话
          </div>
        </div>
      </aside>

      {/* ===== 右侧内容（淡入） ===== */}
      <main className="flex-1 min-h-0 flex flex-col overflow-hidden view-enter" key={tab}>
        {tab === 'ops' && <OpsView />}
        {tab === 'workflow' && <WorkflowView />}
        {tab === 'kb' && <KnowledgeView />}
        {tab === 'users' && <UsersView />}
        {tab === 'intent' && <IntentTreeView />}
        {tab === 'prompt' && <PromptView />}
        {tab === 'tools' && <ToolsView />}
        {tab === 'trace' && <TraceView />}
        {tab === 'debug' && <DebugView />}
        {tab === 'data' && <DataView />}
        {tab === 'health' && <HealthView />}
        {tab === 'settings' && <SettingsView />}
      </main>
    </div>
  )
}
