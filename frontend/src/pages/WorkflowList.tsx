import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { appApi, githubApi, AppInfo } from '../api'

export default function WorkflowList() {
  const { id } = useParams<{ id: string }>()
  const [app, setApp] = useState<AppInfo | null>(null)
  const [workflows, setWorkflows] = useState<{ id: string; name: string; path: string }[]>([])
  const [deleteModal, setDeleteModal] = useState<{ show: boolean; workflow: { id: string; name: string; path: string } | null }>({ show: false, workflow: null })
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (id) loadApp()
  }, [id])

  async function loadApp() {
    const appData = await appApi.get(Number(id))
    setApp(appData)
    if (appData.installationId) {
      const data = await githubApi.listWorkflows(appData.id)
      setWorkflows(data)
    }
  }

  async function handleDelete() {
    if (!deleteModal.workflow || !app) return
    setDeleting(true)
    try {
      await githubApi.deleteWorkflow(app.id, deleteModal.workflow.path)
      setWorkflows(workflows.filter(w => w.id !== deleteModal.workflow!.id))
      setDeleteModal({ show: false, workflow: null })
    } catch (err: any) {
      alert('删除失败: ' + (err?.response?.data?.error || err?.message || '未知错误'))
    } finally {
      setDeleting(false)
    }
  }

  if (!app) return <div>加载中...</div>

  return (
    <div className="card">
      <div className="flex flex-between mb-16">
        <h3>GitHub Workflows</h3>
        <button className="btn btn-primary" onClick={() => window.location.href = `/app/${id}/workflow/create`}>
          创建 Workflow
        </button>
      </div>
      <table className="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>文件路径</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {workflows.length === 0 ? (
            <tr>
              <td colSpan={3} style={{ textAlign: 'center', color: '#999' }}>暂无 Workflow</td>
            </tr>
          ) : (
            workflows.map(wf => (
              <tr key={wf.id}>
                <td>{wf.name}</td>
                <td>{wf.path}</td>
                <td>
                  <button
                    className="btn btn-default"
                    onClick={() => window.open(`https://github.com/${app.repoUrl.split('github.com/')[1]}/blob/main/${wf.path}`, '_blank')}
                  >
                    查看
                  </button>
                  <button
                    className="btn btn-default"
                    style={{ marginLeft: 8 }}
                    onClick={() => window.location.href = `/app/${id}/workflow/create?path=${encodeURIComponent(wf.path)}`}
                  >
                    编辑
                  </button>
                  <button
                    className="btn btn-danger"
                    style={{ marginLeft: 8 }}
                    onClick={() => setDeleteModal({ show: true, workflow: wf })}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {deleteModal.show && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
        }}>
          <div style={{ background: '#fff', padding: 24, borderRadius: 8, minWidth: 320 }}>
            <h4 style={{ marginBottom: 16 }}>确认删除</h4>
            <p style={{ marginBottom: 24 }}>确定要删除 Workflow "{deleteModal.workflow?.name}" 吗？此操作不可恢复。</p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button className="btn btn-default" onClick={() => setDeleteModal({ show: false, workflow: null })}>取消</button>
              <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                {deleting ? '删除中...' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}