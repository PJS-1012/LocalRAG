import { useState } from 'react'
import { agentApi } from '../api/agentApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDuration, statusTone } from '../lib/format'
import type { AgentResponse } from '../types'
import type { ProjectPageProps } from './pageTypes'

const EXAMPLES = ['현재 Git 상태 알려줘', 'DB와 Ollama 실행 중이야?', '최근 오류 확인해줘', '현재 프로젝트 진행 상황 알려줘']

export default function AgentPage({ projectId, project }: ProjectPageProps) {
  const [query, setQuery] = useState('현재 Git 상태 알려줘')
  const [result, setResult] = useState<AgentResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (!projectId) return <EmptyState title="Project가 필요합니다" description="Agent Tool의 실행 범위를 제한하기 위해 Project를 선택해주세요." />
  const run = async (event?: React.FormEvent) => {
    event?.preventDefault(); if (!query.trim()) return
    setLoading(true);setError(null);setResult(null)
    try { setResult(await agentApi.ask(projectId, query.trim())) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Agent 요청에 실패했습니다.') }
    finally { setLoading(false) }
  }
  return <>
    <PageHeader eyebrow="READ-ONLY AGENT" title="Local developer assistant" description={`${project?.name ?? projectId} 범위에서 허용된 Tool만 선택합니다.`} />
    <div className="agent-layout"><Panel className="chat-panel"><div className="suggestions">{EXAMPLES.map(example => <button key={example} onClick={() => setQuery(example)}>{example}</button>)}</div><div className="chat-scroll agent-scroll">
      {!result && !loading && <div className="chat-intro"><span className="orb">⌘</span><h2>한 문장으로 상태를 확인하세요</h2><p>Git, Docker, DB, Ollama, Log와 Project 지식을 읽기 전용으로 조합합니다.</p></div>}
      {loading && <LoadingState label="Tool 선택 및 Local Model 분석 중" />}{error && <ErrorNotice message={error} />}
      {result && <div className="message-stack"><div className="message message-user">{result.query}</div><div className="message message-assistant"><div className="message-meta"><StatusBadge label={result.status} tone={statusTone(result.status)} /><span>LLM {formatDuration(result.llmDurationMillis)}</span></div><div className="answer-copy">{result.answer}</div>{result.warnings.length > 0 && <div className="warning-list">{result.warnings.join(' · ')}</div>}</div></div>}
    </div><form className="prompt-box" onSubmit={run}><textarea aria-label="Agent 질문" rows={3} value={query} onChange={event => setQuery(event.target.value)} /><div><span>Read-only · project scoped · tool evidence</span><button className="button button-primary" disabled={loading || !query.trim()}>Agent 실행</button></div></form></Panel>
    <Panel><div className="panel-heading"><div><span className="eyebrow">TOOL TRACE</span><h2>Execution details</h2></div><span>{formatDuration(result?.toolExecutionDurationMillis)}</span></div>{result?.toolCalls.length ? <div className="tool-trace">{result.toolCalls.map(call => <div key={call.sequence}><span>{call.sequence}</span><div><strong>{call.toolName}</strong><small>{call.outcome}{call.sameArgumentsAs ? ` · #${call.sameArgumentsAs} 재사용` : ''}</small></div><StatusBadge label={formatDuration(call.durationMillis)} tone={call.successful ? 'ok' : 'bad'} /></div>)}</div> : <div className="inline-empty">사용된 Tool과 실행 시간이 표시됩니다.</div>}</Panel></div>
  </>
}
