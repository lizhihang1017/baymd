import { SlidersHorizontal } from 'lucide-react'
import { AiConfigForm, RagConfigForm } from './ConfigForms'

export default function SettingsView() {
  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="w-1 h-6 rounded-full bg-accent mr-3" />
        <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">系统设置</h1>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
        {/* Runtime Config */}
        <section className="bg-white border border-border/70 rounded-xl shadow-sm p-4 card-lift">
          <div className="flex items-center gap-2 mb-1">
            <SlidersHorizontal className="w-4 h-4 text-accent" />
            <h2 className="text-sm font-semibold">系统配置（DB 覆盖 · 重启生效）</h2>
          </div>
          <p className="text-[11px] text-muted mb-2">表单化编辑模型供应商 / RAG 超参数，保存到数据库，重启后端后生效。留空字段保持 yaml 默认值。</p>
          <AiConfigForm />
          <RagConfigForm />
        </section>
      </div>
    </div>
  )
}
