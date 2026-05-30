import { useState, useEffect } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { appApi, workflowApi, githubApi, AppInfo, WorkflowConfig, Job, JobStep, StepType } from '../api'

const STEP_TEMPLATES: Record<StepType, { name: string; defaultConfig: Record<string, string> }> = {
  'checkout': { name: '检出代码', defaultConfig: {} },
  'setup-jdk': { name: '设置 JDK', defaultConfig: { version: '17', distribution: 'temurin' } },
  'setup-android': { name: '设置 Android SDK', defaultConfig: { version: 'latest' } },
  'cache': { name: '缓存依赖', defaultConfig: { path: '.gradle/caches' } },
  'gradle': { name: 'Gradle 构建', defaultConfig: { task: 'assembleRelease' } },
  'gradle-test': { name: 'Gradle 测试', defaultConfig: { task: 'test' } },
  'shell': { name: 'Shell 脚本', defaultConfig: { script: 'echo hello' } },
  'upload-artifact': { name: '上传产物', defaultConfig: { name: 'app-release-${{ github.run_id }}', path: 'app/build/outputs/apk/release/*.apk' } }
}

function createDefaultSteps(): JobStep[] {
  return [
    { id: '1', name: '检出代码', type: 'checkout', config: {} },
    { id: '2', name: '设置 JDK', type: 'setup-jdk', config: { version: '17', distribution: 'temurin' } },
    { id: '3', name: '设置 Android SDK', type: 'setup-android', config: { version: 'latest' } },
    { id: '4', name: 'Gradle 构建', type: 'gradle', config: { task: 'assembleRelease' } },
    { id: '5', name: '上传产物', type: 'upload-artifact', config: { name: 'app-release', path: 'app/build/outputs/apk/release/*.apk' } }
  ]
}

function createStep(type: StepType): JobStep {
  const template = STEP_TEMPLATES[type]
  return {
    id: Date.now().toString(),
    name: template.name,
    type,
    config: { ...template.defaultConfig }
  }
}

// 生成单个 Step 的 YAML 预览
function generateStepYaml(step: JobStep): string {
  switch (step.type) {
    case 'checkout':
      return `- name: ${step.name}\n  uses: actions/checkout@v4`
    case 'setup-jdk':
      return `- name: ${step.name}\n  uses: actions/setup-java@v4\n  with:\n    distribution: '${step.config.distribution || 'temurin'}'\n    java-version: '${step.config.version || '17'}'`
    case 'setup-android':
      return `- name: ${step.name}\n  uses: android-actions/setup-android@v3\n  with:\n    android-version: '${step.config.version || 'latest'}'`
    case 'cache':
      return `- name: ${step.name}\n  uses: actions/cache@v4\n  with:\n    path: ${step.config.path || '.gradle/caches'}\n    key: \${runner.os}-gradle-\${hashFiles('**/*.lock')}`
    case 'gradle':
      return `- name: ${step.name}\n  run: ./gradlew ${step.config.task || 'assembleRelease'}\n  shell: bash`
    case 'gradle-test':
      return `- name: ${step.name}\n  run: ./gradlew ${step.config.task || 'test'}\n  shell: bash`
    case 'shell':
      return `- name: ${step.name}\n  run: ${step.config.script || 'echo hello'}\n  shell: bash`
    case 'upload-artifact':
      return `- name: ${step.name}\n  uses: actions/upload-artifact@v4\n  with:\n    name: ${step.config.name || 'app-release'}\n    path: ${step.config.path || 'app/build/outputs/apk/release/*.apk'}`
    default:
      return `# Unknown step type: ${step.type}`
  }
}

