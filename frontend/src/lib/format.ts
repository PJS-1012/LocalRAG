export const formatDate = (value?: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
export const formatDuration = (ms?: number | null) => ms == null ? '—' : ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
export const shortHash = (hash?: string | null) => hash ? hash.slice(0, 8) : '—'
export const relativeTime = (value?: string | null) => {
  if (!value) return '기록 없음'
  const diff = Date.now() - new Date(value).getTime()
  if (diff < 60_000) return '방금 전'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}분 전`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}시간 전`
  return `${Math.floor(diff / 86_400_000)}일 전`
}
export const statusTone = (status?: string | null) => {
  const normalized = status?.toUpperCase() ?? ''
  if (['UP', 'SUCCESS', 'AVAILABLE', 'RESOLVED', 'READ_SUCCESS', 'INDEXED'].includes(normalized)) return 'ok' as const
  if (['FAILED', 'DOWN', 'NOT_RUNNING', 'UNAVAILABLE'].includes(normalized)) return 'bad' as const
  if (['PARTIAL_SUCCESS', 'UNVERIFIED', 'ALREADY_RUNNING','SUCCESS_WITH_WARNINGS','INSUFFICIENT_EVIDENCE','NO_EVIDENCE'].includes(normalized)) return 'warn' as const
  if (['VERIFIED', 'CHANGED', 'RUNNING'].includes(normalized)) return 'info' as const
  return 'muted' as const
}
