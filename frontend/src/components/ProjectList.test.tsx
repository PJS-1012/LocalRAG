import { fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ProjectList, { remoteLabel,workLabel,languageLabel } from './ProjectList'
import StartupScreen from './StartupScreen'
import type { WorkspaceOverview } from '../types'
describe('Desktop metadata and startup', () => {
  it('separates working changes, remote lag, and file-count languages',()=>{
    const git={remoteStatus:'TRACKING',ahead:3,behind:2,clean:false,changes:{modified:3,added:1,deleted:0}} as WorkspaceOverview['projects'][number]['git']
    expect(remoteLabel(git)).toBe('미Push 커밋 3개 · 원격보다 2개 뒤처짐')
    expect(remoteLabel({...git!,behind:0})).not.toContain('원격과 동일')
    expect(workLabel(git)).toBe('수정 3 · 신규 1 · 삭제 0')
    expect(workLabel({...git!,status:'NOT_GIT_REPOSITORY'})).toBe('Git 저장소 아님')
    expect(languageLabel({languages:{languages:[{name:'Java',percent:80},{name:'YAML',percent:20}]}} as WorkspaceOverview['projects'][number])).toBe('Java 80% · YAML 20%')
  })
  it('opens cheap detail locally and distinguishes clean from unpushed', () => {
    const summary = { projects: [{ project: { name: 'P', projectId: 'A/P', rootPath: 'C:/workspace/A/P', projectType: 'JAVA' },
      git: { branch: 'main', clean: true, upstream: 'company/main', remoteStatus: 'TRACKING', ahead: 3, behind: 0,
        commits: [{ hash: 'a'.repeat(40), message: 'Local change', author: 'Author', timestamp: '2026-09-17T00:00:00Z', pushStatus: 'UNPUSHED' }] },
      indexedDocumentCount: 2, indexedChunkCount: 4, automationStatus: 'DISABLED', errorHistoryCount: 1, notificationCount: 2, warnings: [] }] } as unknown as WorkspaceOverview
    const retry = vi.fn()
    render(<ProjectList summary={summary} error={null} retry={retry} />)
    fireEvent.click(screen.getByRole('button', { name: /P A\/P/ }))
    expect(screen.getByLabelText('프로젝트 상세')).toHaveTextContent('변경 없음')
    expect(screen.getByText('미Push')).toBeInTheDocument()
    expect(screen.getByText('C:/workspace/A/P')).toBeInTheDocument()
    expect(retry).not.toHaveBeenCalled()
  })
  it('shows missing model and offers a real retry action', () => {
    const retry = vi.fn()
    render(<StartupScreen status={{ state: 'UNAVAILABLE', detail: 'qwen3:8b', managed: false, pid: null,
      stages: [{ name: 'Models', state: 'MODEL_MISSING', detail: 'qwen3:8b' }] }} retry={retry} />)
    expect(screen.getByText('모델 없음')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 준비' }))
    expect(retry).toHaveBeenCalledOnce()
  })
})
