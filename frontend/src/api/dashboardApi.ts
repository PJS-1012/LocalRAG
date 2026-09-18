import { apiRequest, query } from './client'
import type { ProjectOverview, WorkspaceOverview } from '../types'
export const dashboardApi = {
  summary: () => apiRequest<WorkspaceOverview>('/api/workspaces/overview'),
  overview: (projectId: string) => apiRequest<ProjectOverview>(`/api/workspaces/projects/overview?${query({ projectId })}`)
}
