export class ApiError extends Error {
  constructor(public readonly status: number | null, message: string, public readonly detail?: string) {
    super(message)
  }
}

export type BackendReadiness = 'STARTING' | 'READY' | 'UNAVAILABLE' | 'PORT_IN_USE'
export interface DesktopBackendStatus {
  state: BackendReadiness
  detail: string
  managed: boolean
  pid: number | null
}

interface DesktopApiResponse { status: number; body: string }

const isTauriHost = (hostname: string) => hostname === 'tauri.localhost'
export const resolveApiUrl = (path: string, hostname = window.location.hostname) =>
  `${isTauriHost(hostname) ? 'http://127.0.0.1:18080' : ''}${path}`

export const isTauriRuntime = () => isTauriHost(window.location.hostname) || '__TAURI_INTERNALS__' in window

export async function desktopBackendStatus(): Promise<DesktopBackendStatus | null> {
  if (!isTauriRuntime()) return null
  const { invoke } = await import('@tauri-apps/api/core')
  return invoke<DesktopBackendStatus>('desktop_backend_status')
}

const delay = (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds))

export async function waitForBackend(
  attempts = 45,
  intervalMilliseconds = 1000,
  onStatus?: (status: DesktopBackendStatus) => void,
): Promise<DesktopBackendStatus> {
  let last: DesktopBackendStatus = { state: 'STARTING', detail: 'Backend readiness 확인 중', managed: false, pid: null }
  onStatus?.(last)
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const desktop = await desktopBackendStatus()
      if (desktop) {
        last = desktop
        onStatus?.(desktop)
        if (desktop.state === 'READY') return desktop
        if (desktop.state === 'PORT_IN_USE') return desktop
      } else {
        const response = await fetch(resolveApiUrl('/api/health'), { headers: { Accept: 'application/json' } })
        if (response.ok) {
          const body = await response.json() as { status?: string }
          if (body.status === 'UP') return { ...last, state: 'READY', detail: 'LocalRAG Backend ready' }
        }
      }
    } catch {
      // Backend can be unavailable while the bundled jar starts; retry is bounded below.
    }
    if (attempt + 1 < attempts) await delay(intervalMilliseconds)
  }
  return { ...last, state: 'UNAVAILABLE', detail: last.detail || 'Backend readiness timeout' }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  let status: number
  let text: string
  try {
    if (isTauriRuntime()) {
      const { invoke } = await import('@tauri-apps/api/core')
      const response = await invoke<DesktopApiResponse>('desktop_api_request', { request: {
        path,
        method: init?.method ?? 'GET',
        body: typeof init?.body === 'string' ? init.body : null,
      } })
      status = response.status
      text = response.body
    } else {
      const response = await fetch(resolveApiUrl(path), {
        ...init,
        headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
      })
      status = response.status
      text = await response.text()
    }
  } catch {
    throw new ApiError(null, 'LocalRAG Backend에 연결할 수 없습니다.', 'Backend 실행 상태를 확인해주세요.')
  }
  if (status < 200 || status >= 300) {
    const conflict = status === 409 ? '다른 변경이 먼저 저장되었습니다. 최신 내용을 다시 불러와주세요.' : null
    throw new ApiError(status, conflict ?? `요청을 처리하지 못했습니다. (${status})`, text || undefined)
  }
  return (text ? JSON.parse(text) : undefined) as T
}

export const query = (values: Record<string, string | number | null | undefined>) => {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') params.set(key, String(value)) })
  return params.toString()
}
