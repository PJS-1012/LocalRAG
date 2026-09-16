import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, resolveApiUrl, waitForBackend } from './client'

describe('apiRequest', () => {
  afterEach(() => vi.restoreAllMocks())
  it('distinguishes backend offline from an application error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    await expect(apiRequest('/api/health')).rejects.toMatchObject({ status: null, message: 'LocalRAG Backend에 연결할 수 없습니다.' })
  })
  it('turns optimistic locking conflict into an actionable message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Concurrent update', { status: 409 })))
    await expect(apiRequest('/api/history')).rejects.toMatchObject({ status: 409, message: expect.stringContaining('최신 내용') })
  })
  it('uses the fixed loopback backend only for the Tauri production host', () => {
    expect(resolveApiUrl('/api/health', 'tauri.localhost')).toBe('http://127.0.0.1:18080/api/health')
    expect(resolveApiUrl('/api/health', 'localhost')).toBe('/api/health')
  })
  it('bounds backend readiness retries and reports unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(waitForBackend(2, 0)).resolves.toMatchObject({ state: 'UNAVAILABLE' })
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})
