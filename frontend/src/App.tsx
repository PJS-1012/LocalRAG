import { useCallback, useEffect, useMemo, useState } from 'react'
import { workspaceApi } from './api/workspaceApi'
import { dashboardApi } from './api/dashboardApi'
import { ApiError, isTauriRuntime, retryStartup, waitForBackend, type BackendReadiness, type DesktopBackendStatus } from './api/client'
import type { DetectedProject, ProjectOverview, WorkspaceDiscovery, WorkspaceOverview } from './types'
import StartupScreen from './components/StartupScreen'
import ProjectList from './components/ProjectList'
import { StatusBadge } from './components/ui'
import { statusTone } from './lib/format'
import DashboardPage from './pages/DashboardPage'
import RagPage from './pages/RagPage'
import UnifiedChatPage from './pages/UnifiedChatPage'
import AgentPage from './pages/AgentPage'
import ErrorsPage from './pages/ErrorsPage'
import ProgressPage from './pages/ProgressPage'
import ActivityPage from './pages/ActivityPage'
import AutomationPage from './pages/AutomationPage'
import NotificationsPage from './pages/NotificationsPage'
import SettingsPage from './pages/SettingsPage'

export type RouteKey = 'dashboard' | 'chat' | 'knowledge' | 'agent' | 'errors' | 'progress' | 'activity' | 'automation' | 'notifications' | 'settings'
const NAV: Array<{ id: RouteKey; label: string; icon: string; group?: string }> = [
  { id: 'dashboard', label: '대시보드', icon: '⌂' },
  { id: 'chat', label: '채팅', icon: '◈', group: '작업공간' },
  { id: 'errors', label: '오류 관리', icon: '!' },
  { id: 'progress', label: '진행 상태', icon: '↗', group: '작업 현황' },
  { id: 'activity', label: '최근 작업', icon: '≋' },
  { id: 'automation', label: '자동화', icon: '⟳', group: '관리' },
  { id: 'notifications', label: '알림', icon: '◇' },
  { id: 'settings', label: '설정', icon: '⚙' },
  { id: 'agent', label: '에이전트 상세', icon: '⌘', group: '고급 기능' },
  { id: 'knowledge', label: '지식 검색 상세', icon: '◈' },
]

