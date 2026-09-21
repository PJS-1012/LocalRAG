import { useEffect, useRef, useState } from 'react'
import type { ProjectSummary, WorkspaceOverview } from '../types'
import { Panel, StatusBadge } from './ui'
import { formatDate, shortHash } from '../lib/format'
import { label } from '../lib/labels'

export function remoteLabel(git: ProjectSummary['git']) {
  if (git?.remoteStatus === 'TRACKING') return `${git.ahead === 0 ? 'Push 완료' : `미Push 커밋 ${git.ahead}개`} · ${git.behind === 0 ? (git.ahead === 0 ? '원격과 동일' : '뒤처진 커밋 없음') : `원격보다 ${git.behind}개 뒤처짐`}`
  return label(git?.remoteStatus ?? 'UNKNOWN')
}
export function workLabel(git: ProjectSummary['git']) {
  if(git?.status === 'NOT_GIT_REPOSITORY') return 'Git 저장소 아님'
  if(git?.clean == null) return '확인 불가'
  if(git.clean) return '변경 없음'
  return git.changes ? `수정 ${git.changes.modified} · 신규 ${git.changes.added} · 삭제 ${git.changes.deleted}` : '수정된 파일 있음'
}
export function languageLabel(project: ProjectSummary) {
  if(!project.languages) return '언어 확인 불가'
  return project.languages.languages.slice(0,3).map(l=>`${l.name} ${l.percent}%`).join(' · ') || '지원 소스 파일 없음'
}
export function CommitList({ git }: { git: ProjectSummary['git'] }) {
  const [copied, setCopied] = useState<string | null>(null)
  return <div className="commit-list">{git?.commits.map(commit => <div key={commit.hash}>
    <button className="hash-copy" title={commit.hash} aria-label={`커밋 해시 복사 ${commit.hash}`} onClick={() => {
      void navigator.clipboard.writeText(commit.hash).then(() => setCopied(commit.hash)).catch(() => setCopied(null))
    }}><code>{shortHash(commit.hash)}</code>{copied === commit.hash && <small>복사됨</small>}</button>
    <strong title={commit.message}>{commit.message}</strong><StatusBadge label={commit.pushStatus} tone={commit.pushStatus === 'PUSHED' ? 'ok' : 'warn'} />
    <span className="commit-author">작성자: {commit.author} · {formatDate(commit.timestamp)}</span>
  </div>)}</div>
}
export default function ProjectList({ summary, error, retry }: { summary: WorkspaceOverview | null; error: string | null; retry: () => void }) {
  const [selected, setSelected] = useState<string | null>(null)
  const detailRef = useRef<HTMLElement>(null)
  useEffect(() => { detailRef.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' }) }, [selected])
  const detail = summary?.projects.find(p => p.project.projectId === selected)
  return <Panel className="all-projects"><div className="panel-heading"><div><span className="eyebrow">작업공간</span><h2>전체 프로젝트 <span>{summary?.projects.length ?? '—'}</span></h2></div><button className="button button-secondary" onClick={retry}>메타데이터 새로고침</button></div>
    {error && <div role="alert" className="notice notice-warn">{error}</div>}
    {!summary && !error && <p>프로젝트 정보 확인 중…</p>}
    <div className="project-scroll" aria-label="전체 프로젝트">{summary?.projects.map(p => <button className={selected === p.project.projectId ? 'project-row selected-row' : 'project-row'} key={p.project.projectId} onClick={() => setSelected(p.project.projectId)}>
      <span><strong>{p.project.name}</strong><small>{p.project.projectId} · {p.project.projectType}</small><small className="language-info">{languageLabel(p)} (파일 기준)</small></span>
      <span>{p.git?.branch ?? '—'}<small>작업: {workLabel(p.git)}</small><small>원격: {remoteLabel(p.git)}</small></span>
      <span>{p.indexedChunkCount ?? '—'}개 청크<small>자동화 {label(p.automationStatus)}</small></span>
      <span>{p.errorHistoryCount ?? '—'}개 오류<small>{p.notificationCount ?? '—'}개 알림</small></span>
    </button>)}</div>
    <p className="metadata-note">원격 상태: 설정된 upstream의 로컬 참조 기준 · 자동 fetch 없음 · 언어 통계: 캐시된 파일 수 기준</p>
    {detail && <section ref={detailRef} className="project-detail" aria-label="프로젝트 상세"><div className="panel-heading"><h2>{detail.project.name} · 프로젝트 상세</h2><button className="button button-secondary" onClick={() => setSelected(null)}>상세 닫기</button></div>
      <dl className="detail-list">{[
        ['프로젝트 ID',detail.project.projectId],['프로젝트 경로',detail.project.rootPath],['프로젝트 유형',detail.project.projectType],
        ['주요 언어 (허용 소스 파일 수 기준)',languageLabel(detail)],
        ['브랜치',detail.git?.branch ?? '—'],['작업 상태',workLabel(detail.git)],
        ['원격 브랜치',detail.git?.upstream ?? '원격 브랜치 없음'],['원격 상태',remoteLabel(detail.git)],
        ['미Push 커밋 수',detail.git?.ahead ?? '확인 불가'],['최근 커밋',detail.git?.commits[0]?.hash ?? '—'],
        ['인덱싱',`${detail.indexedDocumentCount ?? '—'}개 문서 / ${detail.indexedChunkCount ?? '—'}개 청크`],
        ['자동화',label(detail.automationStatus)],['오류',detail.errorHistoryCount ?? '—'],['알림',detail.notificationCount ?? '—']
      ].map(([label,value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <CommitList git={detail.git} />
      {detail.warnings.length > 0 && <p role="alert">{detail.warnings.join(' · ')}</p>}
    </section>}
  </Panel>
}
