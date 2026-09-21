import { useState } from 'react'
import { indexApi } from '../api/indexApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, Metric, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDuration, relativeTime, statusTone } from '../lib/format'
import { CommitList, remoteLabel } from '../components/ProjectList'
import type { DashboardGit } from '../types'
import type { ProjectPageProps } from './pageTypes'

export default function DashboardPage({ projectId, project, overview, refreshOverview, gitSummary }: ProjectPageProps & { gitSummary: DashboardGit | null }) {
  const [indexing, setIndexing] = useState(false)
  const [indexResult, setIndexResult] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  if (!projectId || !project) return <EmptyState title="프로젝트를 선택해주세요" description="상단 프로젝트 선택기에서 작업할 프로젝트를 선택하면 로컬 상태를 불러옵니다." />
  if (!overview) return <LoadingState label="프로젝트 상태 확인 중" />
  const indexProject = async () => {
    setIndexing(true); setError(null); setIndexResult(null)
    try { const result = await indexApi.index(projectId); setIndexResult(`${result.status} · ${result.storedCount} chunks · ${formatDuration(result.totalDurationMillis)}`); await refreshOverview() }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : '인덱싱에 실패했습니다.') }
    finally { setIndexing(false) }
  }
  const changed = overview.git ? overview.git.modified.length + overview.git.added.length + overview.git.deleted.length + overview.git.untracked.length : 0
  return <>
    <PageHeader eyebrow="프로젝트 개요" title={project.name} description={`${project.projectId} · ${project.detectedFramework ?? project.projectType}`} action={<button className="button button-primary" onClick={indexProject} disabled={indexing}>{indexing ? '인덱싱 중…' : '프로젝트 인덱싱'}</button>} />
    {error && <ErrorNotice message={error} />}{indexResult && <div className="notice notice-success">{indexResult}</div>}
    <div className="metric-grid">
      <Metric label="인덱싱 청크" value={overview.index?.storedChunkCount?.toLocaleString() ?? '—'} hint={overview.index?.latestIndexedAt ? relativeTime(overview.index.latestIndexedAt) : '아직 인덱싱되지 않음'} accent="#7dd3fc" />
      <Metric label="Git 브랜치" value={overview.git?.branch ?? '—'} hint={overview.git?.status !== 'SUCCESS' ? 'Git 상태 확인 불가' : overview.git.clean ? '변경 없음' : `${changed}개 변경 감지`} accent={overview.git?.clean ? '#6ee7b7' : '#fbbf24'} />
      <Metric label="미해결 오류" value={(overview.errors?.unverified ?? 0) + (overview.errors?.verified ?? 0)} hint={`해결됨 ${overview.errors?.resolved ?? 0}`} accent="#fb7185" />
      <Metric label="알림" value={overview.unacknowledgedNotificationCount} hint={`${overview.notificationCandidateCount} 개 전체 후보`} accent="#c4b5fd" />
    </div>
    <div className="dashboard-grid">
      <Panel><div className="panel-heading"><div><span className="eyebrow">작업 상태</span><h2>Git 작업 현황</h2></div><StatusBadge label={overview.git?.status !== 'SUCCESS' ? overview.git?.status ?? 'UNKNOWN' : overview.git.clean ? 'CLEAN' : 'CHANGED'} tone={overview.git?.clean ? 'ok' : 'warn'} /></div>
        <dl className="detail-list"><div><dt>프로젝트 유형</dt><dd>{project.projectType}</dd></div><div><dt>프레임워크</dt><dd>{project.detectedFramework ?? '확인 불가'}</dd></div><div><dt>Git</dt><dd>{project.gitRepository ? 'Git 저장소' : 'Git 저장소 아님'}</dd></div><div><dt>임베딩 모델</dt><dd>{overview.index?.embeddingModel ?? '—'}</dd></div></dl>
      </Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">실행 환경</span><h2>로컬 서비스</h2></div><small>{formatDuration(overview.durationMillis)}</small></div>
        <div className="service-list">{[['Docker',overview.docker],['PostgreSQL / pgvector',overview.database],['Ollama',overview.ollama],['프로젝트 컨테이너',overview.projectContainers]].map(([name,value]) => <div key={name as string}><span>{name as string}</span><StatusBadge label={(value as typeof overview.docker)?.status ?? 'UNAVAILABLE'} tone={statusTone((value as typeof overview.docker)?.status)} /></div>)}</div>
      </Panel>
      <Panel className="panel-wide"><div className="panel-heading"><div><span className="eyebrow">개발 이력</span><h2>최근 커밋</h2></div><span className="muted">{overview.recentCommits?.commits.length ?? 0}개</span></div>
        <p className="metadata-note">{remoteLabel(gitSummary)} · 로컬 upstream 참조 기준</p>
        {gitSummary?.commits.length ? <CommitList git={gitSummary} /> : <div className="inline-empty">최근 commit 정보가 없습니다.</div>}
      </Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">오류</span><h2>검증 상태</h2></div></div><div className="triple-count"><div><strong>{overview.errors?.unverified ?? 0}</strong><span>미검증</span></div><div><strong>{overview.errors?.verified ?? 0}</strong><span>검증됨</span></div><div><strong>{overview.errors?.resolved ?? 0}</strong><span>해결됨</span></div></div></Panel>
      <Panel><div className="panel-heading"><div><span className="eyebrow">자동화</span><h2>최근 실행</h2></div>{overview.latestAutomation && <StatusBadge label={overview.latestAutomation.status} tone={statusTone(overview.latestAutomation.status)} />}</div>
        {overview.latestAutomation ? <dl className="detail-list"><div><dt>실행 계기</dt><dd>{overview.latestAutomation.triggerType}</dd></div><div><dt>변경 사항</dt><dd>{overview.latestAutomation.changeDetected ? '변경 감지' : '변경 없음'}</dd></div><div><dt>완료 시각</dt><dd>{relativeTime(overview.latestAutomation.finishedAt)}</dd></div><div><dt>소요 시간</dt><dd>{formatDuration(overview.latestAutomation.durationMillis)}</dd></div></dl> : <div className="inline-empty">실행 이력이 없습니다.</div>}
      </Panel>
    </div>
    {overview.warnings.length > 0 && <div className="notice notice-warn"><strong>일부 상태를 불러오지 못했습니다.</strong><span>{overview.warnings.join(' · ')}</span></div>}
  </>
}
