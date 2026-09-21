import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RagPage from './RagPage'
import AgentPage from './AgentPage'
import { ragApi } from '../api/ragApi'
import { agentApi } from '../api/agentApi'
import type { ProjectOverview } from '../types'

vi.mock('../api/ragApi', () => ({ ragApi: { ask: vi.fn(), source: vi.fn() } }))
vi.mock('../api/agentApi', () => ({ agentApi: { ask: vi.fn() } }))
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ragApi.ask).mockImplementation(() => new Promise(() => {}))
  vi.mocked(agentApi.ask).mockImplementation(() => new Promise(() => {}))
})
afterEach(cleanup)

describe.each([
  { name: 'RAG', Page: RagPage, label: 'RAG 질문', ask: ragApi.ask },
  { name: 'Agent', Page: AgentPage, label: 'Agent 질문', ask: agentApi.ask },
])('$name composer', ({ Page, label, ask }) => {
  const setup = () => {
    render(<Page projectId="P" project={null} overview={null} refreshOverview={vi.fn()} />)
    return screen.getByLabelText(label)
  }
  it('sends on Enter and blocks repeated/in-flight submissions', async () => {
    const input = setup()
    fireEvent.change(input, { target: { value: '프로젝트 질문' } })
    fireEvent.keyDown(input, { key: 'Enter', repeat: true })
    expect(ask).not.toHaveBeenCalled()
    fireEvent.keyDown(input, { key: 'Enter' })
    await waitFor(() => expect(ask).toHaveBeenCalledWith('P', '프로젝트 질문'))
    fireEvent.keyDown(input, { key: 'Enter' })
    fireEvent.submit(input.closest('form')!)
    expect(ask).toHaveBeenCalledTimes(1)
  })
  it('keeps Shift+Enter as a newline', async () => {
    const input = setup()
    await userEvent.clear(input)
    await userEvent.type(input, 'first{Shift>}{Enter}{/Shift}second')
    expect(input).toHaveValue('first\nsecond')
    expect(ask).not.toHaveBeenCalled()
  })
  it('does not send while Korean IME composition is in progress', () => {
    const input = setup()
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true })
    fireEvent.keyDown(input, { key: 'Enter', keyCode: 229 })
    expect(ask).not.toHaveBeenCalled()
  })
  it('does not send blank input', () => {
    const input = setup()
    fireEvent.change(input, { target: { value: '   ' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(ask).not.toHaveBeenCalled()
  })
})

it('explains how to prepare a project with no stored index without auto-indexing', () => {
  const overview = { index: { storedChunkCount: 0 } } as ProjectOverview
  render(<RagPage projectId="P" project={null} overview={overview} refreshOverview={vi.fn()} />)
  expect(screen.getByText(/Dashboard에서.*Project 인덱싱/)).toBeInTheDocument()
  expect(ragApi.ask).not.toHaveBeenCalled()
})
