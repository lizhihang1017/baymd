import { useState, useEffect } from 'react'
import { Loader2, Plus, Pencil, Trash2, X, ChevronDown, ChevronRight, GitFork } from 'lucide-react'
import { getIntentTree, createIntentNode, updateIntentNode, deleteIntentNode } from '../api/baymd'
import type { IntentTreeNode } from '../api/baymd'

const LEVEL_LABEL = ['', '领域 DOMAIN', '类别 CATEGORY', '主题 TOPIC']
const KIND_LABEL: Record<number, string> = { 0: 'KB', 1: 'SYSTEM', 2: 'MCP' }

interface Form {
  intentCode: string; name: string; level: number; parentCode: string;
  description: string; examples: string; kind: number; enabled: number;
  collectionName: string; topK: string; mcpToolId: string;
}

const EMPTY_FORM: Form = {
  intentCode: '', name: '', level: 2, parentCode: '', description: '',
  examples: '', kind: 0, enabled: 1, collectionName: '', topK: '', mcpToolId: '',
}

/** 意图树管理 — 树形展示三级意图节点,支持增删改 */
export default function IntentTreeView() {
  const [tree, setTree] = useState<IntentTreeNode[]>([])
  const [loading, setLoading] = useState(false)
  const [msg, setMsg] = useState('')
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})
  const [editor, setEditor] = useState<null | { mode: 'add' | 'edit'; id?: string; parentCode?: string; defaultLevel?: number }>(null)
  const [form, setForm] = useState<Form>(EMPTY_FORM)

  const load = async () => {
    setLoading(true)
    try { setTree(await getIntentTree()) } catch { /* 忽略 */ } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])

  const toggle = (id: string) => setCollapsed(c => ({ ...c, [id]: !c[id] }))

  const openAdd = (parent?: IntentTreeNode) => {
    setForm({ ...EMPTY_FORM, level: parent ? Math.min(parent.level + 1, 3) : 1, parentCode: parent?.intentCode || '' })
    setEditor({ mode: 'add', parentCode: parent?.intentCode, defaultLevel: parent ? Math.min(parent.level + 1, 3) : 1 })
  }
  const openEdit = (n: IntentTreeNode) => {
    setForm({
      intentCode: n.intentCode || '', name: n.name || '', level: n.level || 2, parentCode: n.parentCode || '',
      description: n.description || '', examples: n.examples || '', kind: n.kind ?? 0, enabled: n.enabled ?? 1,
      collectionName: n.collectionName || '', topK: n.topK ? String(n.topK) : '', mcpToolId: n.mcpToolId || '',
    })
    setEditor({ mode: 'edit', id: n.id })
  }

  const save = async () => {
    setMsg('')
    try {
      const payload: Record<string, any> = {
        intentCode: form.intentCode.trim(), name: form.name.trim(), level: form.level,
        parentCode: form.parentCode || undefined, description: form.description || undefined,
        examples: form.examples.split(/[,，]/).map(s => s.trim()).filter(Boolean),
        kind: form.kind, enabled: form.enabled,
        collectionName: form.collectionName || undefined, mcpToolId: form.mcpToolId || undefined,
        topK: form.topK ? Number(form.topK) : undefined,
      }
      if (editor?.mode === 'add') {
        const res = await createIntentNode(payload)
        if (!res.success) throw new Error(res.message)
      } else if (editor?.id) {
        const res = await updateIntentNode(editor.id, payload)
        if (!res.success) throw new Error(res.message)
      }
      setEditor(null)
      setMsg('✅ 已保存')
      await load()
    } catch (e: any) { setMsg(`保存失败: ${e?.message || ''}`) }
  }

  const del = async (n: IntentTreeNode) => {
    if (!window.confirm(`确定删除意图节点「${n.name}」？其子节点将一并删除。`)) return
    setMsg('')
    try {
      const res = await deleteIntentNode(n.id)
      if (!res.success) throw new Error(res.message)
      setMsg('✅ 已删除')
      await load()
    } catch (e: any) { setMsg(`删除失败: ${e?.message || ''}`) }
  }

  const renderNode = (n: IntentTreeNode, depth: number) => {
    const isOpen = !collapsed[n.id]
    const hasChildren = n.children && n.children.length > 0
    return (
      <div key={n.id}>
        <div className="flex items-center gap-1.5 py-1.5 px-2 rounded-lg hover:bg-accent/5 group"
          style={{ marginLeft: depth * 20 }}>
          {hasChildren ? (
            <button onClick={() => toggle(n.id)} className="text-muted hover:text-text-primary shrink-0">
              {isOpen ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
            </button>
          ) : <span className="w-3.5 shrink-0" />}
          <span className={`text-xs shrink-0 px-1.5 py-0.5 rounded ${
            n.kind === 1 ? 'bg-vital/10 text-vital' : n.kind === 2 ? 'bg-amber-500/10 text-amber-600' : 'bg-accent/10 text-accent'
          }`}>{KIND_LABEL[n.kind ?? 0]}</span>
          <span className="text-xs text-text-primary truncate">{n.name}</span>
          {n.enabled === 0 && <span className="text-[10px] text-muted shrink-0">(停用)</span>}
          <span className="text-[10px] text-muted/60 font-mono truncate hidden md:inline">{n.intentCode}</span>
          <div className="ml-auto flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
            <button onClick={() => openAdd(n)} title="添加子节点" className="text-muted hover:text-accent"><Plus className="w-3.5 h-3.5" /></button>
            <button onClick={() => openEdit(n)} title="编辑" className="text-muted hover:text-accent"><Pencil className="w-3.5 h-3.5" /></button>
            <button onClick={() => del(n)} title="删除" className="text-muted hover:text-vital"><Trash2 className="w-3.5 h-3.5" /></button>
          </div>
        </div>
        {hasChildren && isOpen && n.children!.map(c => renderNode(c, depth + 1))}
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-16 border-b border-border flex items-center px-6 shrink-0 bg-white/70 backdrop-blur-md border-b border-border/70">
        <div className="w-1 h-6 rounded-full bg-accent mr-3" />
        <h1 className="font-display text-lg font-semibold text-text-primary tracking-tight">意图树</h1>
      </header>
      <div className="flex-1 min-h-0 overflow-y-auto p-5">
        <div className="max-w-3xl">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2 text-xs text-muted">
              <GitFork className="w-4 h-4 text-accent" /> 共 {countNodes(tree)} 个节点
            </div>
            <div className="flex gap-2">
              <button onClick={() => openAdd(undefined)}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">
                <Plus className="w-3.5 h-3.5" /> 新增领域
              </button>
              <button onClick={load}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">刷新</button>
            </div>
          </div>

          {loading ? (
            <div className="flex items-center gap-2 text-xs text-muted"><Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载中...</div>
          ) : tree.length === 0 ? (
            <p className="text-xs text-muted">意图树为空,点击「新增领域」创建</p>
          ) : (
            <div className="bg-white border border-border/70 rounded-xl shadow-sm p-3">
              {tree.map(n => renderNode(n, 0))}
            </div>
          )}
          {msg && <p className="text-[11px] text-accent mt-3">{msg}</p>}
        </div>
      </div>

      {/* 编辑弹层 */}
      {editor && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setEditor(null)}>
          <div className="bg-white rounded-xl shadow-xl w-[500px] max-h-[85vh] overflow-y-auto p-5" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold">{editor.mode === 'add' ? '新增意图节点' : '编辑意图节点'}</h3>
              <button onClick={() => setEditor(null)} className="text-muted hover:text-text-primary"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-muted">编码（intentCode）</span>
                  <input value={form.intentCode} onChange={e => setForm(f => ({ ...f, intentCode: e.target.value }))}
                    placeholder="如 medical-drug-western" disabled={editor.mode === 'edit'}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
                <label className="block">
                  <span className="text-muted">名称</span>
                  <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                    placeholder="如 西药查询"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
                <label className="block">
                  <span className="text-muted">层级</span>
                  <select value={form.level} onChange={e => setForm(f => ({ ...f, level: Number(e.target.value) }))}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent">
                    {[1, 2, 3].map(l => <option key={l} value={l}>{LEVEL_LABEL[l]}</option>)}
                  </select>
                </label>
                <label className="block">
                  <span className="text-muted">类型</span>
                  <select value={form.kind} onChange={e => setForm(f => ({ ...f, kind: Number(e.target.value) }))}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent">
                    <option value={0}>KB 知识库检索</option>
                    <option value={1}>SYSTEM 系统直答</option>
                    <option value={2}>MCP 实时工具</option>
                  </select>
                </label>
                <label className="block">
                  <span className="text-muted">父节点编码（留空=顶级）</span>
                  <input value={form.parentCode} onChange={e => setForm(f => ({ ...f, parentCode: e.target.value }))}
                    placeholder="如 medical-drug"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
                <label className="block">
                  <span className="text-muted">状态</span>
                  <select value={form.enabled} onChange={e => setForm(f => ({ ...f, enabled: Number(e.target.value) }))}
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent">
                    <option value={1}>启用</option>
                    <option value={0}>停用</option>
                  </select>
                </label>
              </div>
              <label className="block">
                <span className="text-muted">描述</span>
                <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  placeholder="节点语义说明,用于意图分类"
                  className="mt-0.5 w-full h-16 px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent resize-y" />
              </label>
              <label className="block">
                <span className="text-muted">示例问题（逗号分隔）</span>
                <input value={form.examples} onChange={e => setForm(f => ({ ...f, examples: e.target.value }))}
                  placeholder="如: 感冒吃什么药,咳嗽怎么办"
                  className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
              </label>
              <div className="grid grid-cols-3 gap-3">
                <label className="block">
                  <span className="text-muted">知识库</span>
                  <input value={form.collectionName} onChange={e => setForm(f => ({ ...f, collectionName: e.target.value }))}
                    placeholder="collection"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
                <label className="block">
                  <span className="text-muted">TopK</span>
                  <input value={form.topK} onChange={e => setForm(f => ({ ...f, topK: e.target.value }))}
                    placeholder="默认"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
                <label className="block">
                  <span className="text-muted">MCP 工具（kind=MCP 时）</span>
                  <input value={form.mcpToolId} onChange={e => setForm(f => ({ ...f, mcpToolId: e.target.value }))}
                    placeholder="如 maps_weather"
                    className="mt-0.5 w-full px-2 py-1.5 rounded-lg border border-border bg-white focus:outline-none focus:border-accent" />
                </label>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setEditor(null)}
                className="px-3 py-1.5 rounded-lg border border-border text-xs text-muted hover:text-text-primary">取消</button>
              <button onClick={save} className="px-4 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90">保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function countNodes(nodes: IntentTreeNode[]): number {
  return nodes.reduce((sum, n) => sum + 1 + (n.children ? countNodes(n.children) : 0), 0)
}
