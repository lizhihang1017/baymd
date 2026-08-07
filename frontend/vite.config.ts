import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,          // 绑定 0.0.0.0，允许外部访问（生产建议用 nginx 代理构建产物）
    port: 5173,
    proxy: {
      '/api': 'http://localhost:9090'
    }
  }
})
