import { apiRequest, query } from './client'
import type { DocumentReadResult, RagResponse } from '../types'
export const ragApi = {
  ask: (projectId: string, userQuery: string) => apiRequest<RagResponse>('/api/workspaces/projects/rag/chat', { method: 'POST', body: JSON.stringify({ projectId, query: userQuery }) }),
  source: (projectId: string, filePath: string) => apiRequest<DocumentReadResult>(`/api/workspaces/projects/documents/read?${query({ projectId, filePath })}`, { method: 'POST' }),
}
