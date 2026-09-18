import { fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ProjectList from './ProjectList'
import StartupScreen from './StartupScreen'
import type { WorkspaceOverview } from '../types'
describe('Desktop metadata and startup', () => {
  it('opens cheap detail locally and distinguishes clean from unpushed', () => {
    const summary = { projects: [{ project: { name: 'P', projectId: 'A/P', rootPath: 'C:/workspace/A/P', projectType: 'JAVA' },
      git: { branch: 'main', clean: true, upstream: 'company/main', remoteStatus: 'TRACKING', ahead: 3, behind: 0,
        commits: [{ hash: 'a'.repeat(40), message: 'Local change', author: 'Author', timestamp: '2026-09-17T00:00:00Z', pushStatus: 'UNPUSHED' }] },
      indexedDocumentCount: 2, indexedChunkCount: 4, automationStatus: 'DISABLED', errorHistoryCount: 1, notificationCount: 2, warnings: [] }] } as unknown as WorkspaceOverview
    const retry = vi.fn()
    render(<ProjectList summary={summary} error={null} retry={retry} />)
    fireEvent.click(screen.getByRole('button', { name: /P A\/P/ }))
    expect(screen.getByLabelText('Project detail')).toHaveTextContent('CLEAN')
    expect(screen.getByText('UNPUSHED')).toBeInTheDocument()
    expect(screen.getByText('C:/workspace/A/P')).toBeInTheDocument()
    expect(retry).not.toHaveBeenCalled()
  })
  it('shows missing model and offers a real retry action', () => {
    const retry = vi.fn()
    render(<StartupScreen status={{ state: 'UNAVAILABLE', detail: 'qwen3:8b', managed: false, pid: null,
      stages: [{ name: 'Models', state: 'MODEL_MISSING', detail: 'qwen3:8b' }] }} retry={retry} />)
    expect(screen.getByText('MODEL_MISSING')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry Startup' }))
    expect(retry).toHaveBeenCalledOnce()
  })
})