export default function App() {
  const [route, setRoute] = useState<RouteKey>('dashboard')
  const [discovery, setDiscovery] = useState<WorkspaceDiscovery | null>(null)
  const [selectedId, setSelectedId] = useState(localStorage.getItem('localrag.project') ?? '')
  const [overview, setOverview] = useState<ProjectOverview | null>(null)
  const [backendError, setBackendError] = useState<string | null>(null)
  const [backendStatus, setBackendStatus] = useState<BackendReadiness>('STARTING')
  const [backendDetail, setBackendDetail] = useState('Backend readiness 확인 중')
  const [navOpen, setNavOpen] = useState(false)
  const [startup, setStartup] = useState<DesktopBackendStatus>({ state: 'STARTING', detail: 'Runtime 준비 중', managed: false, pid: null })
  const [summary, setSummary] = useState<WorkspaceOverview | null>(null)
  const [summaryError, setSummaryError] = useState<string | null>(null)
  const refreshSummary = useCallback(async () => {
    setSummaryError(null)
    try {
      const value = await dashboardApi.summary()
      if (!Array.isArray(value.projects)) throw new Error('Invalid summary response')
      setSummary(value)
    } catch { setSummaryError('프로젝트 정보를 불러오지 못했습니다. 메타데이터 새로고침으로 다시 시도하세요.') }
  }, [])

  const connectBackend = useCallback(async () => {
    setBackendStatus('STARTING'); setBackendDetail('Backend readiness 확인 중'); setBackendError(null)
    setStartup({ state: 'STARTING', detail: 'Runtime 준비 중', managed: false, pid: null })
    const readiness = await waitForBackend(isTauriRuntime() ? 510 : 45, 1000, (status: DesktopBackendStatus) => {
      setStartup(status)
      setBackendStatus(status.state); setBackendDetail(status.detail)
    })
    setStartup(readiness)
    if (readiness.state !== 'READY') {
      setBackendStatus(readiness.state); setBackendDetail(readiness.detail)
      setBackendError(readiness.state === 'PORT_IN_USE' ? 'Port 18080을 다른 프로세스가 사용 중입니다.' : 'LocalRAG Backend에 연결할 수 없습니다.')
      return
    }
    setBackendStatus('READY'); setBackendError(null)
    try {
      const value = await workspaceApi.discovery()
      setDiscovery(value)
      const valid = value.projects.some(project => project.projectId === selectedId)
      if (!valid && value.projects[0]) setSelectedId(value.projects[0].projectId)
    } catch (error) {
      setBackendStatus('UNAVAILABLE')
      setBackendError(error instanceof ApiError ? error.message : 'LocalRAG Backend에 연결할 수 없습니다.')
    }
  }, [selectedId])

  useEffect(() => { void connectBackend() }, []) // project choice is reconciled once after discovery
  useEffect(() => { if (backendStatus === 'READY') void refreshSummary() }, [backendStatus, refreshSummary])
  const retryRuntime = async () => {
    try { await retryStartup(); await connectBackend() }
    catch { setStartup(value => ({ ...value, state: 'UNAVAILABLE', detail: 'Startup 재시도 요청 실패' })) }
  }

  const refreshOverview = useCallback(() => {
    if (!selectedId) { setOverview(null); return Promise.resolve() }
    return dashboardApi.overview(selectedId).then(setOverview).catch(() => setOverview(null))
  }, [selectedId])

  useEffect(() => { localStorage.setItem('localrag.project', selectedId); if (backendStatus === 'READY') void refreshOverview() }, [selectedId, refreshOverview, backendStatus])

  const selectedProject = useMemo<DetectedProject | null>(() => discovery?.projects.find(project => project.projectId === selectedId) ?? null, [discovery, selectedId])
  const pageProps = { projectId: selectedId, project: selectedProject, overview, refreshOverview }
  const currentPage = {
    chat: <UnifiedChatPage key={selectedId} {...pageProps}/>,
    dashboard: <><ProjectList summary={summary} error={summaryError} retry={() => void refreshSummary()} /><DashboardPage {...pageProps} gitSummary={summary?.projects.find(p => p.project.projectId === selectedId)?.git ?? null} /></>,
    knowledge: <RagPage {...pageProps} />,
    agent: <AgentPage {...pageProps} />,
    errors: <ErrorsPage {...pageProps} />,
    progress: <ProgressPage {...pageProps} />,
    activity: <ActivityPage {...pageProps} />,
    automation: <AutomationPage {...pageProps} />,
    notifications: <NotificationsPage {...pageProps} />,
    settings: <SettingsPage {...pageProps} discovery={discovery} backendError={backendError} backendStatus={backendStatus} retryStartup={() => void retryRuntime()} />,
  }[route]

  if (isTauriRuntime() && backendStatus !== 'READY') return <StartupScreen status={startup} retry={() => void retryRuntime()} />
  return <div className="app-shell">
    <aside className={`sidebar ${navOpen ? 'sidebar-open' : ''}`}>
      <div className="brand"><div className="brand-mark">LR</div><div><strong>LocalRAG</strong><span>WORKBENCH</span></div></div>
      <nav>{NAV.map(item => <div key={item.id}>{item.group && <div className="nav-group">{item.group}</div>}<button className={route === item.id ? 'nav-active' : ''} onClick={() => { setRoute(item.id); setNavOpen(false) }}><span className="nav-icon">{item.icon}</span>{item.label}{item.id === 'notifications' && overview && overview.unacknowledgedNotificationCount > 0 && <em>{overview.unacknowledgedNotificationCount}</em>}</button></div>)}</nav>
      <div className="sidebar-foot"><span className={`connection-light ${backendStatus === 'READY' ? '' : 'offline'}`} /><div><strong>{backendStatus === 'STARTING' ? 'Backend starting' : backendError ? 'Backend offline' : '로컬 모드'}</strong><small>Java 17 · qwen3</small></div></div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <button className="mobile-menu" onClick={() => setNavOpen(value => !value)}>☰</button>
        <div className="workspace-label"><span>작업공간</span><strong>{discovery?.workspaceRoot?.split(/[\\/]/).filter(Boolean).at(-1) ?? '연결 대기'}</strong></div>
        <div className="project-picker"><label htmlFor="project-select">프로젝트</label><select id="project-select" aria-label="프로젝트 선택" value={selectedId} onChange={event => setSelectedId(event.target.value)}><option value="">Project 없음</option>{discovery?.projects.map(project => <option key={project.projectId} value={project.projectId}>{project.name} · {project.projectType}</option>)}</select></div>
        <div className="top-statuses">
          <StatusBadge label={`Docker ${overview?.docker?.status ?? '—'}`} tone={statusTone(overview?.docker?.status)} />
          <StatusBadge label={`DB ${overview?.database?.status ?? '—'}`} tone={statusTone(overview?.database?.status)} />
          <StatusBadge label={`Ollama ${overview?.ollama?.status ?? '—'}`} tone={statusTone(overview?.ollama?.status)} />
        </div>
      </header>
      {backendStatus === 'STARTING' && <div className="backend-banner starting"><strong>Backend 시작 중</strong><span>{backendDetail}</span></div>}
      {backendError && <div className="backend-banner"><strong>{backendError}</strong><span>{backendDetail}</span><button onClick={() => void connectBackend()}>다시 연결</button></div>}
      <main className="main-content">{currentPage}</main>
    </div>
  </div>
}
