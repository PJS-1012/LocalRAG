import { Fragment, useMemo, useState } from 'react'
import { ragApi } from '../api/ragApi'
import { ApiError } from '../api/client'
import { CodeBlock, EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDuration, statusTone } from '../lib/format'
import type { DocumentReadResult, RagResponse, RagSource } from '../types'
import type { ProjectPageProps } from './pageTypes'

function Answer({ value, sources, onSource }: { value: string; sources: RagSource[]; onSource: (source: RagSource) => void }) {
  const parts = value.split(/(\[S\d+\])/g)
  return <div className="answer-copy">{parts.map((part, index) => {
    const id = part.match(/^\[(S\d+)\]$/)?.[1]; const source = sources.find(item => item.id === id)
    return source ? <button className="citation" key={`${part}-${index}`} onClick={() => onSource(source)}>{part}</button> : <Fragment key={index}>{part}</Fragment>
  })}</div>
}

export default function RagPage({ projectId, project }: ProjectPageProps) {
  const [query, setQuery] = useState('이 프로젝트의 주요 구조와 현재 구현 상태를 설명해줘')
  const [result, setResult] = useState<RagResponse | null>(null)
  const [selected, setSelected] = useState<RagSource | null>(null)
  const [document, setDocument] = useState<DocumentReadResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [sourceLoading, setSourceLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const sourceExcerpt = useMemo(() => {
    if (!selected || !document?.document) return null
    const lines = document.document.content.split('\n'); const from = Math.max(0, selected.startLine - 1); const to = Math.min(lines.length, selected.endLine)
    return lines.slice(from, to).map((line, index) => `${String(from + index + 1).padStart(4, ' ')}  ${line}`).join('\n')
  }, [document, selected])
  if (!projectId) return <EmptyState title="Project가 필요합니다" description="RAG 검색 범위를 강제하기 위해 먼저 Project를 선택해주세요." />
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (!query.trim()) return
    setLoading(true); setError(null); setResult(null); setSelected(null); setDocument(null)
    try { setResult(await ragApi.ask(projectId, query.trim())) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'RAG 요청에 실패했습니다.') }
    finally { setLoading(false) }
  }
  const openSource = async (source: RagSource) => {
    setSelected(source); setSourceLoading(true)
    try { setDocument(await ragApi.source(projectId, source.filePath)) } catch { setDocument(null) }
    finally { setSourceLoading(false) }
  }
  return <>
    <PageHeader eyebrow="KNOWLEDGE" title="Project-grounded chat" description={`${project?.name ?? projectId}의 인덱싱된 자료만 사용합니다.`} />
    <div className="split-workspace">
      <Panel className="chat-panel"><div className="chat-scroll">
        {!result && !loading && <div className="chat-intro"><span className="orb">◈</span><h2>Project 지식에 질문하세요</h2><p>답변은 검색된 Source와 citation으로 근거를 표시합니다.</p></div>}
        {loading && <LoadingState label="관련 자료 검색 및 Local Model 분석 중" />}
        {error && <ErrorNotice message={error} />}
        {result && <div className="message-stack"><div className="message message-user">{result.query}</div><div className="message message-assistant"><div className="message-meta"><StatusBadge label={result.status} tone={statusTone(result.status)} /><span>{formatDuration(result.totalDurationMillis)}</span></div>{result.status === 'NO_EVIDENCE' ? <div className="no-evidence"><strong>근거를 찾지 못했습니다.</strong><span>현재 Project 자료만으로 답할 수 없어 추측하지 않았습니다.</span></div> : <Answer value={result.answer} sources={result.sources} onSource={openSource} />}{result.warnings.length > 0 && <div className="warning-list">{result.warnings.join(' · ')}</div>}</div></div>}
      </div><form className="prompt-box" onSubmit={submit}><textarea aria-label="RAG 질문" value={query} onChange={event => setQuery(event.target.value)} rows={3} placeholder="Project 자료에 대해 질문하세요" /><div><span>Top-K 5 · threshold 0.45 · project scoped</span><button className="button button-primary" disabled={loading || !query.trim()}>전송</button></div></form></Panel>
      <Panel className="sources-panel"><div className="panel-heading"><div><span className="eyebrow">SOURCES</span><h2>Retrieved context</h2></div><span className="source-count">{result?.sourceCount ?? 0}</span></div>
        <div className="source-list">{result?.sources.map(source => <button key={source.id} className={selected?.id === source.id ? 'source-active' : ''} onClick={() => openSource(source)}><span>{source.id}</span><div><strong>{source.fileName}</strong><small>{source.filePath} · L{source.startLine}–{source.endLine}</small></div></button>)}{!result?.sources.length && <div className="inline-empty">질문 후 검색된 Source가 여기에 표시됩니다.</div>}</div>
        {selected && <div className="source-preview"><div><strong>{selected.fileName}</strong><span>L{selected.startLine}–{selected.endLine}</span></div>{sourceLoading ? <LoadingState label="Source 읽는 중" /> : sourceExcerpt ? <CodeBlock>{sourceExcerpt}</CodeBlock> : <ErrorNotice message="Source 내용을 읽을 수 없습니다." detail={document?.reason ?? undefined} />}</div>}
      </Panel>
    </div>
  </>
}
