import { apiRequest, query } from './client'
import type { ErrorAnalysis, ErrorHistory, ErrorHistoryDetail, ErrorHistoryPage, ErrorStatus, SimilarErrorResponse } from '../types'

export interface ErrorFilters { status?: ErrorStatus | ''; errorType?: string; errorMessage?: string; occurredFrom?: string; occurredTo?: string; relatedFile?: string; relatedCommit?: string; page?: number }

export const errorApi = {
  list: (projectId: string, filters: ErrorFilters = {}) => apiRequest<ErrorHistoryPage>(`/api/workspaces/projects/errors/history?${query({ projectId, ...filters, size: 20 })}`),
  detail: (projectId: string, id: number) => apiRequest<ErrorHistoryDetail>(`/api/workspaces/projects/errors/history/${id}?${query({ projectId })}`),
  analyze: (projectId: string, queryText: string) => apiRequest<ErrorAnalysis>('/api/workspaces/projects/errors/analyze', { method: 'POST', body: JSON.stringify({ projectId, query: queryText }) }),
  save: (projectId: string, analysisId: string) => apiRequest<{ history: ErrorHistory }>('/api/workspaces/projects/errors/history', { method: 'POST', body: JSON.stringify({ projectId, analysisId }) }),
  updateStatus: (projectId: string, history: ErrorHistory, status: ErrorStatus, verificationNote: string, rootCause: string | null, solution: string | null) => apiRequest<ErrorHistory>(`/api/workspaces/projects/errors/history/${history.id}/status`, { method: 'PATCH', body: JSON.stringify({ projectId, status, verificationNote, rootCause, solution, expectedVersion: history.version }) }),
  similar: (projectId: string, errorMessage: string, errorType?: string | null, symptom?: string | null) => apiRequest<SimilarErrorResponse>('/api/workspaces/projects/errors/similar', { method: 'POST', body: JSON.stringify({ projectId, errorType, errorMessage, symptom, topK: 5 }) }),
}
