export type StatusTone = 'ok' | 'warn' | 'bad' | 'muted' | 'info'

export interface DetectedProject {
  name: string
  projectId: string
  rootPath: string
  projectType: string
  detectedFramework: string | null
  gitRepository: boolean
  enabled: boolean
  detectionHints: string[]
}

export interface WorkspaceDiscovery {
  workspaceRoot: string
  projects: DetectedProject[]
  containers: Array<{ name: string; rootPath: string }>
}

export interface ProjectIndexStats {
  projectId: string
  storedChunkCount: number
  embeddingModel: string | null
  dimensions: number
  latestIndexedAt: string | null
}

export interface GitStatus {
  projectId: string
  status: string
  branch: string | null
  clean: boolean
  modified: string[]
  added: string[]
  deleted: string[]
  untracked: string[]
  reason: string | null
}

export interface RecentCommit { hash: string; message: string; author: string; timestamp: string }
export interface DashboardGit {
  status: string; branch: string | null; clean: boolean | null; upstream: string | null
  ahead: number | null; behind: number | null; remoteStatus: string; reason: string | null
  commits: Array<RecentCommit & { pushStatus: string }>
}
export interface ProjectSummary {
  project: DetectedProject; git: DashboardGit | null; indexedDocumentCount: number | null
  indexedChunkCount: number | null; automationStatus: string; errorHistoryCount: number | null
  notificationCount: number | null; warnings: string[]
}
export interface WorkspaceOverview { projects: ProjectSummary[]; collectedAt: string; durationMillis: number; remoteBasis: string }
export interface GitRecentCommits { status: string; commits: RecentCommit[]; reason: string | null }
export interface EnvironmentStatus { status: string; reason: string | null; [key: string]: unknown }

export interface ProjectOverview {
  project: DetectedProject
  index: ProjectIndexStats | null
  git: GitStatus | null
  recentCommits: GitRecentCommits | null
  docker: EnvironmentStatus | null
  projectContainers: EnvironmentStatus | null
  ollama: EnvironmentStatus | null
  database: EnvironmentStatus | null
  errors: { total: number; unverified: number; verified: number; resolved: number } | null
  latestAutomation: AutomationRun | null
  notificationCandidateCount: number
  unacknowledgedNotificationCount: number
  warnings: string[]
  collectedAt: string
  durationMillis: number
}

export interface RagSource { id: string; filePath: string; fileName: string; startLine: number; endLine: number }
export interface RagResponse {
  projectId: string; query: string; answer: string; sourceCount: number; sources: RagSource[]
  contextCharacters: number; retrievalContextDurationMillis: number; llmDurationMillis: number
  totalDurationMillis: number; status: string; usedSourceIds: string[]; invalidSourceIds: string[]; warnings: string[]
}

export interface AgentToolCall {
  sequence: number; toolName: string; durationMillis: number; outcome: string; successful: boolean
  sameArgumentsAs: number | null
}
export interface AgentResponse {
  projectId: string; query: string; answer: string; toolsUsed: string[]; toolExecutionDurationMillis: number
  llmDurationMillis: number; totalDurationMillis: number; status: string; warnings: string[]
  toolCalls: AgentToolCall[]; knowledgeSourceCount: number
  knowledgeSources: Array<{ id: string; filePath: string; startLine: number; endLine: number }>
}

export type ErrorStatus = 'UNVERIFIED' | 'VERIFIED' | 'RESOLVED'
export interface ErrorHistory {
  id: number; analysisId: string; projectId: string; occurredAt: string | null; recordedAt: string
  errorType: string | null; errorMessage: string; symptom: string | null; rootCause: string | null
  solution: string | null; status: ErrorStatus; relatedFiles: string[]; relatedCommits: string[]
  evidenceSummary: unknown; verificationNote: string | null; statusChangedAt: string | null; version: number
}
export interface VerificationAudit {
  id: number; fromStatus: ErrorStatus; toStatus: ErrorStatus; rootCause: string | null; solution: string | null
  verificationNote: string; actor: string; changedAt: string; previousVersion: number
}
export interface ErrorHistoryDetail { history: ErrorHistory; verifications: VerificationAudit[]; queryDurationMillis: number }
export interface ErrorHistoryPage { content: ErrorHistory[]; totalElements: number; totalPages: number; page: number; size: number; queryDurationMillis: number }
export interface ErrorAnalysis {
  analysisId: string; projectId: string; analyzedAt: string; expiresAt: string; occurredAt: string | null
  errorType: string | null; errorMessage: string; symptom: string | null; analysis: string
  rootCause: string | null; solution: string | null; status: ErrorStatus; evidenceStatus: string
  confirmedEvidence: unknown[]; unknown: string[]; relatedFiles: string[]; relatedCommits: string[]
  llmDurationMillis: number; totalDurationMillis: number
}
export interface SimilarError {
  errorHistoryId: number; status: ErrorStatus; trustLabel: string; similarity: number
  errorType: string | null; errorMessage: string; rootCause: string | null; solution: string | null
  occurredAt: string | null; relatedFiles: string[]; relatedCommits: string[]
}
export interface SimilarErrorResponse { status: string; threshold: number; resultCount: number; caution: string; results: SimilarError[]; totalDurationMillis: number }

export interface ProjectProgress {
  status: string; projectId: string; summary: string; completed: string[]; inProgress: string[]; planned: string[]
  blocked: string[]; documentationMismatch: string[]; unknown: string[]; unresolvedErrors: number
  evidence: unknown[]; toolsUsed: string[]; generatedAt: string; evidenceCollectionDurationMillis: number
  ragDurationMillis: number; llmDurationMillis: number; totalDurationMillis: number
}
export interface ActivitySummary {
  status: string; projectId: string; since: string | null; commitLimit: number; commits: RecentCommit[]
  changedAreas: string[]; summary: string; currentWorkingTree: GitStatus | null; relatedDecisionLogs: unknown[]
  generatedAt: string; gitEvidenceDurationMillis: number; ragDurationMillis: number
  llmDurationMillis: number; totalDurationMillis: number
}

export interface AutomationConfig {
  projectId: string; enabled: boolean; progressSummaryEnabled: boolean; activitySummaryEnabled: boolean
  errorWatchEnabled: boolean; environmentWatchEnabled: boolean; intervalSeconds: number
  lastRunAt: string | null; nextRunAt: string | null; createdAt: string; updatedAt: string; version: number
}
export interface AutomationRun {
  id: number; projectId: string; triggerType: string; startedAt: string; finishedAt: string
  status: string; changeDetected: boolean; summary: string | null; errorMessage: string | null
  resultDetails: unknown; durationMillis: number
}
export interface NotificationCandidate {
  id: number; projectId: string; automationRunId: number | null; notificationType: string
  title: string; summary: string; createdAt: string; acknowledgedAt: string | null
}
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; page: number; size: number }
export interface AutomationExecution {
  status: string; projectId: string; triggerType: string; changeDetected: boolean; llmInvoked: boolean
  reason: string | null; run: AutomationRun | null; notifications: NotificationCandidate[]; totalDurationMillis: number
}
export interface DocumentReadResult {
  status: string
  document: { fileName: string; filePath: string; relativePath: string; content: string; modifiedAt: string } | null
  reason: string | null
}
export interface ProjectIndexResult { status: string; storedCount: number; writtenCount: number; deletedCount: number; failedCount: number; totalDurationMillis: number; reason: string | null }
