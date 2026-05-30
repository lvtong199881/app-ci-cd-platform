import { useState, useEffect } from 'react'
import { buildApi, BuildRecord } from '../api'

interface Props {
  appId: number
}

export default function BuildRecords({ appId }: Props) {
  const [builds, setBuilds] = useState<BuildRecord[]>([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [selectedBuild, setSelectedBuild] = useState<BuildRecord | null>(null)
  const [logs, setLogs] = useState('')
  const [loadingLogs, setLoadingLogs] = useState(false)

  const pageSize = 20

  useEffect(() => {
    loadBuilds()
  }, [appId, page])

  async function loadBuilds() {
    const data = await buildApi.list(appId, page, pageSize)
    setBuilds(data.content)
    setTotal(data.totalElements)
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
                  <button className="btn btn-default" onClick={() => handleViewLogs(build)}>日志</button>
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
    </div>
  )
}