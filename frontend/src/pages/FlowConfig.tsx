import { useState, useEffect } from 'react'
import { flowApi, BuildFlow, FlowStep } from '../api'

interface Props {
  appId: number
}

export default function FlowConfig({ appId }: Props) {
  const [flows, setFlows] = useState<BuildFlow[]>([])
  const [showForm, setShowForm] = useState(false)
  const [editingFlow, setEditingFlow] = useState<BuildFlow | null>(null)
  const [form, setForm] = useState({
    flowName: '',
    steps: [{ type: 'gradle', name: '构建 APK', config: { task: 'assembleRelease' } } as FlowStep],
    isDefault: false
  })

  useEffect(() => {
    loadFlows()
  }, [appId])

  async function loadFlows() {
    const data = await flowApi.list(appId)
    setFlows(data)
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      if (editingFlow) {
        await flowApi.update(editingFlow.id, {
          flowName: form.flowName,
          steps: form.steps,
          isDefault: form.isDefault
        })
      } else {
        await flowApi.create({
          appId: appId,
          flowName: form.flowName,
          steps: form.steps,
          isDefault: form.isDefault
        })
      }
      resetForm()
      loadFlows()
    } catch {
      alert('保存失败')
    }
  }

  async function handleDelete(flowId: number) {
    if (confirm('确定删除？')) {
      await flowApi.delete(flowId)
      loadFlows()
    }
  }

  function startEdit(flow: BuildFlow) {
    setEditingFlow(flow)
    setForm({
      flowName: flow.flowName,
      steps: JSON.parse(flow.flowConfig),
      isDefault: flow.isDefault
    })
    setShowForm(true)
  }

  function resetForm() {
    setShowForm(false)
    setEditingFlow(null)
    setForm({
      flowName: '',
      steps: [{ type: 'gradle', name: '构建 APK', config: { task: 'assembleRelease' } }],
      isDefault: false
    })
  }

  function addStep() {
    setForm({ ...form, steps: [...form.steps, { type: 'shell', name: '', config: {} } as FlowStep] })
  }

  function removeStep(index: number) {
    setForm({ ...form, steps: form.steps.filter((_, i) => i !== index) })
  }

  function updateStep(index: number, step: FlowStep) {
    const newSteps = [...form.steps]
    newSteps[index] = step
    setForm({ ...form, steps: newSteps })
  }

  return (
    <div>
      <div className="card">
        <div className="flex flex-between mb-16">
          <h3>构建流程</h3>
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>新建流程</button>
        </div>

        {showForm && (
          <form onSubmit={handleCreate} className="mb-16">
            <div className="form-item">
              <label>流程名称</label>
              <input value={form.flowName} onChange={e => setForm({ ...form, flowName: e.target.value })} required />
            </div>

            <div className="form-item">
              <label>步骤配置</label>
              {form.steps.map((step, index) => (
                <div key={index} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <select
                    value={step.type}
                    onChange={e => updateStep(index, { ...step, type: e.target.value })}
                    style={{ width: 120 }}
                  >
                    <option value="gradle">Gradle Task</option>
                    <option value="shell">Shell 脚本</option>
                  </select>
                  <input
                    placeholder="步骤名称"
                    value={step.name}
                    onChange={e => updateStep(index, { ...step, name: e.target.value })}
                    style={{ flex: 1 }}
                  />
                  {step.type === 'gradle' && (
                    <input
                      placeholder="Task 名，如 assembleRelease"
                      value={(step.config as Record<string, string>).task || ''}
                      onChange={e => updateStep(index, { ...step, config: { task: e.target.value } })}
                      style={{ width: 200 }}
                    />
                  )}
                  <button type="button" className="btn btn-danger" onClick={() => removeStep(index)}>删除</button>
                </div>
              ))}
              <button type="button" className="btn btn-default" onClick={addStep}>+ 添加步骤</button>
            </div>

            <div className="form-item">
              <label>
                <input
                  type="checkbox"
                  checked={form.isDefault}
                  onChange={e => setForm({ ...form, isDefault: e.target.checked })}
                  style={{ width: 'auto', marginRight: 8 }}
                />
                设为默认流程
              </label>
            </div>

            <div className="flex gap-8">
              <button type="submit" className="btn btn-primary">{editingFlow ? '保存' : '创建'}</button>
              <button type="button" className="btn btn-default" onClick={resetForm}>取消</button>
            </div>
          </form>
        )}

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
            {flows.map(flow => (
              <tr key={flow.id}>
                <td>{flow.flowName}</td>
                <td>{flow.isDefault ? <span className="badge">默认</span> : '-'}</td>
                <td>{JSON.parse(flow.flowConfig).length}</td>
                <td>
                  <div className="flex gap-8">
                    <button className="btn btn-default" onClick={() => startEdit(flow)}>编辑</button>
                    <button className="btn btn-danger" onClick={() => handleDelete(flow.id)}>删除</button>
                  </div>
                </td>
              </tr>
            ))}
            {flows.length === 0 && (
              <tr><td colSpan={4} style={{ textAlign: 'center', color: '#999' }}>暂无构建流程</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}