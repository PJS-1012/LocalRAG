import { Fragment, useRef, useState } from 'react'
import { apiRequest } from '../api/client'
import { submitOnEnter } from '../lib/chatInput'
import { chatWarning } from '../lib/labels'
import { formatDuration, statusTone } from '../lib/format'
import { EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, StatusBadge } from '../components/ui'
import type { AgentResponse } from '../types'
import type { ProjectPageProps } from './pageTypes'

interface UnifiedResponse { result: AgentResponse; routing: string; routes: string[]; evidence: Array<{ sequence: number; toolName: string; observedAt: string; result: unknown }> }
function evidenceGroup(name:string) {
  if(name==='searchProjectKnowledge')return '코드·문서 / 프로젝트 정보'
  if(['analyzeProjectProgress','summarizeRecentDevelopment','findSimilarErrors'].includes(name))return '작업 이력'
  return '실행 상태'
}
const SUGGESTIONS=['이 프로젝트 처음 보는데 전체적으로 설명해줘','처음 보면 어떤 파일부터 보면 돼?','최근 작업이랑 남은 문제 알려줘','현재 Git 상태 알려줘']
export default function UnifiedChatPage({ projectId, project }: ProjectPageProps) {
  const [query,setQuery]=useState('')
  const [response,setResponse]=useState<UnifiedResponse|null>(null)
  const [loading,setLoading]=useState(false)
  const [error,setError]=useState<string|null>(null)
  const busy=useRef(false)
  if(!projectId)return <EmptyState title="프로젝트를 선택해주세요" description="상단에서 프로젝트를 선택하면 해당 범위의 코드와 상태를 함께 확인합니다."/>
  const submit=async(event:React.FormEvent)=>{
    event.preventDefault();if(busy.current||!query.trim())return
    busy.current=true;setLoading(true);setError(null);setResponse(null)
    try {setResponse(await apiRequest<UnifiedResponse>('/api/workspaces/projects/chat/unified',{method:'POST',body:JSON.stringify({projectId,query:query.trim()})}))}
    catch(reason){setError(reason instanceof Error?reason.message:'질문 처리에 실패했습니다.')}
    finally{busy.current=false;setLoading(false)}
  }
  const result=response?.result
  return <><PageHeader eyebrow="프로젝트 대화" title="무엇이 궁금하세요?" description={`${project?.name??projectId}의 코드·Git·진행 상태를 질문에 맞게 함께 확인합니다.`}/>
    <div className="split-workspace"><Panel className="chat-panel"><div className="suggestions">{SUGGESTIONS.map(q=><button key={q} onClick={()=>setQuery(q)}>{q}</button>)}</div>
      <div className="chat-scroll">{!result&&!loading&&<div className="chat-intro"><h2>프로젝트를 몰라도 괜찮습니다</h2><p>목적과 구조, 시작할 파일, 최근 작업을 평소 말하듯 물어보세요.</p><p>일반 개발 지식도 질문할 수 있습니다. 파일이나 실행 상태는 변경하지 않습니다.</p></div>}
      {loading&&<LoadingState label="질문에 필요한 근거를 확인하고 답변하는 중"/>}{error&&<ErrorNotice message={error}/>}
      {result&&<div className="message-stack"><div className="message message-user">{result.query}</div><div className="message message-assistant"><div className="message-meta"><StatusBadge label={result.status} tone={statusTone(result.status)}/><span>{formatDuration(result.totalDurationMillis)}</span></div>
        <div className="answer-copy">{result.answer.split(/(\[K\d+-S\d+\])/g).map((part,i)=>{const source=result.knowledgeSources.find(s=>`[${s.id}]`===part);return source?<a className="citation" href={`#source-${source.id}`} key={i}>{part}</a>:<Fragment key={i}>{part}</Fragment>})}</div>
        {result.warnings.length>0&&<div className="warning-list">확인 한계: {result.warnings.map(chatWarning).join(' · ')}</div>}</div></div>}</div>
      <form className="prompt-box" onSubmit={submit}><textarea aria-label="채팅 질문" rows={3} value={query} maxLength={4000} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>submitOnEnter(e,loading||!query.trim())} placeholder="이 프로젝트가 하는 일부터 알려줘"/><div><span>Enter 전송 · Shift+Enter 줄바꿈 · 읽기 전용</span><button className="button button-primary" disabled={loading||!query.trim()}>전송</button></div></form>
    </Panel><Panel className="sources-panel"><div className="panel-heading"><h2>답변 근거</h2><span className="source-count">{result?.knowledgeSourceCount??0}</span></div>
      <p className="metadata-note">코드·문서 / 실행 상태 / 작업 이력을 구분해서 확인하세요. 검색 결과가 없어도 다른 근거를 활용합니다.</p>
      {result&&<p>근거 수집 {formatDuration(result.toolExecutionDurationMillis)} · LLM {formatDuration(result.llmDurationMillis)}</p>}
      <div className="source-list">{result?.knowledgeSources.map(s=><div className="unified-source" id={`source-${s.id}`} key={s.id}><strong>{s.id}</strong><p>{s.filePath}</p><small>근거 위치: {s.startLine}–{s.endLine}행</small></div>)}</div>
      {result?.toolCalls.map(c=><div className="unified-source" key={c.sequence}><strong>{c.sequence}. {c.toolName}</strong><p>{c.outcome} · {formatDuration(c.durationMillis)}</p></div>)}
      {response?.evidence.map(e=><details key={e.sequence}><summary>{e.toolName} 근거 상세</summary><p>{evidenceGroup(e.toolName)}</p><pre className="evidence-json">{JSON.stringify(e.result,null,2)}</pre></details>)}
    </Panel></div></>
}
