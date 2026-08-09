import { useState, useEffect, useRef } from 'react'
import { Upload, Database, Search, ChevronLeft, Trash2, Loader2, FileText, SlidersHorizontal, ChevronDown, Eye, X } from 'lucide-react'
import { listKnowledgeBases, listDocuments, listChunkStrategies, uploadDocument, chunkDocument, deleteDocument, previewChunk, listChunks } from '../api/baymd'
import type { DocChunk } from '../api/baymd'

interface KB { id: string; name?: string; description?: string; documentCount?: number }
interface Doc { id: string; docName?: string; status?: string; chunkCount?: number }

export default function KnowledgeView() {
  const [kbs, setKbs] = useState<KB[]>([])
  const [selectedKb, setSelectedKb] = useState<KB | null>(null)
  const [docs, setDocs] = useState<Doc[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [msg, setMsg] = useState('')
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  // 分块设置（Dify 式）
  const [strategies, setStrategies] = useState<any[]>([])
  const [chunkStrategy, setChunkStrategy] = useState('fixed_size')
  const [chunkCfg, setChunkCfg] = useState<Record<string, any>>({ chunkSize: 512, overlapSize: 128, separator: '' })
  const [showChunk, setShowChunk] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [previewData, setPreviewData] = useState<{ totalChunks: number; previews: { index: number; content: string }[] } | null>(null)
  const [viewingDoc, setViewingDoc] = useState<string | null>(null) // 正在查看分块的文档名
  const [chunkList, setChunkList] = useState<DocChunk[]>([])
  const [chunkTotal, setChunkTotal] = useState(0)

  useEffect(() => {
    listKnowledgeBases().then(res => {
      const data = res.data
      const list = Array.isArray(data) ? data : (data?.records || [])
      setKbs(list)
    }).catch(() => {})
    listChunkStrategies().then(list => {
      setStrategies(list || [])
      // 用默认策略的默认配置填充
      const def = (list || []).find((s: any) => s.value === 'fixed_size')
      if (def?.defaultConfig) setChunkCfg(def.defaultConfig)
    }).catch(() => {})
  }, [])

  const loadDocs = async (kb: KB) => {
    setSelectedKb(kb)
    setDocsLoading(true)
    setMsg('')
    try {
      const res = await listDocuments(kb.id)
      const data = res.data
      setDocs(Array.isArray(data) ? data : (data?.records || []))
    } catch { setMsg('加载文档失败') } finally { setDocsLoading(false) }
  }

  const handleDelete = async (doc: Doc) => {
    try {
      await deleteDocument(doc.id)
      setDocs(prev => prev.filter(d => d.id !== doc.id))
      setMsg(`已删除: ${doc.docName}`)
    } catch (e: any) {
      setMsg(`删除失败: ${e?.message || ''}`)
    }
  }

  const handleFileSelected = async (file: File) => {
    setSelectedFile(file)
    setPreviewData(null)
    setPreviewing(true)
    setMsg('')
    try {
      const data = await previewChunk(file, chunkStrategy, chunkCfg)
      setPreviewData(data)
    } catch (e: any) {
      setMsg(`预览失败: ${e?.message || ''}`)
    } finally { setPreviewing(false) }
  }

  const handleConfirmUpload = async () => {
    if (!selectedKb || !selectedFile) return
    setUploading(true)
    setMsg('')
    try {
      const doc = await uploadDocument(selectedKb.id, selectedFile, chunkStrategy, chunkCfg)
      await chunkDocument(doc.id)
      setMsg(`已上传并触发分块: ${selectedFile.name}`)
      setSelectedFile(null)
      setPreviewData(null)
      loadDocs(selectedKb)
    } catch (e: any) {
      setMsg(`上传失败: ${e?.message || ''}`)
    } finally { setUploading(false) }
  }

  const handleViewContent = async (doc: Doc) => {
    setViewingDoc(doc.docName || doc.id)
    setChunkList([])
    setChunkTotal(0)
    try {
      const data = await listChunks(doc.id)
      setChunkList(data?.records || [])
      setChunkTotal(data?.total || 0)
    } catch (e: any) {
      setChunkList([{ content: `读取分块失败: ${e?.message || ''}` }])
    }
  }

  return (
    <div className="flex-1 flex flex-col bg-surface min-h-0">
      <header className="h-12 border-b border-border flex items-center px-4 shrink-0">
        <h1 className="text-sm font-semibold text-text-primary">知识库管理</h1>
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto p-6">
        {selectedKb ? (
          /* ====== 文档列表 ====== */
          <div>
            <button onClick={() => setSelectedKb(null)}
              className="flex items-center gap-1 text-xs text-muted hover:text-text-primary mb-3">
              <ChevronLeft className="w-3.5 h-3.5" /> 返回知识库
            </button>
            <div className="flex items-center justify-between mb-3">
              <div>
                <h2 className="text-sm font-semibold">{selectedKb.name || selectedKb.id}</h2>
                <p className="text-xs text-muted">{docs.length} 个文档</p>
              </div>
              <button onClick={() => fileInputRef.current?.click()}
                disabled={uploading || previewing}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90 transition-opacity disabled:opacity-40">
                <Upload className="w-3.5 h-3.5" />
                {uploading ? '上传中...' : previewing ? '预览中...' : '上传文档'}
              </button>
              <input ref={fileInputRef} type="file" accept=".md,.pdf,.doc,.docx,.txt,.html"
                className="hidden" onChange={e => {
                  const f = e.target.files?.[0]
                  if (f) handleFileSelected(f)
                  e.target.value = ''
                }} />
            </div>

            {/* 预览 + 确认上传 */}
            {selectedFile && (
              <div className="bg-white border border-border rounded-lg mb-3 overflow-hidden">
                <div className="flex items-center justify-between px-3 py-2 bg-accent/5 border-b border-border">
                  <span className="text-xs font-medium text-accent truncate">📄 {selectedFile.name}</span>
                  {previewData && (
                    <span className="text-[11px] text-muted shrink-0">共 {previewData.totalChunks} 块</span>
                  )}
                </div>
                {previewData && (
                  <div className="px-3 py-2 space-y-1.5 max-h-48 overflow-y-auto">
                    {previewData.previews.map(p => (
                      <div key={p.index} className="text-[11px] text-text-primary bg-panel rounded-md px-2 py-1">
                        <span className="text-muted mr-1">#{p.index}</span>
                        <span className="whitespace-pre-wrap line-clamp-3">{p.content}</span>
                      </div>
                    ))}
                    {previewData.totalChunks > previewData.previews.length && (
                      <p className="text-[11px] text-muted">... 还有 {previewData.totalChunks - previewData.previews.length} 块未显示</p>
                    )}
                  </div>
                )}
                <div className="flex justify-end gap-2 px-3 py-2 border-t border-border">
                  <button onClick={() => { setSelectedFile(null); setPreviewData(null) }}
                    className="px-3 py-1 rounded-md border border-border text-xs text-muted hover:text-text-primary transition-colors">
                    取消
                  </button>
                  <button onClick={handleConfirmUpload} disabled={uploading}
                    className="px-3 py-1 rounded-md bg-accent text-white text-xs hover:opacity-90 transition-opacity disabled:opacity-40">
                    {uploading ? '上传中...' : '确认上传'}
                  </button>
                </div>
              </div>
            )}

            {/* 分块设置（Dify 式） */}
            <div className="bg-white border border-border rounded-lg mb-3 overflow-hidden">
              <button onClick={() => setShowChunk(!showChunk)}
                className="w-full flex items-center justify-between px-3 py-2 text-xs text-text-primary hover:bg-surface/60 transition-colors">
                <span className="flex items-center gap-1.5">
                  <SlidersHorizontal className="w-3.5 h-3.5 text-accent" />
                  分块设置
                </span>
                <ChevronDown className={`w-3.5 h-3.5 text-muted transition-transform ${showChunk ? 'rotate-180' : ''}`} />
              </button>
              {showChunk && (
                <div className="px-3 pb-3 space-y-2">
                  <label className="flex items-center justify-between gap-3 text-xs">
                    <span className="text-muted shrink-0">切开方式</span>
                    <select value={chunkStrategy} onChange={e => {
                      const s = e.target.value
                      setChunkStrategy(s)
                      const def = strategies.find(x => x.value === s)
                      if (def?.defaultConfig) setChunkCfg(def.defaultConfig)
                    }}
                      className="w-44 px-2 py-1 rounded-md border border-border bg-white text-xs focus:outline-none focus:border-accent">
                      {(strategies || []).map((s: any) => (
                        <option key={s.value} value={s.value}>{s.label}</option>
                      ))}
                    </select>
                  </label>

                  {chunkStrategy === 'fixed_size' ? (
                    <>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">最大块长（字符）</span>
                        <input type="number" value={chunkCfg.chunkSize ?? 512}
                          onChange={e => setChunkCfg(c => ({ ...c, chunkSize: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">重叠长度（字符）</span>
                        <input type="number" value={chunkCfg.overlapSize ?? 128}
                          onChange={e => setChunkCfg(c => ({ ...c, overlapSize: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">自定义分隔符</span>
                        <input type="text" value={chunkCfg.separator ?? ''}
                          onChange={e => setChunkCfg(c => ({ ...c, separator: e.target.value }))}
                          placeholder="如 \n\n 或 ###（留空用自动分块）"
                          className="flex-1 min-w-0 px-2 py-1 rounded-md border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                      </label>
                    </>
                  ) : chunkStrategy === 'structure_aware' ? (
                    <>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">目标长度（字符）</span>
                        <input type="number" value={chunkCfg.targetChars ?? 1400}
                          onChange={e => setChunkCfg(c => ({ ...c, targetChars: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">重叠长度（字符）</span>
                        <input type="number" value={chunkCfg.overlapChars ?? 0}
                          onChange={e => setChunkCfg(c => ({ ...c, overlapChars: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                    </>
                  ) : chunkStrategy === 'parent_child' ? (
                    <>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">父块最大长度（字符）</span>
                        <input type="number" value={chunkCfg.parentMaxChars ?? 1400}
                          onChange={e => setChunkCfg(c => ({ ...c, parentMaxChars: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">子块最大长度（字符）</span>
                        <input type="number" value={chunkCfg.childMaxChars ?? 500}
                          onChange={e => setChunkCfg(c => ({ ...c, childMaxChars: Number(e.target.value) }))}
                          className="w-24 px-2 py-1 rounded-md border border-border bg-white text-xs text-right focus:outline-none focus:border-accent" />
                      </label>
                      <label className="flex items-center justify-between gap-3 text-xs">
                        <span className="text-muted shrink-0">自定义分隔符</span>
                        <input type="text" value={chunkCfg.separator ?? ''}
                          onChange={e => setChunkCfg(c => ({ ...c, separator: e.target.value }))}
                          placeholder="如 \n\n（留空用自动）"
                          className="flex-1 min-w-0 px-2 py-1 rounded-md border border-border bg-white text-xs focus:outline-none focus:border-accent" />
                      </label>
                    </>
                  ) : (
                    <label className="flex flex-col gap-1.5 py-1 text-xs">
                      <span className="text-muted">QA 提取提示词（可选，留空用默认）</span>
                      <textarea value={chunkCfg.prompt ?? ''}
                        onChange={e => setChunkCfg(c => ({ ...c, prompt: e.target.value }))}
                        placeholder="自定义 QA 提取提示词，{doc} 代表文档内容。如：从以下医学文档提取常见问答对，输出 JSON 数组 [{question, answer}]"
                        className="w-full h-20 px-2 py-1 rounded-md border border-border bg-white text-[11px] font-mono
                          focus:outline-none focus:border-accent resize-y" />
                    </label>
                  )}
                </div>
              )}
            </div>

            {docsLoading ? (
              <div className="flex items-center gap-2 text-xs text-muted">
                <Loader2 className="w-3.5 h-3.5 animate-spin" /> 加载文档...
              </div>
            ) : docs.length === 0 ? (
              <p className="text-xs text-muted">该知识库暂无文档</p>
            ) : (
              <div className="bg-panel border border-border rounded-xl divide-y divide-border">
                {docs.map(d => (
                  <div key={d.id} className="flex items-center justify-between px-4 py-2.5">
                    <div className="flex items-center gap-2 min-w-0">
                      <FileText className="w-4 h-4 text-accent shrink-0" />
                      <span className="text-xs truncate">{d.docName}</span>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <span className={`text-[11px] px-1.5 py-0.5 rounded ${
                        d.status === 'success' ? 'bg-accent/10 text-accent'
                        : d.status === 'failed' ? 'bg-vital/10 text-vital' : 'text-muted'}`}>
                        {d.status || 'pending'} {typeof d.chunkCount === 'number' ? `· ${d.chunkCount}块` : ''}
                      </span>
                      <button onClick={() => handleViewContent(d)} title="查看内容"
                        className="text-muted hover:text-accent transition-colors">
                        <Eye className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => handleDelete(d)} title="删除"
                        className="text-muted hover:text-vital transition-colors">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {viewingDoc && (
              <div className="bg-white border border-border rounded-lg mt-3 overflow-hidden">
                <div className="flex items-center justify-between px-3 py-2 bg-accent/5 border-b border-border">
                  <span className="text-xs font-medium text-accent truncate">{viewingDoc}</span>
                  <button onClick={() => setViewingDoc(null)}
                    className="text-muted hover:text-text-primary transition-colors">
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
                <div className="px-3 py-2 space-y-2 max-h-96 overflow-y-auto">
                  <p className="text-[11px] text-muted">共 {chunkTotal} 块</p>
                  {chunkList.length === 0 ? (
                    <p className="text-[11px] text-muted">加载中...</p>
                  ) : (
                    chunkList.map((c, i) => (
                      <div key={i} className="bg-panel rounded-md px-2 py-1.5">
                        <span className="text-[10px] text-accent font-medium">#{c.chunkIndex ?? c.index ?? i}</span>
                        <p className="text-[11px] text-text-primary whitespace-pre-wrap mt-0.5">{c.content}</p>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
            {msg && <p className="text-xs text-muted mt-3">{msg}</p>}
          </div>
        ) : (
          /* ====== 知识库列表 ====== */
          <div>
            {kbs.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-muted">
                <Database className="w-12 h-12 mb-3" />
                <p className="text-sm">暂无知识库</p>
                <p className="text-xs mt-1">通过系统配置或 API 创建</p>
              </div>
            ) : (
              <div className="grid gap-4">
                {kbs.map(kb => (
                  <div key={kb.id} className="bg-panel border border-border rounded-xl p-4">
                    <div className="flex items-center gap-3 mb-3">
                      <Database className="w-5 h-5 text-accent" />
                      <div className="min-w-0">
                        <h3 className="text-sm font-semibold">{kb.name || kb.id}</h3>
                        <p className="text-xs text-muted truncate">{kb.description || ''}</p>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button onClick={() => loadDocs(kb)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-accent text-white text-xs hover:opacity-90 transition-opacity">
                        <Search className="w-3.5 h-3.5" /> 查看文档
                      </button>
                      <button onClick={() => { setSelectedKb(kb); loadDocs(kb) }}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-text-primary text-xs hover:bg-surface transition-colors">
                        <Upload className="w-3.5 h-3.5" /> 上传文档
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
