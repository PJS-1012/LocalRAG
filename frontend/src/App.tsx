import { useCallback, useEffect, useMemo, useState } from 'react'
import { workspaceApi } from './api/workspaceApi'
import { dashboardApi } from './api/dashboardApi'
import { ApiError } from './api/client'
import type { DetectedProject, ProjectOverview, WorkspaceDiscovery } from './types'
import { StatusBadge } from './components/ui'
import { statusTone } from './lib/format'
import DashboardPage from './pages/DashboardPage'
import RagPage from './pages/RagPage'
import AgentPage from './pages/AgentPage'
import ErrorsPage from './pages/ErrorsPage'
import ProgressPage from './pages/ProgressPage'
import ActivityPage from './pages/ActivityPage'
import AutomationPage from './pages/AutomationPage'
import NotificationsPage from './pages/NotificationsPage'
import SettingsPage from './pages/SettingsPage'

export type RouteKey = 'dashboard' | 'knowledge' | 'agent' | 'errors' | 'progress' | 'activity' | 'automation' | 'notifications' | 'settings'
const NAV: Array<{ id: RouteKey; label: string; icon: string; group?: string }> = [
  { id: 'dashboard', label: 'Dashboard', icon: '⌂' },
  { id: 'knowledge', label: 'Chat / Knowledge', icon: '◈', group: 'WORKSPACE' },
  { id: 'agent', label: 'Agent', icon: '⌘' },
  { id: 'errors', label: 'Errors', icon: '!' },
  { id: 'progress', label: 'Progress', icon: '↗', group: 'INTELLIGENCE' },
  { id: 'activity', label: 'Activity', icon: '≋' },
  { id: 'automation', label: 'Automation', icon: '⟳', group: 'OPERATIONS' },
  { id: 'notifications', label: 'Notifications', icon: '◇' },
  { id: 'settings', label: 'Settings', icon: '⚙' },
]

export default function App() {
  const [route, setRoute] = useState<RouteKey>('dashboard')
  const [discovery, setDiscovery] = useState<WorkspaceDiscovery | null>(null)
  const [selectedId, setSelectedId] = useState(localStorage.getItem('localrag.project') ?? '')
  const [overview, setOverview] = useState<ProjectOverview | null>(null)
  const [backendError, setBackendError] = useState<string | null>(null)
  const [navOpen, setNavOpen] = useState(false)

  useEffect(() => {
    workspaceApi.discovery().then(value => {
      setDiscovery(value); setBackendError(null)
      const valid = value.projects.some(project => project.projectId === selectedId)
      if (!valid && value.projects[0]) setSelectedId(value.projects[0].projectId)
    }).catch((error: ApiError) => setBackendError(error.message))
  }, []) // project choice is reconciled once after discovery

  const refreshOverview = useCallback(() => {
    if (!selectedId) { setOverview(null); return Promise.resolve() }
    return dashboardApi.overview(selectedId).then(setOverview).catch(() => setOverview(null))
  }, [selectedId])

  useEffect(() => { localStorage.setItem('localrag.project', selectedId); void refreshOverview() }, [selectedId, refreshOverview])

  const selectedProject = useMemo<DetectedProject | null>(() => discovery?.projects.find(project => project.projectId === selectedId) ?? null, [discovery, selectedId])
  const pageProps = { projectId: selectedId, project: selectedProject, overview, refreshOverview }
  const currentPage = {
    dashboard: <DashboardPage {...pageProps} />,
    knowledge: <RagPage {...pageProps} />,
    agent: <AgentPage {...pageProps} />,
    errors: <ErrorsPage {...pageProps} />,
    progress: <ProgressPage {...pageProps} />,
    activity: <ActivityPage {...pageProps} />,
    automation: <AutomationPage {...pageProps} />,
    notifications: <NotificationsPage {...pageProps} />,
    settings: <SettingsPage {...pageProps} discovery={discovery} backendError={backendError} />,
  }[route]

  return <div className="app-shell">
    <aside className={`sidebar ${navOpen ? 'sidebar-open' : ''}`}>
      <div className="brand"><div className="brand-mark">LR</div><div><strong>LocalRAG</strong><span>WORKBENCH</span></div></div>
      <nav>{NAV.map(item => <div key={item.id}>{item.group && <div className="nav-group">{item.group}</div>}<button className={route === item.id ? 'nav-active' : ''} onClick={() => { setRoute(item.id); setNavOpen(false) }}><span className="nav-icon">{item.icon}</span>{item.label}{item.id === 'notifications' && overview && overview.unacknowledgedNotificationCount > 0 && <em>{overview.unacknowledgedNotificationCount}</em>}</button></div>)}</nav>
      <div className="sidebar-foot"><span className={`connection-light ${backendError ? 'offline' : ''}`} /><div><strong>{backendError ? 'Backend offline' : 'Local mode'}</strong><small>Java 17 · qwen3</small></div></div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <button className="mobile-menu" onClick={() => setNavOpen(value => !value)}>☰</button>
        <div className="workspace-label"><span>WORKSPACE</span><strong>{discovery?.workspaceRoot?.split(/[\\/]/).filter(Boolean).at(-1) ?? '연결 대기'}</strong></div>
        <div className="project-picker"><label htmlFor="project-select">PROJECT</label><select id="project-select" aria-label="Project 선택" value={selectedId} onChange={event => setSelectedId(event.target.value)}><option value="">Project 없음</option>{discovery?.projects.map(project => <option key={project.projectId} value={project.projectId}>{project.name} · {project.projectType}</option>)}</select></div>
        <div className="top-statuses">
          <StatusBadge label={`Docker ${overview?.docker?.status ?? '—'}`} tone={statusTone(overview?.docker?.status)} />
          <StatusBadge label={`DB ${overview?.database?.status ?? '—'}`} tone={statusTone(overview?.database?.status)} />
          <StatusBadge label={`Ollama ${overview?.ollama?.status ?? '—'}`} tone={statusTone(overview?.ollama?.status)} />
        </div>
      </header>
      {backendError && <div className="backend-banner"><strong>LocalRAG Backend에 연결할 수 없습니다.</strong><span>launcher 또는 Backend 실행 상태를 확인해주세요.</span><button onClick={() => location.reload()}>다시 연결</button></div>}
      <main className="main-content">{currentPage}</main>
    </div>
  </div>
}
