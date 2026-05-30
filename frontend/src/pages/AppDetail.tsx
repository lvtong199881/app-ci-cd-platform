import React, { useState, useEffect } from 'react'
import { useParams, useLocation, useNavigate } from 'react-router-dom'
import { appApi, AppInfo } from '../api'

interface Props {
  children?: React.ReactNode
}

export default function AppDetail({ children }: Props) {
  const { id } = useParams<{ id: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const [app, setApp] = useState<AppInfo | null>(null)

  useEffect(() => {
    if (id) loadApp()
  }, [id])

  async function loadApp() {
    const appData = await appApi.get(Number(id))
    setApp(appData)
  }

  if (!app) return <div>加载中...</div>

  // 从 URL 解析当前 tab
  const path = location.pathname
  const isConfig = path.includes('/config')
  const isBuilds = path.includes('/builds')
  const isWorkflows = path.includes('/workflows')

  function NavButton({ label, to, active }: { label: string; to: string; active: boolean }) {
    return (
      <button
        onClick={() => navigate(to)}
        style={{
          display: 'block',
          width: '100%',
          padding: '12px 24px',
          textAlign: 'left',
          background: active ? '#1890ff' : 'transparent',
          border: 'none',
          cursor: 'pointer',
          color: '#fff'
        }}
      >
        {label}
      </button>
    )
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* 左侧导航 */}
      <aside style={{ width: 200, background: '#0d1b2a', padding: '0 0' }}>
        <nav>
          <NavButton label="App 配置" to={`/app/${id}/config`} active={isConfig} />
          <NavButton label="构建记录" to={`/app/${id}/builds`} active={isBuilds} />
          <NavButton label="Workflow 管理" to={`/app/${id}/workflows`} active={isWorkflows} />
        </nav>
      </aside>

      {/* 主内容区 */}
      <main style={{ flex: 1, padding: 24 }}>
        {children && React.cloneElement(children as React.ReactElement, { appId: Number(id) })}
      </main>
    </div>
  )
}