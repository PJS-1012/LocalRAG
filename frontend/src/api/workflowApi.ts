import { apiRequest } from './client'
import type { ActivitySummary, ProjectProgress } from '../types'
export const workflowApi = {
  progress: (projectId: string) => apiRequest<ProjectProgress>('/api/workspaces/projects/progress/analyze', { method: 'POST', body: JSON.stringify({ projectId }) }),
  activity: (projectId: string, since: string | null, commitLimit = 5) => apiRequest<ActivitySummary>('/api/workspaces/projects/activity/summary', { method: 'POST', body: JSON.stringify({ projectId, since, commitLimit }) }),
}
