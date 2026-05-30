import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { appApi, AppInfo } from '../api'

export default function AppConfig() {
  const { id } = useParams<{ id: string }>()
  const [app, setApp] = useState<AppInfo | null>(null)

  useEffect(() => {
    if (id) loadApp()
  }, [id])

  async function loadApp() {
    const appData = await appApi.get(Number(id))
    setApp(appData)
  }

  if (!app) return <div>加载中...</div>

  return (
    <div className="card">
      <h3>基本信息</h3>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div className="form-item">
          <label>应用名称</label>
          <div>{app.appName}</div>
        </div>
        <div className="form-item">
          <label>App Key</label>
          <span className="badge">{app.appKey}</span>
        </div>
        <div className="form-item">
          <label>仓库</label>
          <a href={app.repoUrl} target="_blank" rel="noopener noreferrer" style={{ color: '#1890ff' }}>{app.repoUrl}</a>
        </div>
        <div className="form-item">
          <label>主分支</label>
          <div>{app.branch}</div>
        </div>
        <div className="form-item">
          <label>Workflow ID</label>
          <div>{app.workflowId || '-'}</div>
        </div>
      </div>
    </div>
  )
}