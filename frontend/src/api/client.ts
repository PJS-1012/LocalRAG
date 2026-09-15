export class ApiError extends Error {
  constructor(public readonly status: number | null, message: string, public readonly detail?: string) {
    super(message)
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
    })
  } catch {
    throw new ApiError(null, 'LocalRAG Backend에 연결할 수 없습니다.', 'Backend 실행 상태를 확인해주세요.')
  }
  const text = await response.text()
  if (!response.ok) {
    const conflict = response.status === 409 ? '다른 변경이 먼저 저장되었습니다. 최신 내용을 다시 불러와주세요.' : null
    throw new ApiError(response.status, conflict ?? `요청을 처리하지 못했습니다. (${response.status})`, text || undefined)
  }
  return (text ? JSON.parse(text) : undefined) as T
}

export const query = (values: Record<string, string | number | null | undefined>) => {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') params.set(key, String(value)) })
  return params.toString()
}
