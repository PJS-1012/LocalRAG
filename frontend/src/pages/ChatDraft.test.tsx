import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UnifiedChatPage from './UnifiedChatPage'
import AgentPage from './AgentPage'
import RagPage from './RagPage'
import { apiRequest } from '../api/client'
import { agentApi } from '../api/agentApi'
import { ragApi } from '../api/ragApi'

vi.mock('../api/client', async importOriginal => ({
  ...await importOriginal<typeof import('../api/client')>(), apiRequest: vi.fn(),
}))
vi.mock('../api/agentApi', () => ({ agentApi: { ask: vi.fn() } }))
vi.mock('../api/ragApi', () => ({ ragApi: { ask: vi.fn(), source: vi.fn() } }))
beforeEach(() => vi.resetAllMocks())
afterEach(cleanup)
const answer = {
  projectId: 'P', query: '보낸 질문', answer: '응답 완료', status: 'SUCCESS', warnings: [],
  sources: [], sourceCount: 0, contextCharacters: 0, retrievalContextDurationMillis: 0,
  usedSourceIds: [], invalidSourceIds: [], toolsUsed: [], toolCalls: [],
  knowledgeSources: [], knowledgeSourceCount: 0, toolExecutionDurationMillis: 0,
  llmDurationMillis: 1, totalDurationMillis: 1,
}
const reply = { ...answer, result: answer, evidence: [], routes: [], routing: 'EXISTING_AGENT_TOOL_SELECTION' }

describe.each([
  { name: 'Unified', Page: UnifiedChatPage, label: '채팅 질문', button: '전송', ask: apiRequest },
  { name: 'Agent', Page: AgentPage, label: 'Agent 질문', button: 'Agent 실행', ask: agentApi.ask },
  { name: 'RAG', Page: RagPage, label: 'RAG 질문', button: '전송', ask: ragApi.ask },
])('$name input reset', ({ Page, label, button, ask }) => {
  function setup() {
    let resolve!: (value: typeof reply) => void
    let reject!: (reason: Error) => void
    const pending = new Promise<typeof reply>((ok, fail) => { resolve = ok; reject = fail })
    vi.mocked(ask).mockReturnValue(pending)
    render(<Page projectId="P" project={null} overview={null} refreshOverview={vi.fn()} />)
    const input = screen.getByLabelText(label)
    fireEvent.change(input, { target: { value: '보낸 질문' } })
    return { input, resolve, reject }
  }
  it.each(['Enter', 'button'])('clears immediately after %s and keeps the submitted question in the result', async method => {
    const { input, resolve } = setup()
    if (method === 'Enter') fireEvent.keyDown(input, { key: 'Enter' })
    else fireEvent.click(screen.getByRole('button', { name: button }))
    expect(input).toHaveValue('')
    expect(ask).toHaveBeenCalledTimes(1)
    await act(async () => resolve(reply))
    expect(screen.getByText('보낸 질문')).toBeInTheDocument()
    expect(input).toHaveValue('')
  })
  it('restores the question after a request failure', async () => {
    const { input, reject } = setup()
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(input).toHaveValue('')
    await act(async () => reject(new Error('Connection failed')))
    expect(input).toHaveValue('보낸 질문')
    expect(screen.getByRole('button', { name: button })).toBeEnabled()
  })
  it.each(['success', 'failure'])('preserves a new draft when the previous request ends with %s', async outcome => {
    const { input, resolve, reject } = setup()
    fireEvent.keyDown(input, { key: 'Enter' })
    fireEvent.change(input, { target: { value: '다음 질문 작성 중' } })
    await act(async () => {
      if (outcome === 'success') resolve(reply)
      else reject(new Error('Connection failed'))
    })
    expect(input).toHaveValue('다음 질문 작성 중')
    expect(ask).toHaveBeenCalledTimes(1)
  })
})
