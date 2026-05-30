import { useState, useEffect } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { appApi, workflowApi, AppInfo, BuildFlow } from '../api'
import BuildRecords from './BuildRecords'
import FlowConfig from './FlowConfig'

type Tab = 'config' | 'builds' | 'workflows'

export default function AppDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const [app, setApp] = useState<AppInfo | null>(null)
  const [tab, setTab] = useState<Tab>(() => {
    if (location.pathname.includes('/flows')) return 'workflows'
    return 'config'
  })
  const [showWorkflowModal, setShowWorkflowModal] = useState(false)
  const [workflowPreview, setWorkflowPreview] = useState('')
  const [workflows, setWorkflows] = useState<BuildFlow[]>([])
  const [creatingWorkflow, setCreatingWorkflow] = useState(false)

  useEffect(() => {
    if (id) loadApp()
  }, [id])

  async function loadApp() {
    const appData = await appApi.get(Number(id))
    setApp(appData)
    const flowData = await workflowApi.list(Number(id))
    setWorkflows(flowData)
  }

  async function handlePreviewWorkflow() {
    if (!app) return
    try {
      const data = await workflowApi.preview(app.id)
      setWorkflowPreview(data.yaml)
    } catch (err) {
      alert('预览失败')
    }
  }

  async function handleCreateWorkflow() {
    if (!app) return
    setCreatingWorkflow(true)
    try {
      const result = await workflowApi.create(app.id, {
        workflowName: 'app-build.yml'
      })
      alert(`Workflow 创建成功: ${result.workflowId}`)
      setShowWorkflowModal(false)
      loadApp()
    } catch (err) {
      alert('创建失败')
    } finally {
      setCreatingWorkflow(false)
    }
  }

  if (!app) return <div>加载中...</div>

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* 左侧导航 */}
      <aside style={{
        width: 200,
        background: '#0d1b2a',
        padding: '0 0'
      }}>
        <nav>
          <button
            onClick={() => setTab('config')}
            style={{
              display: 'block',
              width: '100%',
              padding: '12px 24px',
              textAlign: 'left',
              background: tab === 'config' ? '#1890ff' : 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#fff'
            }}
          >
            APP 配置
          </button>
          <button
            onClick={() => setTab('builds')}
            style={{
              display: 'block',
              width: '100%',
              padding: '12px 24px',
              textAlign: 'left',
              background: tab === 'builds' ? '#1890ff' : 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#fff'
            }}
          >
            构建记录
          </button>
          <button
            onClick={() => setTab('workflows')}
            style={{
              display: 'block',
              width: '100%',
              padding: '12px 24px',
              textAlign: 'left',
              background: tab === 'workflows' ? '#1890ff' : 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#fff'
            }}
          >
            Workflow 管理
          </button>
        </nav>
      </aside>

      {/* 主内容区 */}
      <main style={{ flex: 1, padding: 24 }}>
        {tab === 'config' && (
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
        )}

        {tab === 'builds' && (
          <BuildRecords appId={app.id} />
        )}

        {tab === 'workflows' && (
          <div className="card">
            <div className="flex flex-between mb-16">
              <h3>Workflow 管理</h3>
              <button className="btn btn-primary" onClick={() => {
                handlePreviewWorkflow()
                setShowWorkflowModal(true)
              }}>
                创建 Workflow
              </button>
            </div>
            <table className="table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>默认</th>
                  <th>步骤数</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {workflows.map(flow => (
                  <tr key={flow.id}>
                    <td>{flow.flowName}</td>
                    <td>{flow.isDefault ? <span className="badge">默认</span> : '-'}</td>
                    <td>{JSON.parse(flow.flowConfig).length}</td>
                    <td>
                      <button
                        className="btn btn-default"
                        onClick={() => navigate(`/app/${app.id}/flows`)}
                      >
                        配置
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>

      {/* 创建 Workflow 弹窗 */}
      {showWorkflowModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            background: '#fff',
            borderRadius: 8,
            padding: 24,
            width: '80%',
            maxWidth: 800,
            maxHeight: '80vh',
            overflow: 'auto'
          }}>
            <div className="flex flex-between mb-16">
              <h2>创建 GitHub Workflow</h2>
              <button className="btn btn-default" onClick={() => setShowWorkflowModal(false)}>关闭</button>
            </div>
            <pre style={{
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: 16,
              borderRadius: 4,
              maxHeight: 400,
              overflow: 'auto',
              fontSize: 12,
              whiteSpace: 'pre-wrap'
            }}>
              {workflowPreview || '加载中...'}
            </pre>
            <div className="flex gap-8" style={{ marginTop: 16 }}>
              <button className="btn btn-primary" onClick={handleCreateWorkflow} disabled={creatingWorkflow}>
                {creatingWorkflow ? '创建中...' : '创建到 GitHub'}
              </button>
              <button className="btn btn-default" onClick={handlePreviewWorkflow}>刷新预览</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}