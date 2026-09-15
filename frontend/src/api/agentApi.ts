import { apiRequest } from './client'
import type { AgentResponse } from '../types'
export const agentApi = { ask: (projectId: string, query: string) => apiRequest<AgentResponse>('/api/workspaces/projects/agent/chat', { method: 'POST', body: JSON.stringify({ projectId, query }) }) }
