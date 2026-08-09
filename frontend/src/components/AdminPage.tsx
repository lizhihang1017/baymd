import { useState } from 'react'
import { Activity, BookOpen, Sparkles, Settings, MessageSquareText, Wrench, GitBranch, GitFork, Bug } from 'lucide-react'
import OpsView from './OpsView'
import KnowledgeView from './KnowledgeView'
import MemoryView from './MemoryView'
import SettingsView from './SettingsView'
import PromptView from './PromptView'
import ToolsView from './ToolsView'
import TraceView from './TraceView'
import IntentTreeView from './IntentTreeView'
import DebugView from './DebugView'

type Tab = 'ops' | 'kb' | 'memory' | 'intent' | 'prompt' | 'tools' | 'trace' | 'debug' | 'settings'

const TABS: { id: Tab; label: string; icon: any }[] = [
  { id: 'ops', label: '运营', icon: Activity },
  { id: 'kb', label: '知识库', icon: BookOpen },
  { id: 'memory', label: '用户记忆', icon: Sparkles },
  { id: 'intent', label: '意图树', icon: GitFork },
  { id: 'prompt', label: '提示词', icon: MessageSquareText },
  { id: 'tools', label: '工具', icon: Wrench },
  { id: 'trace', label: '链路追踪', icon: GitBranch },
  { id: 'debug', label: '调试', icon: Bug },
  { id: 'settings', label: '系统设置', icon: Settings },
]

/** 管理后台 — 面向管理员的运营/配置/知识库/记忆（独立页面 /admin） */
export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('ops')

  return (
    <div className="h-screen flex flex-col bg-surface">
      {/* 顶部 Tab 导航 */}
      <div className="h-12 border-b border-border flex items-center px-4 gap-1 shrink-0 bg-panel">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`flex items-center gap-1.5 px-3 h-full text-xs font-medium border-b-2 transition-colors ${
              tab === t.id
                ? 'border-accent text-accent'
                : 'border-transparent text-muted hover:text-text-primary'}`}>
            <t.icon className="w-3.5 h-3.5" />
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        {tab === 'ops' && <OpsView />}
        {tab === 'kb' && <KnowledgeView />}
        {tab === 'memory' && <MemoryView />}
        {tab === 'intent' && <IntentTreeView />}
        {tab === 'prompt' && <PromptView />}
        {tab === 'tools' && <ToolsView />}
        {tab === 'trace' && <TraceView />}
        {tab === 'debug' && <DebugView />}
        {tab === 'settings' && <SettingsView />}
      </div>
    </div>
  )
}
