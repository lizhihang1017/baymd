import { useState, useEffect } from 'react'
import { Plus, MessageSquare, Trash2, Settings, User as UserIcon } from 'lucide-react'
import { getAuthToken } from '../api/baymd'
import UserAuth from './UserAuth'
import UserMenu from './UserMenu'
import { listConversations, deleteConversation, Conversation } from '../api/baymd'

interface Props {
  onSelect: (id: string) => void
  onNew: () => void
  activeId: string | null
  refreshKey: number
  onAuthChange: () => void
}

/** 用户端侧边栏 — ChatGPT 风格: 炭黑窄栏 + 会话列表 + 底部设置 */
export default function Sidebar({ onSelect, onNew, activeId, refreshKey, onAuthChange }: Props) {
  const [showAuth, setShowAuth] = useState(false)
  const [authVersion, setAuthVersion] = useState(0) // 登录/退出后刷新
  const [conversations, setConversations] = useState<Conversation[]>([])

  const loadConversations = () => {
    listConversations().then(setConversations).catch(() => {})
  }

  useEffect(() => { loadConversations() }, [activeId])
  useEffect(() => { loadConversations() }, [refreshKey]) // 新消息后立即刷新

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    await deleteConversation(id)
    setConversations(prev => prev.filter(c => c.conversationId !== id))
    if (activeId === id) onNew()
  }

  return (
    <aside className="w-64 bg-u-sidebar text-white flex flex-col shrink-0">
      {/* 新对话 */}
      <div className="p-3">
        <button
          onClick={onNew}
          className="w-full flex items-center gap-2.5 px-3.5 py-2.5 rounded-lg
            bg-white/10 hover:bg-white/15 text-sm font-medium
            transition-colors border border-white/10"
        >
          <Plus className="w-4 h-4" />
          新对话
        </button>
      </div>

      {/* 会话列表 */}
      <div className="flex-1 overflow-y-auto px-2 space-y-0.5">
        {conversations.length === 0 ? (
          <p className="p-4 text-xs text-white/40 text-center">暂无对话记录</p>
        ) : (
          conversations.map(conv => (
            <button
              key={conv.conversationId}
              onClick={() => onSelect(conv.conversationId)}
              className={`w-full text-left px-3 py-2.5 flex items-center gap-2.5
                text-sm rounded-lg transition-colors group
                ${activeId === conv.conversationId
                  ? 'bg-white/10 text-white'
                  : 'text-white/60 hover:bg-white/5 hover:text-white'}`}
            >
              <MessageSquare className="w-4 h-4 shrink-0 opacity-60" />
              <span className="truncate flex-1">{conv.title || '新对话'}</span>
              <Trash2
                className="w-3.5 h-3.5 text-white/30 opacity-0 group-hover:opacity-100
                  hover:text-vital transition-all shrink-0"
                onClick={e => handleDelete(e, conv.conversationId)}
              />
            </button>
          ))
        )}
      </div>

      {/* 底部: 登录/用户菜单 */}
      <div className="p-3 border-t border-white/10">
        {getAuthToken() ? (
          <UserMenu onLogout={() => { onAuthChange(); setAuthVersion(v => v + 1) }} />
        ) : (
          <button onClick={() => setShowAuth(true)}
            className="w-full flex items-center gap-2.5 px-3.5 py-2.5 text-sm text-white/60 hover:text-white hover:bg-white/5 rounded-lg transition-colors">
            <UserIcon className="w-4 h-4" />
            登录 / 注册
          </button>
        )}
      </div>

      {/* 登录/注册弹层 */}
      {showAuth && (
        <UserAuth onClose={() => setShowAuth(false)} onSuccess={() => { setShowAuth(false); setAuthVersion(v => v + 1) }} />
      )}
    </aside>
  )
}
