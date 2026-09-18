import { useEffect, useRef, useState } from 'react'
import type { ProjectSummary, WorkspaceOverview } from '../types'
import { Panel, StatusBadge } from './ui'
import { formatDate, shortHash } from '../lib/format'

export function remoteLabel(git: ProjectSummary['git']) {
  if (git?.remoteStatus === 'TRACKING') return `↑ ${git.ahead} ahead · ↓ ${git.behind} behind`
  return git?.remoteStatus === 'NO_UPSTREAM' ? 'No upstream' : git?.remoteStatus ?? 'UNKNOWN'
}
export function CommitList({ git }: { git: ProjectSummary['git'] }) {
  const [copied, setCopied] = useState<string | null>(null)
  return <div className="commit-list">{git?.commits.map(commit => <div key={commit.hash}>
    <button className="hash-copy" title={commit.hash} aria-label={`Copy commit ${commit.hash}`} onClick={() => {
      void navigator.clipboard.writeText(commit.hash).then(() => setCopied(commit.hash)).catch(() => setCopied(null))
    }}><code>{shortHash(commit.hash)}</code>{copied === commit.hash && <small>Copied</small>}</button>
    <strong title={commit.message}>{commit.message}</strong><StatusBadge label={commit.pushStatus} tone={commit.pushStatus === 'PUSHED' ? 'ok' : 'warn'} />
    <span className="commit-author">{commit.author} · {formatDate(commit.timestamp)}</span>
  </div>)}</div>
}
export default function ProjectList({ summary, error, retry }: { summary: WorkspaceOverview | null; error: string | null; retry: () => void }) {
  const [selected, setSelected] = useState<string | null>(null)
  const detailRef = useRef<HTMLElement>(null)
  useEffect(() => { detailRef.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' }) }, [selected])
  const detail = summary?.projects.find(p => p.project.projectId === selected)
  return <Panel className="all-projects"><div className="panel-heading"><div><span className="eyebrow">WORKSPACE</span><h2>All projects <span>{summary?.projects.length ?? '—'}</span></h2></div><button className="button button-secondary" onClick={retry}>Refresh metadata</button></div>
    {error && <div role="alert" className="notice notice-warn">{error}</div>}
    {!summary && !error && <p>Project metadata 확인 중…</p>}
    <div className="project-scroll" aria-label="All projects">{summary?.projects.map(p => <button className={selected === p.project.projectId ? 'project-row selected-row' : 'project-row'} key={p.project.projectId} onClick={() => setSelected(p.project.projectId)}>
      <span><strong>{p.project.name}</strong><small>{p.project.projectId} · {p.project.projectType}</small></span>
      <span>{p.git?.branch ?? '—'}<small>Working tree: {p.git?.clean == null ? 'UNKNOWN' : p.git.clean ? 'CLEAN' : 'DIRTY'}</small><small>{remoteLabel(p.git)}</small></span>
      <span>{p.indexedChunkCount ?? '—'} chunks<small>Automation {p.automationStatus}</small></span>
      <span>{p.errorHistoryCount ?? '—'} errors<small>{p.notificationCount ?? '—'} notifications</small></span>
    </button>)}</div>
    <p className="metadata-note">Remote: configured upstream의 로컬 참조 기준 · 자동 fetch 없음</p>
    {detail && <section ref={detailRef} className="project-detail" aria-label="Project detail"><div className="panel-heading"><h2>{detail.project.name} · Project detail</h2><button className="button button-secondary" onClick={() => setSelected(null)}>Close detail</button></div>
      <dl className="detail-list">{[
        ['Project ID',detail.project.projectId],['Root path',detail.project.rootPath],['Type',detail.project.projectType],
        ['Branch',detail.git?.branch ?? '—'],['Working tree',detail.git?.clean == null ? 'UNKNOWN' : detail.git.clean ? 'CLEAN' : 'DIRTY'],
        ['Upstream',detail.git?.upstream ?? 'No upstream'],['Remote',remoteLabel(detail.git)],
        ['Unpushed commits',detail.git?.ahead ?? 'UNKNOWN'],['Latest commit',detail.git?.commits[0]?.hash ?? '—'],
        ['Index',`${detail.indexedDocumentCount ?? '—'} documents / ${detail.indexedChunkCount ?? '—'} chunks`],
        ['Automation',detail.automationStatus],['Error history',detail.errorHistoryCount ?? '—'],['Notifications',detail.notificationCount ?? '—']
      ].map(([label,value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <CommitList git={detail.git} />
      {detail.warnings.length > 0 && <p role="alert">{detail.warnings.join(' · ')}</p>}
    </section>}
  </Panel>
}
