import { apiRequest, query } from './client'
import type { AutomationConfig, AutomationExecution, AutomationRun, NotificationCandidate, Page } from '../types'
export const automationApi = {
  get: (projectId: string) => apiRequest<AutomationConfig>(`/api/workspaces/projects/automation?${query({ projectId })}`),
  save: (config: Omit<AutomationConfig, 'lastRunAt' | 'nextRunAt' | 'createdAt' | 'updatedAt' | 'version'>) => apiRequest<AutomationConfig>('/api/workspaces/projects/automation', { method: 'PUT', body: JSON.stringify(config) }),
  run: (projectId: string) => apiRequest<AutomationExecution>('/api/workspaces/projects/automation/run', { method: 'POST', body: JSON.stringify({ projectId }) }),
  runs: (projectId: string, page = 0) => apiRequest<Page<AutomationRun>>(`/api/workspaces/projects/automation/runs?${query({ projectId, page, size: 20 })}`),
  notifications: (projectId: string, page = 0) => apiRequest<Page<NotificationCandidate>>(`/api/workspaces/projects/automation/notifications?${query({ projectId, page, size: 20 })}`),
}