function StepsGraph({ steps, selectedStepId, onSelectStep }: { steps: JobStep[]; selectedStepId: string | null; onSelectStep: (id: string) => void }) {
  const NODE_WIDTH = 140
  const NODE_HEIGHT = 36
  const GAP_X = 60
  const GAP_Y = 50
  const PADDING = 30

  const svgWidth = steps.length * (NODE_WIDTH + GAP_X) + PADDING * 2
  const svgHeight = NODE_HEIGHT + GAP_Y * 2 + PADDING * 2

  function getNodePos(index: number) {
    return {
      x: PADDING + index * (NODE_WIDTH + GAP_X),
      y: PADDING + GAP_Y
    }
  }

  function getConnectionPath(fromIndex: number, toIndex: number) {
    const from = getNodePos(fromIndex)
    const to = getNodePos(toIndex)
    const fromX = from.x + NODE_WIDTH
    const fromY = from.y + NODE_HEIGHT / 2
    const toX = to.x
    const toY = to.y + NODE_HEIGHT / 2
    const midX = (fromX + toX) / 2
    return `M ${fromX} ${fromY} C ${midX} ${fromY}, ${midX} ${toY}, ${toX} ${toY}`
  }

  if (steps.length === 0) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', fontSize: 13 }}>
        点击右侧按钮添加 Step
      </div>
    )
  }

  return (
    <div style={{ flex: 1, overflow: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg width={Math.max(svgWidth, 300)} height={svgHeight} style={{ overflow: 'visible' }}>
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#1890ff" />
          </marker>
        </defs>
        {steps.slice(0, -1).map((_, index) => (
          <path key={`line-${index}`} d={getConnectionPath(index, index + 1)} stroke="#1890ff" strokeWidth={2} fill="none" markerEnd="url(#arrowhead)" />
        ))}
        {steps.map((step, index) => {
          const pos = getNodePos(index)
          const isSelected = selectedStepId === step.id
          return (
            <g key={step.id} onClick={() => onSelectStep(step.id)} style={{ cursor: 'pointer' }}>
              <rect x={pos.x} y={pos.y} width={NODE_WIDTH} height={NODE_HEIGHT} rx={18} ry={18}
                fill={isSelected ? '#e6f7ff' : '#fff'} stroke={isSelected ? '#1890ff' : '#ddd'} strokeWidth={isSelected ? 2 : 1} />
              <text x={pos.x + NODE_WIDTH / 2} y={pos.y + NODE_HEIGHT / 2 + 5} textAnchor="middle"
                fill={isSelected ? '#1890ff' : '#333'} fontSize={12} fontWeight={isSelected ? 'bold' : 'normal'}>
                {step.name.length > 12 ? step.name.slice(0, 12) + '...' : step.name}
              </text>
            </g>
          )
        })}
      </svg>
    </div>
  )
}

export default function WorkflowCreate() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const [app, setApp] = useState<AppInfo | null>(null)
  const [workflowName, setWorkflowName] = useState('App Build')
  const [steps, setSteps] = useState<JobStep[]>(createDefaultSteps())
  const [selectedStepId, setSelectedStepId] = useState<string | null>('1')
  const [creatingWorkflow, setCreatingWorkflow] = useState(false)
  const [editPath, setEditPath] = useState<string | null>(null)
  const [nameError, setNameError] = useState<string | null>(null)

  useEffect(() => {
    if (id) loadApp()
  }, [id])

  async function loadApp() {
    const appData = await appApi.get(Number(id))
    setApp(appData)
    const path = searchParams.get('path')
    if (path) {
      setEditPath(path)
      try {
        const content = await githubApi.getWorkflowFile(appData.id, path)
        parseWorkflowContent(content)
      } catch (err) {
        console.error('加载 workflow 失败', err)
      }
    }
  }

  function parseWorkflowContent(content: string) {
    try {
      const yaml = content
      const nameMatch = yaml.match(/^name:\s*(.+)$/m)
      if (nameMatch) setWorkflowName(nameMatch[1].trim())

      // 简单的 steps 解析
      const steps: JobStep[] = []
      const lines = yaml.split('\n')
      let currentStep: Partial<JobStep> | null = null
      let stepId = 1

      for (const line of lines) {
        if (line.match(/^\s*-\s+name:\s*(.+)$/)) {
          if (currentStep?.name) {
            steps.push(currentStep as JobStep)
          }
          const name = line.replace(/^\s*-\s+name:\s*/, '').trim()
          currentStep = { id: String(stepId++), name, type: 'shell', config: {} }
        } else if (line.match(/^\s*uses:\s*(.+)$/) && currentStep) {
          const uses = line.replace(/^\s*uses:\s*/, '').trim()
          if (uses.includes('checkout')) currentStep.type = 'checkout'
          else if (uses.includes('setup-java')) currentStep.type = 'setup-jdk'
          else if (uses.includes('setup-android')) currentStep.type = 'setup-android'
          else if (uses.includes('cache')) currentStep.type = 'cache'
          else if (uses.includes('upload-artifact')) currentStep.type = 'upload-artifact'
        } else if (line.match(/^\s*run:\s*(.+)$/) && currentStep) {
          const run = line.replace(/^\s*run:\s*/, '').trim()
          if (run.includes('gradlew')) {
            const taskMatch = run.match(/gradlew\s+(\S+)/)
            currentStep.type = taskMatch?.includes('test') ? 'gradle-test' : 'gradle'
            currentStep.config = { task: taskMatch?.[1] || 'assembleRelease' }
          } else {
            currentStep.type = 'shell'
            currentStep.config = { script: run }
          }
        } else if (line.match(/^\s*with:/) && currentStep) {
          // 解析 with 块
        }
      }
      if (currentStep?.name) {
        steps.push(currentStep as JobStep)
      }
      if (steps.length > 0) {
        setSteps(steps)
        setSelectedStepId(steps[0].id)
      }
    } catch (err) {
      console.error('解析 workflow 内容失败', err)
    }
  }

  
  async function handleSave() {
    if (!app) return
    if (!workflowName.trim()) {
      setNameError('Workflow 名称不能为空')
      return
    }
    if (!/^[a-zA-Z0-9_\-]+$/.test(workflowName)) {
      setNameError('Workflow 名称只能包含中文、英文、数字、连字符和下划线')
      return
    }
    setCreatingWorkflow(true)
    try {
      const workflow: WorkflowConfig = {
        name: workflowName,
        jobs: [{ id: 'build', name: 'Build', runsOn: 'ubuntu-latest', needs: [], steps }]
      }
      if (editPath) {
        await workflowApi.update(app.id, editPath, workflow)
        alert('Workflow 更新成功')
      } else {
        await workflowApi.create(app.id, workflow)
        alert(`Workflow 创建成功`)
      }
      window.location.href = `/app/${id}/workflows`
    } catch (err: any) {
      console.error('保存失败', err)
      alert((editPath ? '更新' : '创建') + '失败: ' + (err?.response?.data?.error || err?.message || '未知错误'))
    } finally {
      setCreatingWorkflow(false)
    }
  }

  function getSelectedStep() {
    return steps.find(s => s.id === selectedStepId)
  }

  function addStep(type: StepType) {
    const newStep = createStep(type)
    setSteps([...steps, newStep])
    setSelectedStepId(newStep.id)
  }

  function updateStep(stepId: string, updates: Partial<JobStep>) {
    setSteps(steps.map(s => s.id === stepId ? { ...s, ...updates } : s))
  }

  function deleteStep(stepId: string) {
    const newSteps = steps.filter(s => s.id !== stepId)
    setSteps(newSteps)
    if (selectedStepId === stepId) {
      setSelectedStepId(newSteps.length > 0 ? newSteps[0].id : null)
    }
  }

  function moveStep(stepId: string, direction: 'up' | 'down') {
    const index = steps.findIndex(s => s.id === stepId)
    if (index < 0) return
    if (direction === 'up' && index === 0) return
    if (direction === 'down' && index === steps.length - 1) return
    const newSteps = [...steps]
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    ;[newSteps[index], newSteps[targetIndex]] = [newSteps[targetIndex], newSteps[index]]
    setSteps(newSteps)
  }

  if (!app) return <div>加载中...</div>

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', padding: 24 }}>
      <div className="flex flex-between mb-16">
        <div>
          <h2>{editPath ? '编辑 Workflow' : '创建 Workflow'}</h2>
          <a href={`/app/${id}/workflows`} style={{ fontSize: 12, color: '#999' }}>← 返回</a>
        </div>
        <div className="flex gap-8">
          <button className="btn btn-primary" onClick={handleSave} disabled={creatingWorkflow}>
            {creatingWorkflow ? '保存中...' : (editPath ? '保存到 GitHub' : '创建到 GitHub')}
          </button>
        </div>
      </div>

      <div className="form-item mb-16">
        <label>Workflow 名称</label>
        <input
          value={workflowName}
          onChange={e => { setWorkflowName(e.target.value); setNameError(null) }}
          style={{ width: 300, borderColor: nameError ? 'red' : undefined }}
          placeholder="请输入 workflow 名称"
        />
        {nameError && <div style={{ color: 'red', fontSize: 12, marginTop: 4 }}>{nameError}</div>}
      </div>

      {/* 上方 - Steps 有向图 */}
      <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, minHeight: 120, marginBottom: 16 }}>
        <h4>Steps 流程图</h4>
        <StepsGraph steps={steps} selectedStepId={selectedStepId} onSelectStep={setSelectedStepId} />
      </div>

      {/* 下方 */}
      <div style={{ flex: 1, border: '1px solid #ddd', borderRadius: 8, padding: 12, display: 'flex', gap: 16, overflow: 'hidden' }}>
        <div style={{ flex: 1, overflow: 'auto' }}>
          {getSelectedStep() ? (
            <>
              <div className="flex flex-between mb-12">
                <h4>Step 配置</h4>
                <div className="flex gap-8">
                  <button className="btn btn-default" style={{ padding: '4px 8px', fontSize: 11 }} onClick={() => moveStep(selectedStepId!, 'up')}>上移</button>
                  <button className="btn btn-default" style={{ padding: '4px 8px', fontSize: 11 }} onClick={() => moveStep(selectedStepId!, 'down')}>下移</button>
                  <button className="btn btn-danger" style={{ padding: '4px 8px', fontSize: 11 }} onClick={() => deleteStep(selectedStepId!)}>删除</button>
                </div>
              </div>
              <div className="form-item mb-12">
                <label>名称</label>
                <input value={getSelectedStep()!.name} onChange={e => updateStep(selectedStepId!, { name: e.target.value })} />
              </div>
              {getSelectedStep()!.type === 'checkout' && (
                <div className="form-item mb-12" style={{ color: '#999', fontSize: 12 }}>该步骤无需配置参数</div>
              )}
              {getSelectedStep()!.type === 'setup-jdk' && (
                <>
                  <div className="form-item mb-12">
                    <label>JDK 版本</label>
                    <input value={getSelectedStep()!.config.version || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, version: e.target.value } })} placeholder="17" />
                  </div>
                  <div className="form-item mb-12">
                    <label>发行版</label>
                    <select value={getSelectedStep()!.config.distribution || 'temurin'} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, distribution: e.target.value } })} style={{ width: '100%' }}>
                      <option value="temurin">Temurin</option>
                      <option value="oracle">Oracle</option>
                      <option value="adopt">Adopt</option>
                    </select>
                  </div>
                </>
              )}
              {getSelectedStep()!.type === 'setup-android' && (
                <div className="form-item mb-12">
                  <label>Android SDK 版本</label>
                  <input value={getSelectedStep()!.config.version || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, version: e.target.value } })} placeholder="latest" />
                </div>
              )}
              {getSelectedStep()!.type === 'cache' && (
                <div className="form-item mb-12">
                  <label>缓存路径</label>
                  <input value={getSelectedStep()!.config.path || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, path: e.target.value } })} placeholder=".gradle/caches" />
                </div>
              )}
              {getSelectedStep()!.type === 'gradle' && (
                <div className="form-item mb-12">
                  <label>Task</label>
                  <input value={getSelectedStep()!.config.task || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, task: e.target.value } })} placeholder="assembleRelease" />
                </div>
              )}
              {getSelectedStep()!.type === 'gradle-test' && (
                <div className="form-item mb-12">
                  <label>Task</label>
                  <input value={getSelectedStep()!.config.task || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, task: e.target.value } })} placeholder="test" />
                </div>
              )}
              {getSelectedStep()!.type === 'shell' && (
                <div className="form-item mb-12">
                  <label>脚本</label>
                  <textarea value={getSelectedStep()!.config.script || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, script: e.target.value } })} rows={4} />
                </div>
              )}
              {getSelectedStep()!.type === 'upload-artifact' && (
                <>
                  <div className="form-item mb-12">
                    <label>产物名称</label>
                    <input value={getSelectedStep()!.config.name || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, name: e.target.value } })} placeholder="app-release" />
                  </div>
                  <div className="form-item mb-12">
                    <label>产物路径</label>
                    <input value={getSelectedStep()!.config.path || ''} onChange={e => updateStep(selectedStepId!, { config: { ...getSelectedStep()!.config, path: e.target.value } })} placeholder="app/build/outputs/apk/release/*.apk" />
                  </div>
                </>
              )}
              {/* 单个 Step 的 YAML 预览 */}
              <div className="form-item mb-12">
                <label>YAML 预览</label>
                <pre style={{
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: 8,
                  borderRadius: 4,
                  fontSize: 11,
                  whiteSpace: 'pre-wrap',
                  maxHeight: 150,
                  overflow: 'auto'
                }}>
                  {generateStepYaml(getSelectedStep()!)}
                </pre>
              </div>
            </>
          ) : (
            <div style={{ textAlign: 'center', color: '#999', padding: 40 }}>选择一个 Step</div>
          )}
        </div>
        <div style={{ width: 200, borderLeft: '1px solid #eee', paddingLeft: 16 }}>
          <h4>添加 Step</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
            {(Object.keys(STEP_TEMPLATES) as StepType[]).map(type => (
              <button key={type} className="btn btn-default" style={{ fontSize: 12, textAlign: 'left' }} onClick={() => addStep(type)}>
                + {STEP_TEMPLATES[type].name}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}