import { useState, useEffect } from 'react'
import { appApi, AppInfo } from '../api'

interface Props {
  onSelect: (app: AppInfo) => void
  onCreateNew: () => void
}

export default function AppSelector({ onSelect, onCreateNew }: Props) {
  const [apps, setApps] = useState<AppInfo[]>([])
  const [showCreateModal, setShowCreateModal] = useState(false)

  useEffect(() => {
    loadApps()
  }, [])

  async function loadApps() {
    const data = await appApi.list()
    setApps(data)
  }

  async function handleSelect(appId: string) {
    if (!appId) return
    const app = apps.find(a => a.id === Number(appId))
    if (app) onSelect(app)
  }

  async function handleCreateNew() {
    setShowCreateModal(true)
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'nowrap', whiteSpace: 'nowrap' }}>
        <select
          defaultValue=""
          onChange={e => handleSelect(e.target.value)}
          style={{ width: 180, flexShrink: 0 }}
        >
          <option value="" disabled>选择 App</option>
          {apps.map(app => (
            <option key={app.id} value={app.id}>
              {app.appName}
            </option>
          ))}
        </select>
        <button className="btn btn-primary" onClick={handleCreateNew}>+ 新建 App</button>
      </div>

      {showCreateModal && (
        <CreateAppModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={(app) => {
            loadApps()
            onSelect(app)
            setShowCreateModal(false)
          }}
        />
      )}
    </>
  )
}

interface CreateAppModalProps {
  onClose: () => void
  onSuccess: (app: AppInfo) => void
}

function CreateAppModal({ onClose, onSuccess }: CreateAppModalProps) {
  const [repoUrl, setRepoUrl] = useState('')
  const [appName, setAppName] = useState('')
  const [branch, setBranch] = useState('main')
  const [loading, setLoading] = useState(false)

  function handleRepoUrlChange(value: string) {
    setRepoUrl(value)
    const match = value.match(/([^/]+)\/?$/)
    if (match) {
      setAppName(match[1].replace(/\.git$/, ''))
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      const app = await appApi.create({ repoUrl, branch, appName })
      onSuccess(app)
    } catch {
      alert('创建失败')
    } finally {
      setLoading(false)
    }
  }

  return (
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
        width: 400
      }}>
        <h2 style={{ marginBottom: 16 }}>新建 App</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-item">
            <label>仓库地址</label>
            <input
              value={repoUrl}
              onChange={e => handleRepoUrlChange(e.target.value)}
              placeholder="https://github.com/owner/repo"
              required
            />
          </div>
          <div className="form-item">
            <label>应用名称</label>
            <input
              value={appName}
              onChange={e => setAppName(e.target.value)}
              placeholder="从仓库地址自动提取"
              required
            />
          </div>
          <div className="form-item">
            <label>主分支</label>
            <input value={branch} onChange={e => setBranch(e.target.value)} />
          </div>
          <div className="flex gap-8" style={{ marginTop: 16 }}>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? '创建中...' : '创建'}
            </button>
            <button type="button" className="btn btn-default" onClick={onClose}>取消</button>
          </div>
        </form>
      </div>
    </div>
  )
}