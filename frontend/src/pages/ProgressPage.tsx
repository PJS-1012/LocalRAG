import { useState } from 'react'
import { workflowApi } from '../api/workflowApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDate, formatDuration, statusTone } from '../lib/format'
import type { ProjectProgress } from '../types'
import type { ProjectPageProps } from './pageTypes'

const SECTIONS: Array<{ key: keyof Pick<ProjectProgress,'completed'|'inProgress'|'planned'|'blocked'|'documentationMismatch'|'unknown'>; label:string; tone:string }>=[
  {key:'completed',label:'Completed',tone:'section-green'},{key:'inProgress',label:'In progress',tone:'section-blue'},
  {key:'planned',label:'Planned',tone:'section-purple'},{key:'blocked',label:'Blocked / Issues',tone:'section-red'},
  {key:'documentationMismatch',label:'Documentation mismatch',tone:'section-amber'},{key:'unknown',label:'Unknown',tone:'section-gray'},
]

export default function ProgressPage({projectId,project}:ProjectPageProps){
  const[result,setResult]=useState<ProjectProgress|null>(null);const[loading,setLoading]=useState(false);const[error,setError]=useState<string|null>(null)
  if(!projectId)return <EmptyState title="Project가 필요합니다" description="Progress 분석은 선택한 Project의 Git·RAG·Error History 근거를 사용합니다."/>
  const run=async()=>{setLoading(true);setError(null);try{setResult(await workflowApi.progress(projectId))}catch(reason){setError(reason instanceof ApiError?reason.message:'Progress 분석에 실패했습니다.')}finally{setLoading(false)}}
  return <><PageHeader eyebrow="작업 현황" title="프로젝트 진행 상태" description="완료율을 추정하지 않고 확인 가능한 근거별로 현재 상태를 정리합니다." action={<button className="button button-primary" onClick={run} disabled={loading}>{loading?'분석 중…':'새 분석'}</button>}/>{error&&<ErrorNotice message={error}/>} {loading&&<LoadingState label="Git·Project 자료 수집 및 Local Model 분석 중"/>}
    {!loading&&!result&&<Panel><div className="analysis-placeholder"><span>↗</span><h2>{project?.name}의 현재 상태를 분석할 준비가 됐습니다.</h2><p>버튼을 눌렀을 때만 LLM을 호출합니다. 페이지 진입만으로 분석하지 않습니다.</p></div></Panel>}
    {result&&<><Panel><div className="panel-heading"><div><span className="eyebrow">LATEST ANALYSIS</span><h2>Evidence-backed summary</h2></div><StatusBadge label={result.status} tone={statusTone(result.status)}/></div><p className="summary-lead">{result.summary}</p><div className="analysis-meta"><span>{formatDate(result.generatedAt)}</span><span>Unresolved errors {result.unresolvedErrors}</span><span>LLM {formatDuration(result.llmDurationMillis)}</span><span>Total {formatDuration(result.totalDurationMillis)}</span></div></Panel><div className="progress-grid">{SECTIONS.map(section=><Panel key={section.key} className={`progress-section ${section.tone}`}><div className="panel-heading"><h2>{section.label}</h2><span>{result[section.key].length}</span></div><ul>{result[section.key].map((item,index)=><li key={index}>{item}</li>)}</ul>{result[section.key].length===0&&<div className="inline-empty">확인된 항목 없음</div>}</Panel>)}</div></>}
  </>
}
