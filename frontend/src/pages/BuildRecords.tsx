import { useState, useEffect } from 'react'
import { buildApi, BuildRecord, githubApi, appApi } from '../api'

interface Props {
  appId: number
}

interface Workflow {
  id: string
  name: string
  path: string
}

export default function BuildRecords({ appId }: Props) {
  const [builds, setBuilds] = useState<BuildRecord[]>([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [selectedBuild, setSelectedBuild] = useState<BuildRecord | null>(null)
  const [logs, setLogs] = useState('')
  const [loadingLogs, setLoadingLogs] = useState(false)
  const [buildModal, setBuildModal] = useState(false)
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [branches, setBranches] = useState<string[]>([])
  const [selectedWorkflow, setSelectedWorkflow] = useState('')
  const [selectedBranch, setSelectedBranch] = useState('')
  const [triggering, setTriggering] = useState(false)
  const [repoUrl, setRepoUrl] = useState('')

  const pageSize = 20

  useEffect(() => {
    loadApp()
    loadBuilds()
  }, [appId])

  // 每 10 秒刷新一次 pending/running 状态的构建
  useEffect(() => {
    const hasRunning = builds.some(b => b.status === 'pending' || b.status === 'running')
    if (!hasRunning) return
    const timer = setInterval(() => loadBuilds(), 10000)
    return () => clearInterval(timer)
  }, [builds, appId, page])

  async function loadApp() {
    const app = await appApi.get(appId)
    setRepoUrl(app.repoUrl)
  }

  async function loadBuilds() {
    const data = await buildApi.list(appId, page, pageSize)
    setBuilds(data.content)
    setTotal(data.totalElements)
  }

  async function openBuildModal() {
    setBuildModal(true)
    setTriggering(true)
    try {
      const [workflowList, branchList] = await Promise.all([
        githubApi.listWorkflows(appId),
        githubApi.listBranches(appId)
      ])
      setWorkflows(workflowList)
      setBranches(branchList)
      if (workflowList.length > 0) setSelectedWorkflow(workflowList[0].path)
      if (branchList.length > 0) setSelectedBranch(branchList[0])
    } catch {
      setWorkflows([])
      setBranches([])
    } finally {
      setTriggering(false)
    }
  }

  async function handleTriggerBuild() {
    if (!selectedWorkflow || !selectedBranch) return
    setTriggering(true)
    try {
      await buildApi.trigger(appId, undefined, undefined, selectedWorkflow, selectedBranch)
      setBuildModal(false)
      loadBuilds()
    } catch {
      // 错误处理
    } finally {
      setTriggering(false)
    }
  }

  async function handleViewLogs(build: BuildRecord) {
    setSelectedBuild(build)
    setLoadingLogs(true)
    try {
      const data = await buildApi.getLogs(build.id)
      setLogs(data.logs || '暂无日志')
    } catch {
      setLogs('获取日志失败')
    } finally {
      setLoadingLogs(false)
    }
  }

  function getStatusClass(status: string) {
    return `status status-${status}`
  }

  function formatDuration(seconds: number | null) {
    if (seconds == null) return '-'
    if (seconds < 60) return `${seconds}s`
    return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  }

  return (
    <div>
      <div className="card">
        <div className="flex flex-between mb-16">
          <h3>构建记录</h3>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button className="btn btn-primary" onClick={openBuildModal}>构建</button>
            <span>共 {total} 条</span>
            <button className="btn btn-default" disabled={page === 0} onClick={() => setPage(p => p - 1)}>上一页</button>
            <span>{page + 1}</span>
            <button className="btn btn-default" disabled={builds.length < pageSize} onClick={() => setPage(p => p + 1)}>下一页</button>
          </div>
        </div>

        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>状态</th>
              <th>Commit</th>
              <th>时长</th>
              <th>产物</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {builds.map(build => (
              <tr key={build.id}>
                <td>{build.buildNumber}</td>
                <td><span className={getStatusClass(build.status)}>{build.status}</span></td>
                <td>{build.commitSha?.slice(0, 7)}</td>
                <td>{formatDuration(build.durationSeconds)}</td>
                <td>
                  {build.artifactUrl ? (
                    <a href={build.artifactUrl} target="_blank" rel="noopener noreferrer">下载 APK</a>
                  ) : '-'}
                </td>
                <td>{new Date(build.createdAt).toLocaleString()}</td>
                <td>
                  {build.workflowRunId ? (
                    <a
                      href={`https://github.com/${repoUrl.replace('https://github.com/', '')}/actions/runs/${build.workflowRunId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-default"
                    >
                      日志
                    </a>
                  ) : (
                    <span style={{ color: '#999' }}>日志</span>
                  )}
                </td>
              </tr>
            ))}
            {builds.length === 0 && (
              <tr><td colSpan={7} style={{ textAlign: 'center', color: '#999' }}>暂无构建记录</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {selectedBuild && (
        <div className="card">
          <div className="flex flex-between mb-16">
            <h3>构建 #{selectedBuild.buildNumber} 日志</h3>
            <button className="btn btn-default" onClick={() => setSelectedBuild(null)}>关闭</button>
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
            {loadingLogs ? '加载中...' : logs}
          </pre>
        </div>
      )}

      {buildModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{ background: '#fff', padding: 24, borderRadius: 8, minWidth: 400 }}>
            <h4 style={{ marginBottom: 16 }}>触发构建</h4>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>Workflow</label>
              <select
                className="form-control"
                value={selectedWorkflow}
                onChange={e => setSelectedWorkflow(e.target.value)}
                style={{ width: '100%' }}
              >
                {workflows.map(wf => (
                  <option key={wf.id} value={wf.path}>{wf.name}</option>
                ))}
              </select>
            </div>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>分支</label>
              <select
                className="form-control"
                value={selectedBranch}
                onChange={e => setSelectedBranch(e.target.value)}
                style={{ width: '100%' }}
              >
                {branches.map(br => (
                  <option key={br} value={br}>{br}</option>
                ))}
              </select>
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button className="btn btn-default" onClick={() => setBuildModal(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleTriggerBuild} disabled={triggering}>确定</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}