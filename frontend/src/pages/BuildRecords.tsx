import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
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
  const [buildModal, setBuildModal] = useState(false)
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [branches, setBranches] = useState<string[]>([])
  const [selectedWorkflow, setSelectedWorkflow] = useState('')
  const [selectedBranch, setSelectedBranch] = useState('')
  const [triggering, setTriggering] = useState(false)
  const [repoUrl, setRepoUrl] = useState('')
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const pageSize = 20

  useEffect(() => {
    loadApp()
    loadBuilds()
  }, [appId])

  // 启动或停止轮询
  useEffect(() => {
    const hasRunning = builds.some(b => b.status === 'pending' || b.status === 'running')
    if (hasRunning && !timerRef.current) {
      timerRef.current = setInterval(() => loadBuilds(), 5000)
    } else if (!hasRunning && timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
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

   function getStatusIcon(status: string) {
    switch (status) {
      case 'pending': return <span>⏳</span>
      case 'running': return <svg className="spinner" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#1890ff" strokeWidth="2"><path d="M12 2v4M12 18v4M4.93 4.93l2.832.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>
      case 'success': return <span>✅</span>
      case 'failed': return <span>❌</span>
      default: return <span>⚪</span>
    }
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
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {builds.map(build => (
              <tr key={build.id}>
                <td>
                  {build.workflowRunId ? (
                    <a
                      href={`https://github.com/${repoUrl.replace('https://github.com/', '')}/actions/runs/${build.workflowRunId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {build.workflowRunId}
                    </a>
                  ) : '-'}
                </td>
                <td style={{ width: 'auto', whiteSpace: 'nowrap' }}><span className={`status status-${build.status}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>{getStatusIcon(build.status)} {build.status}</span></td>
                <td style={{ width: 'auto', whiteSpace: 'nowrap' }}>
                  {build.commitSha ? (
                    <a
                      href={`https://github.com/${repoUrl.replace('https://github.com/', '')}/commit/${build.commitSha}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {build.commitSha.slice(0, 7)}
                    </a>
                  ) : '-'}
                </td>
                <td>{formatDuration(build.durationSeconds)}</td>
                <td>{new Date(build.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })}</td>
                <td>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <Link to={`/app/${appId}/builds/${build.workflowRunId}`} className="btn btn-default">详情</Link>
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
                    {build.artifactUrl ? (
                      <div style={{ position: 'relative' }}
                        onMouseEnter={e => (e.currentTarget as HTMLElement).querySelector('.qr-popup')?.setAttribute('style', 'display:block')}
                        onMouseLeave={e => (e.currentTarget as HTMLElement).querySelector('.qr-popup')?.setAttribute('style', 'display:none')}
                      >
                        <a href={build.artifactUrl} target="_blank" rel="noopener noreferrer" className="btn btn-default">下载</a>
                        <div className="qr-popup" style={{ display: 'none', position: 'absolute', top: '100%', left: '50%', transform: 'translateX(-50%)', marginTop: 8, padding: 8, background: '#fff', border: '1px solid #ddd', borderRadius: 4, zIndex: 100, boxShadow: '0 2px 8px rgba(0,0,0,0.15)' }}>
                          <img
                            src={`https://api.qrserver.com/v1/create-qr-code/?size=120x120&data=${encodeURIComponent(build.artifactUrl)}`}
                            alt="扫码下载"
                            style={{ width: 120, height: 120 }}
                          />
                        </div>
                      </div>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
            {builds.length === 0 && (
              <tr><td colSpan={6} style={{ textAlign: 'center', color: '#999' }}>暂无构建记录</td></tr>
            )}
          </tbody>
        </table>
      </div>

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