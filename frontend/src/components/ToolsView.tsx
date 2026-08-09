import { useState, useEffect } from 'react'
import { Loader2, Wrench, Save, Plus, Trash2, Plug, RefreshCw } from 'lucide-react'
import { getTools, saveTools, getMcpServers, addMcpServer, testMcpServer, deleteMcpServer } from '../api/baymd'
import type { ToolItem, McpServerStatus } from '../api/baymd'

/**
 * 工具管理 — 本地 Agent 工具 + MCP 远程工具的加载/启用/停用 + MCP Server 动态管理。
 * 工具开关即时生效（热生效，无需重启后端）。
 */
export default function ToolsView() {
  const [tools, setTools] = useState<ToolItem[]>([])
  const [servers, setServers] = useState<McpServerStatus[]>([])
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState('')
  // MCP Server 添加表单
  const [showAdd, setShowAdd] = useState(false)
  const [newName, setNewName] = useState('')
  const [newUrl, setNewUrl] = useState('')
  const [newApiKey, setNewApiKey] = useState('')
  const [busy, setBusy] = useState('')   // 正在操作的 server 名
  const [testResult, setTestResult] = useState<Record<string, string[]>>({})

  const load = async () => {
    setLoaded(false)
    try {
      const [t, s] = await Promise.all([getTools(), getMcpServers()])
      setTools(t)
      setServers(s)
      setLoaded(true)
    } catch (e: any) {
      setMsg(`加载失败: ${e?.message || ''}`)
      setLoaded(true)
    }
  }

  useEffect(() => { load() }, [])

  const toggle = (name: string) => {
    setTools(prev => prev.map(t => t.name === name ? { ...t, enabled: !t.enabled } : t))
  }

  const save = async () => {
    const enabled = tools.filter(t => t.enabled).map(t => t.name)
    setSaving(true)
    try {
      const res = await saveTools(enabled)
      if (!res.success) throw new Error(res.message)
      setMsg(`✅ 已保存 ${enabled.length}/${tools.length} 个工具为启用，即时生效`)
    } catch (e: any) {
      setMsg(`保存失败: ${e?.message || ''}`)
    } finally {
      setSaving(false)
    }
  }

  const enabledCount = tools.filter(t => t.enabled).length
  const localTools = tools.filter(t => t.source === 'local')
  const mcpTools = tools.filter(t => t.source === 'mcp')

  const handleAddMcp = async () => {
    if (!newName.trim() || !newUrl.trim()) { setMsg('请填写 Server 名称和 URL'); return }
    setBusy('__add__')
    setMsg('')
    try {
      const res = await addMcpServer(newName.trim(), newUrl.trim(), newApiKey.trim() || undefined)
      if (!res.success) throw new Error(res.message)
      setMsg(`✅ MCP Server [${newName}] 已添加并连接`)
      setNewName(''); setNewUrl(''); setNewApiKey(''); setShowAdd(false)
      await load()
    } catch (e: any) {
      setMsg(`添加失败: ${e?.message || ''}`)
    } finally { setBusy('') }
  }

  const handleTestMcp = async (s: McpServerStatus) => {
    setBusy(`test:${s.name}`)
    setMsg('')
    try {
      const res = await testMcpServer(s.name, s.url, s.apiKey || undefined)
      if (!res.success) throw new Error(res.message)
      setTestResult(prev => ({ ...prev, [s.name]: res.data || [] }))
      setMsg(`测试连接成功，发现 ${(res.data || []).length} 个工具`)
    } catch (e: any) {
      setMsg(`测试失败: ${e?.message || ''}`)
    } finally { setBusy('') }
  }

  const handleDeleteMcp = async (s: McpServerStatus) => {
    if (!window.confirm(`确定删除 MCP Server [${s.name}]？将断开连接并注销其工具。`)) return
    setBusy(`del:${s.name}`)
    setMsg('')
    try {
      const res = await deleteMcpServer(s.name)
      if (!res.success) throw new Error(res.message)
      setMsg(`已删除 [${s.name}]`)
      await load()
    } catch (e: any) {
      setMsg(`删除失败: ${e?.message || ''}`)
    } finally { setBusy('') }
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">工具管理</h1>
        <span className="ml-3 text-[11px] text-muted">本地 Agent 工具 + MCP 远程工具的启用/停用 · 开关即时生效，无需重启</span>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-5">
        <div className="max-w-3xl mx-auto">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2 text-xs text-muted">
              <Wrench className="w-4 h-4 text-accent" />
              共 {tools.length} 个工具，{enabledCount} 个启用
            </div>
            <button onClick={save} disabled={saving}
              className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90 disabled:opacity-50">
              <Save className="w-3.5 h-3.5" /> {saving ? '保存中...' : '保存开关'}
            </button>
          </div>

          {!loaded ? (
            <div className="flex items-center gap-2 text-xs text-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
          ) : (
            <div className="space-y-6">
              {/* MCP Server 管理 */}
              <section>
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <h2 className="text-xs font-semibold">MCP Server 管理</h2>
                    <p className="text-[11px] text-muted mt-0.5">添加 / 删除外部 MCP Server，连接后其工具自动注册到下方「MCP 远程工具」</p>
                  </div>
                  <button onClick={() => { setShowAdd(v => !v); setTestResult({}) }}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-accent text-accent text-xs hover:bg-accent/5 transition-colors">
                    <Plus className="w-3.5 h-3.5" /> 添加 Server
                  </button>
                </div>

                {/* 添加表单 */}
                {showAdd && (
                  <div className="bg-panel border border-border rounded-xl p-3 mb-3">
                    <div className="flex gap-2 items-end flex-wrap">
                      <label className="flex-1 min-w-[140px]">
                        <span className="text-[11px] text-muted">Server 名称</span>
                        <input value={newName} onChange={e => setNewName(e.target.value)}
                          placeholder="如 my-medical-server"
                          className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex-[2] min-w-[220px]">
                        <span className="text-[11px] text-muted">Server URL（HTTP，自动补 /mcp）</span>
                        <input value={newUrl} onChange={e => setNewUrl(e.target.value)}
                          placeholder="如 http://123.57.135.210:9000"
                          className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex-[2] min-w-[200px]">
                        <span className="text-[11px] text-muted">API Key（可选，走 Bearer 认证的服务填，如 Ahrefs）</span>
                        <input value={newApiKey} onChange={e => setNewApiKey(e.target.value)} type="password"
                          placeholder="自动加 Authorization: Bearer"
                          className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                        <span className="text-[10px] text-muted/80">query 认证的服务（如高德）：把 key 直接拼进 URL，如 https://mcp.amap.com/mcp?key=xxx，此项留空</span>
                      </label>
                      <button onClick={handleAddMcp} disabled={busy === '__add__'}
                        className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90 disabled:opacity-50">
                        {busy === '__add__' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plug className="w-3.5 h-3.5" />}
                        连接并添加
                      </button>
                    </div>
                  </div>
                )}

                {/* Server 列表 */}
                {servers.length === 0 ? (
                  <p className="text-[11px] text-muted bg-panel border border-border rounded-lg p-3">暂无 MCP Server 配置</p>
                ) : (
                  <div className="bg-panel border border-border rounded-xl divide-y divide-border">
                    {servers.map(s => (
                      <div key={s.name} className="flex items-center gap-3 px-4 py-3">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className={`w-2 h-2 rounded-full ${s.connected ? 'bg-accent' : 'bg-vital'}`} />
                            <span className="text-xs font-medium">{s.name}</span>
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface border border-border text-muted font-mono">{s.url}</span>
                            <span className="text-[10px] text-muted">{s.connected ? `已连接 · ${s.toolCount} 工具` : '未连接'}</span>
                          </div>
                          {s.error && <p className="text-[11px] text-vital mt-0.5 truncate">{s.error}</p>}
                          {testResult[s.name] && testResult[s.name].length > 0 && (
                            <p className="text-[11px] text-muted mt-0.5 truncate">测试发现工具: {testResult[s.name].join(', ')}</p>
                          )}
                        </div>
                        <button onClick={() => handleTestMcp(s)} disabled={busy === `test:${s.name}`}
                          className="flex items-center gap-1 px-2.5 py-1.5 rounded-md border border-border text-xs text-muted hover:text-accent transition-colors disabled:opacity-50">
                          <RefreshCw className={`w-3 h-3 ${busy === `test:${s.name}` ? 'animate-spin' : ''}`} /> 测试
                        </button>
                        <button onClick={() => handleDeleteMcp(s)} disabled={busy === `del:${s.name}`}
                          className="flex items-center gap-1 px-2.5 py-1.5 rounded-md border border-vital text-vital text-xs hover:bg-vital/5 transition-colors disabled:opacity-50">
                          <Trash2 className="w-3 h-3" /> 删除
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* 本地工具 */}
              <ToolGroup title="本地 Agent 工具" subtitle="随系统内置，ReAct 循环可直接调用（知识检索 / 计算器 / 药物相互作用等）"
                tools={localTools} toggle={toggle} />
              {/* MCP 远程工具 */}
              <ToolGroup title="MCP 远程工具" subtitle="通过 MCP Server 连接，供意图定向检索调用实时数据"
                tools={mcpTools} toggle={toggle} />
            </div>
          )}

          {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
        </div>
      </div>
    </div>
  )
}

function ToolGroup({ title, subtitle, tools, toggle }: {
  title: string; subtitle: string;
  tools: ToolItem[]; toggle: (name: string) => void;
}) {
  if (tools.length === 0) {
    return (
      <section>
        <h2 className="text-xs font-semibold mb-0.5">{title}</h2>
        <p className="text-[11px] text-muted mb-2">{subtitle}</p>
        <p className="text-[11px] text-muted bg-panel border border-border rounded-lg p-3">暂无工具</p>
      </section>
    )
  }
  return (
    <section>
      <h2 className="text-xs font-semibold mb-0.5">{title} <span className="text-muted font-normal">({tools.length})</span></h2>
      <p className="text-[11px] text-muted mb-2">{subtitle}</p>
      <div className="bg-panel border border-border rounded-xl divide-y divide-border">
        {tools.map(t => (
          <div key={t.name} className="flex items-center gap-3 px-4 py-3">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium">{t.label}</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent/10 text-accent font-mono">{t.name}</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface border border-border text-muted">{t.source}</span>
              </div>
              <p className="text-[11px] text-muted mt-0.5 truncate">{t.description}</p>
            </div>
            <button onClick={() => toggle(t.name)}
              className={`relative w-10 h-6 rounded-full transition-colors shrink-0 ${
                t.enabled ? 'bg-accent' : 'bg-muted/30'
              }`}>
              <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all ${
                t.enabled ? 'left-[18px]' : 'left-0.5'
              }`} />
            </button>
          </div>
        ))}
      </div>
    </section>
  )
}
