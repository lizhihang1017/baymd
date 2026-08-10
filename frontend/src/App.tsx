import { useState, useCallback, useEffect } from 'react'

import Sidebar from './components/Sidebar'
import ChatView from './components/ChatView'
import AdminPage from './components/AdminPage'
import { getCurrentUser, getAuthToken, clearAuthToken } from './api/baymd'
import LoginPage from './components/LoginPage'
import UserLoginGate from './components/UserLoginGate'


/**
 * 路由：/ 为用户端（对话），/admin 为管理后台（独立页面）。
 * 管理后台仅 admin 角色可访问，非 admin 访问 /admin 自动跳回 /。
 */
function App() {
  const [isAdminRoute] = useState(() => window.location.pathname.startsWith('/admin'))

  // 按路由切换标签图标: 用户端=大白, 管理员=心电线
  useEffect(() => {
    const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (link) link.href = isAdminRoute ? '/favicon.svg' : '/favicon-user.svg'
  }, [isAdminRoute])
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null) // null = 加载中
  const [authTick, setAuthTick] = useState(0) // 登录/退出触发重渲染
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [deepThinking, setDeepThinking] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0) // 新消息后刷新会话列表

  useEffect(() => {
    getCurrentUser().then(u => setIsAdmin(u?.role === 'admin')).catch(() => setIsAdmin(false))
  }, [])

  const selectConversation = useCallback((id: string) => {
    setConversationId(id)
  }, [])

  const newChat = useCallback(() => {
    setConversationId(null)
  }, [])

  // 退出登录触发: 强制重新评估登录态
  void authTick

  // ===== 全局登录门: 未登录访问任何路径 → 登录页 =====
  if (!getAuthToken()) {
    if (isAdminRoute) {
      return <LoginPage onSuccess={() => window.location.reload()} />
    }
    return <UserLoginGate onSuccess={() => window.location.reload()} />
  }

  // ===== 管理后台（/admin）=====
  if (isAdminRoute) {
    return <AdminPage />
  }

  // ===== 用户端（/）ChatGPT 风格 =====
  return (
    <div className="flex h-screen bg-u-bg">
      <Sidebar onSelect={selectConversation} onNew={newChat} activeId={conversationId} refreshKey={refreshKey} onAuthChange={() => setAuthTick(t => t + 1)} />
      <ChatView
        conversationId={conversationId}
        deepThinking={deepThinking}
        onToggleDeepThinking={() => setDeepThinking(d => !d)}
        onMessageSent={() => setRefreshKey(k => k + 1)}
        onConversationCreated={setConversationId}
      />
    </div>
  )
}

export default App
