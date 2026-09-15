import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client'

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
})
