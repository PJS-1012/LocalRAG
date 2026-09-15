import { apiRequest, query } from './client'
import type { ProjectIndexResult, ProjectIndexStats } from '../types'
export const indexApi = {
  stats: (projectId: string) => apiRequest<ProjectIndexStats>(`/api/workspaces/projects/index/stats?${query({ projectId })}`),
  index: (projectId: string) => apiRequest<ProjectIndexResult>(`/api/workspaces/projects/index?${query({ projectId })}`, { method: 'POST' }),
}
