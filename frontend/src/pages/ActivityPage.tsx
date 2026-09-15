import { useState } from 'react'
import { workflowApi } from '../api/workflowApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, Segmented, StatusBadge } from '../components/ui'
import { formatDate, formatDuration, shortHash, statusTone } from '../lib/format'
import type { ActivitySummary } from '../types'
import type { ProjectPageProps } from './pageTypes'

type Range='commits'|'today'|'24h'|'7d'
const sinceFor=(range:Range)=>{const now=new Date();if(range==='commits')return null;if(range==='today'){now.setHours(0,0,0,0);return now.toISOString()}if(range==='24h')return new Date(Date.now()-86_400_000).toISOString();return new Date(Date.now()-7*86_400_000).toISOString()}

export default function ActivityPage({projectId,project}:ProjectPageProps){
  const[range,setRange]=useState<Range>('commits');const[result,setResult]=useState<ActivitySummary|null>(null);const[loading,setLoading]=useState(false);const[error,setError]=useState<string|null>(null)
  if(!projectId)return <EmptyState title="Project가 필요합니다" description="최근 개발 작업은 선택한 Project의 Git 범위에서만 확인합니다."/>
  const run=async()=>{setLoading(true);setError(null);try{setResult(await workflowApi.activity(projectId,sinceFor(range),5))}catch(reason){setError(reason instanceof ApiError?reason.message:'최근 작업 요약에 실패했습니다.')}finally{setLoading(false)}}
  return <><PageHeader eyebrow="DEVELOPMENT ACTIVITY" title="What changed recently" description={`${project?.name??projectId}의 commit, working tree와 Decision Log 근거를 요약합니다.`} action={<button className="button button-primary" onClick={run} disabled={loading}>{loading?'요약 중…':'요약 생성'}</button>}/><Segmented value={range} onChange={value=>setRange(value as Range)} options={[{value:'commits',label:'최근 5 commit'},{value:'today',label:'오늘'},{value:'24h',label:'최근 24시간'},{value:'7d',label:'최근 7일'}]}/>{error&&<ErrorNotice message={error}/>} {loading&&<LoadingState label="Git evidence 및 Decision Log 분석 중"/>}
    {!loading&&!result&&<Panel><div className="analysis-placeholder"><span>≋</span><h2>원하는 기간을 선택하고 요약을 생성하세요.</h2><p>실행 버튼을 눌렀을 때만 Local Model이 호출됩니다.</p></div></Panel>}
    {result&&<div className="activity-grid"><Panel className="panel-wide"><div className="panel-heading"><div><span className="eyebrow">SUMMARY</span><h2>Recent development</h2></div><StatusBadge label={result.status} tone={statusTone(result.status)}/></div><p className="summary-lead">{result.summary}</p><div className="tag-row">{result.changedAreas.map(area=><span key={area}>{area}</span>)}</div><div className="analysis-meta"><span>{formatDate(result.generatedAt)}</span><span>Git {formatDuration(result.gitEvidenceDurationMillis)}</span><span>LLM {formatDuration(result.llmDurationMillis)}</span></div></Panel><Panel><div className="panel-heading"><h2>Commits</h2><span>{result.commits.length}</span></div><div className="commit-list">{result.commits.map(commit=><div key={commit.hash}><code>{shortHash(commit.hash)}</code><strong>{commit.message}</strong><span>{formatDate(commit.timestamp)}</span></div>)}</div></Panel><Panel><div className="panel-heading"><h2>Working tree</h2>{result.currentWorkingTree&&<StatusBadge label={result.currentWorkingTree.clean?'CLEAN':'CHANGED'} tone={result.currentWorkingTree.clean?'ok':'warn'}/>}</div>{result.currentWorkingTree?<dl className="detail-list"><div><dt>Branch</dt><dd>{result.currentWorkingTree.branch}</dd></div><div><dt>Modified</dt><dd>{result.currentWorkingTree.modified.length}</dd></div><div><dt>Untracked</dt><dd>{result.currentWorkingTree.untracked.length}</dd></div></dl>:<div className="inline-empty">Git 상태 없음</div>}</Panel></div>}
  </>
}
