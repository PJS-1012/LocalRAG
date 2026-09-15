import { apiRequest } from './client'
import type { DetectedProject, WorkspaceDiscovery } from '../types'
export const workspaceApi = {
  projects: () => apiRequest<DetectedProject[]>('/api/workspaces/projects'),
  discovery: () => apiRequest<WorkspaceDiscovery>('/api/workspaces/discovery'),
}
