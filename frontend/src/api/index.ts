import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export interface AppInfo {
  id: number
  appName: string
  appKey: string
  repoUrl: string
  branch: string
  buildConfig: string | null
  workflowId: string | null
  createdAt: string
  updatedAt: string
}

export interface BuildRecord {
  id: number
  buildNumber: number
  status: string
  workflowRunId: string | null
  artifactUrl: string | null
  commitSha: string | null
  commitMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  durationSeconds: number | null
  createdAt: string
}

export interface BuildFlow {
  id: number
  appId: number
  flowName: string
  flowConfig: string
  isDefault: boolean
  createdAt: string
}

export interface FlowStep {
  type: string
  name: string
  config: Record<string, unknown>
}

// Workflow 编辑器类型
export type StepType = 'checkout' | 'setup-jdk' | 'setup-android' | 'cache' | 'gradle' | 'gradle-test' | 'shell' | 'upload-artifact'

export interface JobStep {
  id: string
  name: string
  type: StepType
  config: Record<string, string>
}

export interface Job {
  id: string
  name: string
  runsOn: string
  needs: string[]
  steps: JobStep[]
}

export interface WorkflowConfig {
  name: string
  jobs: Job[]
}

export interface CreateAppRequest {
  repoUrl: string
  branch?: string
}

export const appApi = {
  list: () => api.get<AppInfo[]>('/apps').then(r => r.data),
  get: (id: number) => api.get<AppInfo>(`/apps/${id}`).then(r => r.data),
  getByKey: (key: string) => api.get<AppInfo>(`/apps/key/${key}`).then(r => r.data),
  create: (data: CreateAppRequest) => api.post<AppInfo>('/apps', data).then(r => r.data),
  update: (id: number, data: Partial<AppInfo>) => api.put<AppInfo>(`/apps/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/apps/${id}`)
}

export const buildApi = {
  trigger: (appId: number, flowId?: number, buildParams?: Record<string, string>) =>
    api.post<BuildRecord>(`/builds/trigger/${appId}`, buildParams, {
      params: flowId ? { flowId } : undefined
    }).then(r => r.data),
  list: (appId: number, page = 0, size = 20) =>
    api.get<{ content: BuildRecord[]; totalElements: number }>(`/builds/app/${appId}`, {
      params: { page, size }
    }).then(r => r.data),
  get: (id: number) => api.get<BuildRecord>(`/builds/${id}`).then(r => r.data),
  getLogs: (id: number) => api.get<{ logs: string }>(`/builds/${id}/logs`).then(r => r.data)
}

export const flowApi = {
  list: (appId: number) => api.get<BuildFlow[]>(`/flows/app/${appId}`).then(r => r.data),
  get: (id: number) => api.get<BuildFlow>(`/flows/${id}`).then(r => r.data),
  create: (data: { appId: number; flowName: string; steps: FlowStep[]; isDefault?: boolean }) =>
    api.post<BuildFlow>('/flows', data).then(r => r.data),
  update: (id: number, data: Partial<FlowStep>) => api.put<BuildFlow>(`/flows/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/flows/${id}`)
}

export const githubApi = {
  listWorkflows: (appId: number) =>
    api.get<{ id: string; name: string; path: string }[]>(`/github/repos/${appId}/workflows`).then(r => r.data),
  deleteWorkflow: (appId: number, path: string) =>
    api.delete(`/github/repos/${appId}/workflows?path=${encodeURIComponent(path)}`),
  getWorkflowFile: (appId: number, path: string) =>
    api.get<string>(`/github/repos/${appId}/workflows/file?path=${encodeURIComponent(path)}`).then(r => r.data)
}

export const workflowApi = {
  list: (appId: number) =>
    api.get<BuildFlow[]>(`/flows/app/${appId}`).then(r => r.data),
  preview: (appId: number, workflow: WorkflowConfig) =>
    api.post<{ yaml: string }>(`/workflows/preview/${appId}`, workflow).then(r => r.data),
  create: (appId: number, workflow: WorkflowConfig) =>
    api.post<{ workflowId: string }>(`/workflows/create/${appId}`, workflow).then(r => r.data),
  update: (appId: number, path: string, workflow: WorkflowConfig) =>
    api.put<{ success: boolean }>(`/github/repos/${appId}/workflows?path=${encodeURIComponent(path)}`, workflow).then(r => r.data)
}

export default api