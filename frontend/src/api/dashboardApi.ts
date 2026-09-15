import { apiRequest, query } from './client'
import type { ProjectOverview } from '../types'
export const dashboardApi = { overview: (projectId: string) => apiRequest<ProjectOverview>(`/api/workspaces/projects/overview?${query({ projectId })}`) }
