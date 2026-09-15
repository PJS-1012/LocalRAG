import { useState } from 'react'
import { indexApi } from '../api/indexApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, Metric, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDate, formatDuration, relativeTime, shortHash, statusTone } from '../lib/format'
import type { ProjectPageProps } from './pageTypes'

export default function DashboardPage({ projectId, project, overview, refreshOverview }: ProjectPageProps) {
  const [indexing, setIndexing] = useState(false)
  const [indexResult, setIndexResult] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  if (!projectId || !project) return <EmptyState title="Project를 선택해주세요" description="상단 Project 선택기에서 작업할 Project를 선택하면 로컬 상태를 불러옵니다." />
  if (!overview) return <LoadingState label="Project 상태 확인 중" />
  const indexProject = async () => {
    setIndexing(true); setError(null); setIndexResult(null)
    try { const result = await indexApi.index(projectId); setIndexResult(`${result.status} · ${result.storedCount} chunks · ${formatDuration(result.totalDurationMillis)}`); await refreshOverview() }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : '인덱싱에 실패했습니다.') }
    finally { setIndexing(false) }
  }
  const changed = overview.git ? overview.git.modified.length + overview.git.added.length + overview.git.deleted.length + overview.git.untracked.length : 0
  return <>
    <PageHeader eyebrow="PROJECT OVERVIEW" title={project.name} description={`${project.projectId} · ${project.detectedFramework ?? project.projectType}`} action={<button className="button button-primary" onClick={indexProject} disabled={indexing}>{indexing ? '인덱싱 중…' : 'Project 인덱싱'}</button>} />
    {error && <ErrorNotice message={error} />}{indexResult && <div className="notice notice-success">{indexResult}</div>}
    <div className="metric-grid">
      <Metric label="INDEXED CHUNKS" value={overview.index?.storedChunkCount?.toLocaleString() ?? '—'} hint={overview.index?.latestIndexedAt ? relativeTime(overview.index.latestIndexedAt) : '아직 인덱싱되지 않음'} accent="#7dd3fc" />
      <Metric label="GIT BRANCH" value={overview.git?.branch ?? '—'} hint={overview.git?.clean ? 'Working tree clean' : `${changed}개 변경 감지`} accent={overview.git?.clean ? '#6ee7b7' : '#fbbf24'} />
      <Metric label="OPEN ERRORS" value={(overview.errors?.unverified ?? 0) + (overview.errors?.verified ?? 0)} hint={`Resolved ${overview.errors?.resolved ?? 0}`} accent="#fb7185" />
      <Metric label="NOTIFICATIONS" value={overview.unacknowledgedNotificationCount} hint={`${overview.notificationCandidateCount} total candidates`} accent="#c4b5fd" />
    </div>
    <div className="dashboard-grid">
      <Panel><div className="panel-heading"><div><span className="eyebrow">PROJECT STATUS</span><h2>Repository signal</h2></div><StatusBadge label={overview.git?.clean ? 'CLEAN' : 'CHANGED'} tone={overview.git?.clean ? 'ok' : 'warn'} /></div>
        <dl className="detail-list"><div><dt>Type</dt><dd>{project.projectType}</dd></div><div><dt>Framework</dt><dd>{project.detectedFramework ?? 'Unknown'}</dd></div><div><dt>Git</dt><dd>{project.gitRepository ? 'Repository' : 'Not detected'}</dd></div><div><dt>Index model</dt><dd>{overview.index?.embeddingModel ?? '—'}</dd></div></dl>
      </Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">ENVIRONMENT</span><h2>Local services</h2></div><small>{formatDuration(overview.durationMillis)}</small></div>
        <div className="service-list">{[['Docker',overview.docker],['PostgreSQL / pgvector',overview.database],['Ollama',overview.ollama],['Project container',overview.projectContainers]].map(([name,value]) => <div key={name as string}><span>{name as string}</span><StatusBadge label={(value as typeof overview.docker)?.status ?? 'UNAVAILABLE'} tone={statusTone((value as typeof overview.docker)?.status)} /></div>)}</div>
      </Panel>
      <Panel className="panel-wide"><div className="panel-heading"><div><span className="eyebrow">DEVELOPMENT</span><h2>Recent commits</h2></div><span className="muted">{overview.recentCommits?.commits.length ?? 0} records</span></div>
        {overview.recentCommits?.commits.length ? <div className="commit-list">{overview.recentCommits.commits.slice(0,4).map(commit => <div key={commit.hash}><code>{shortHash(commit.hash)}</code><strong>{commit.message}</strong><span>{commit.author} · {formatDate(commit.timestamp)}</span></div>)}</div> : <div className="inline-empty">최근 commit 정보가 없습니다.</div>}
      </Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">ERRORS</span><h2>Verification state</h2></div></div><div className="triple-count"><div><strong>{overview.errors?.unverified ?? 0}</strong><span>Unverified</span></div><div><strong>{overview.errors?.verified ?? 0}</strong><span>Verified</span></div><div><strong>{overview.errors?.resolved ?? 0}</strong><span>Resolved</span></div></div></Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">AUTOMATION</span><h2>Latest run</h2></div>{overview.latestAutomation && <StatusBadge label={overview.latestAutomation.status} tone={statusTone(overview.latestAutomation.status)} />}</div>
        {overview.latestAutomation ? <dl className="detail-list"><div><dt>Trigger</dt><dd>{overview.latestAutomation.triggerType}</dd></div><div><dt>Change</dt><dd>{overview.latestAutomation.changeDetected ? 'Detected' : 'No change'}</dd></div><div><dt>Finished</dt><dd>{relativeTime(overview.latestAutomation.finishedAt)}</dd></div><div><dt>Duration</dt><dd>{formatDuration(overview.latestAutomation.durationMillis)}</dd></div></dl> : <div className="inline-empty">실행 이력이 없습니다.</div>}
      </Panel>
    </div>
    {overview.warnings.length > 0 && <div className="notice notice-warn"><strong>일부 상태를 불러오지 못했습니다.</strong><span>{overview.warnings.join(' · ')}</span></div>}
  </>
}
