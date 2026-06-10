import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { buildApi, BuildRecord, appApi } from '../api'

export default function BuildRecordDetail() {
  const { id, workflowRunId } = useParams<{ id: string; workflowRunId: string }>()
  const [build, setBuild] = useState<BuildRecord | null>(null)
  const [repoUrl, setRepoUrl] = useState('')
  const [logs, setLogs] = useState('')
  const [loadingLogs, setLoadingLogs] = useState(false)

  useEffect(() => {
    loadBuild()
    loadApp()
  }, [id, workflowRunId])

  async function loadBuild() {
    if (!workflowRunId) return
    const data = await buildApi.getByRunId(workflowRunId)
    setBuild(data)
    loadLogs()
  }

  async function loadApp() {
    const app = await appApi.get(Number(id))
    setRepoUrl(app.repoUrl)
  }

  async function loadLogs() {
    if (!workflowRunId) return
    setLoadingLogs(true)
    try {
      const data = await buildApi.getLogsByRunId(workflowRunId)
      setLogs(data.logs || '暂无日志')
    } catch {
      setLogs('获取日志失败')
    } finally {
      setLoadingLogs(false)
    }
  }

  function getStatusIcon(status: string) {
    switch (status) {
      case 'pending': return '⏳'
      case 'running': return '🔄'
      case 'success': return '✅'
      case 'failed': return '❌'
      default: return '⚪'
    }
  }

  function formatDuration(seconds: number | string | null) {
    if (seconds == null) return '-'
    const s = typeof seconds === 'string' ? parseInt(seconds, 10) : seconds
    if (s < 60) return `${s}s`
    return `${Math.floor(s / 60)}m ${s % 60}s`
  }

  function formatTime(timestamp: number | string | null) {
    if (timestamp == null) return '-'
    return new Date(typeof timestamp === 'string' ? parseInt(timestamp, 10) : timestamp).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
  }

  if (!build) return <div>加载中...</div>

  return (
    <div>
      <div className="card">
        <div className="flex flex-between mb-16">
          <h3>构建 #{build.buildNumber} <span className={`status status-${build.status}`}>{getStatusIcon(build.status)} {build.status}</span></h3>
          <Link to={`/app/${id}/builds`} className="btn btn-default">返回列表</Link>
        </div>

        <div style={{ display: 'flex', gap: 48 }}>
          <div style={{ flex: 1 }}>
            <table className="table">
              <tbody>
                <tr>
                  <td style={{ width: 80 }}>Commit</td>
                  <td>
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
                </tr>
                <tr>
                  <td>时长</td>
                  <td>{formatDuration(build.durationSeconds)}</td>
                </tr>
                <tr>
                  <td>时间</td>
                  <td>{formatTime(build.startedAt)} ~ {formatTime(build.finishedAt)}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div style={{ flex: 1 }}>
            <table className="table">
              <tbody>
                <tr>
                  <td style={{ width: 100 }}>Actions</td>
                  <td>
                    {build.workflowRunId ? (
                      <a
                        href={`https://github.com/${repoUrl.replace('https://github.com/', '')}/actions/runs/${build.workflowRunId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        查看 Run
                      </a>
                    ) : '-'}
                  </td>
                </tr>
                <tr>
                  <td>下载</td>
                  <td>
                    {build.artifactUrl ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                        <a href={build.artifactUrl} target="_blank" rel="noopener noreferrer" style={{ maxWidth: 300, wordBreak: 'break-all' }}>{build.artifactUrl}</a>
                        <img
                          src={`https://api.qrserver.com/v1/create-qr-code/?size=120x120&data=${encodeURIComponent(build.artifactUrl)}`}
                          alt="扫码下载"
                          style={{ width: 120, height: 120 }}
                        />
                      </div>
                    ) : '-'}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="flex flex-between mb-16">
          <h3>构建日志</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-default" onClick={loadLogs}>刷新</button>
            <button className="btn btn-default" onClick={() => {
              const blob = new Blob([logs], { type: 'text/plain' })
              const url = URL.createObjectURL(blob)
              const a = document.createElement('a')
              a.href = url
              a.download = `build-${build.buildNumber}-logs.txt`
              a.click()
              URL.revokeObjectURL(url)
            }}>下载</button>
          </div>
        </div>
        <pre style={{
          background: '#1e1e1e',
          color: '#d4d4d4',
          padding: 16,
          borderRadius: 4,
          maxHeight: 500,
          overflow: 'auto',
          fontSize: 12,
          whiteSpace: 'pre-wrap'
        }}>
          {loadingLogs ? '加载中...' : logs}
        </pre>
      </div>
    </div>
  )
}