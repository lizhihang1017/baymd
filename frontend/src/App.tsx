import { useState, useCallback, useEffect } from 'react'
import { MessageSquare, LayoutDashboard, Brain } from 'lucide-react'
import Sidebar from './components/Sidebar'
import ChatView from './components/ChatView'
import AdminPage from './components/AdminPage'
import { getCurrentUser } from './api/baymd'

/**
 * 路由：/ 为用户端（对话），/admin 为管理后台（独立页面）。
 * 管理后台仅 admin 角色可访问，非 admin 访问 /admin 自动跳回 /。
 */
function App() {
  const [isAdminRoute] = useState(() => window.location.pathname.startsWith('/admin'))
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null) // null = 加载中
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [deepThinking, setDeepThinking] = useState(false)

  useEffect(() => {
    getCurrentUser().then(u => setIsAdmin(u?.role === 'admin')).catch(() => setIsAdmin(false))
  }, [])

  const selectConversation = useCallback((id: string) => {
    setConversationId(id)
  }, [])

  const newChat = useCallback(() => {
    setConversationId(null)
  }, [])

  // ===== 管理后台（/admin）=====
  if (isAdminRoute) {
    if (isAdmin === null) {
      return <div className="h-screen bg-surface" />
    }
    if (!isAdmin) {
      window.location.href = '/'
      return null
    }
    return <AdminPage />
  }

  // ===== 用户端（/）=====
  return (
    <div className="flex h-screen bg-surface">
      <nav className="w-14 bg-panel border-r border-border flex flex-col items-center py-4 gap-2 shrink-0">
        <div className="w-8 h-8 rounded-lg bg-accent flex items-center justify-center mb-4">
          <Brain className="w-5 h-5 text-white" />
        </div>
        <button onClick={() => { window.location.href = '/' }}
          className="w-9 h-9 rounded-lg flex items-center justify-center bg-accent-light text-accent"
          title="对话">
          <MessageSquare className="w-5 h-5" />
        </button>
        {/* 管理后台入口（/admin 页面内部会做 admin 角色守卫） */}
        <button onClick={() => { window.location.href = '/admin' }}
          className="w-9 h-9 rounded-lg flex items-center justify-center text-muted hover:text-text-primary hover:bg-panel transition-colors"
          title="管理后台">
          <LayoutDashboard className="w-5 h-5" />
        </button>
      </nav>

      <div className="flex flex-1 overflow-hidden">
        <Sidebar onSelect={selectConversation} onNew={newChat} activeId={conversationId} />
        <ChatView
          conversationId={conversationId}
          deepThinking={deepThinking}
          onToggleDeepThinking={() => setDeepThinking(d => !d)}
        />
      </div>
    </div>
  )
}

export default App
