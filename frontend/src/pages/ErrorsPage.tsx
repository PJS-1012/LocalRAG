import { useEffect, useState } from 'react'
import { errorApi, type ErrorFilters } from '../api/errorApi'
import { ApiError } from '../api/client'
import { CodeBlock, EmptyState, ErrorNotice, InfoNotice, LoadingState, PageHeader, Panel, Segmented, StatusBadge } from '../components/ui'
import { formatDate, formatDuration, shortHash, statusTone } from '../lib/format'
import type { ErrorAnalysis, ErrorHistory, ErrorHistoryDetail, ErrorHistoryPage, ErrorStatus, SimilarErrorResponse } from '../types'
import type { ProjectPageProps } from './pageTypes'

type Tab = 'history' | 'analyze' | 'similar'

export default function ErrorsPage({ projectId, project }: ProjectPageProps) {
  const [tab, setTab] = useState<Tab>('history')
  const [filters, setFilters] = useState<ErrorFilters>({ page: 0 })
  const [page, setPage] = useState<ErrorHistoryPage | null>(null)
  const [detail, setDetail] = useState<ErrorHistoryDetail | null>(null)
  const [analysisQuery, setAnalysisQuery] = useState('최근 발생한 오류를 분석해줘')
  const [analysis, setAnalysis] = useState<ErrorAnalysis | null>(null)
  const [similarMessage, setSimilarMessage] = useState('')
  const [similar, setSimilar] = useState<SimilarErrorResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const loadHistory = async (next = filters) => {
    if (!projectId) return
    setLoading(true);setError(null)
    try { setPage(await errorApi.list(projectId, next)) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Error History를 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }
  useEffect(() => { setDetail(null);setAnalysis(null);setSimilar(null);setSaved(false);setFilters({ page: 0 }); if (projectId) void loadHistory({ page: 0 }) }, [projectId])
  if (!projectId) return <EmptyState title="Project가 필요합니다" description="Error History는 Project별로 격리됩니다." />

  const openDetail = async (history: ErrorHistory) => {
    setLoading(true);setError(null)
    try { setDetail(await errorApi.detail(projectId, history.id)) } catch (reason) { setError(reason instanceof ApiError ? reason.message : '상세 정보를 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }
  const analyze = async (event: React.FormEvent) => {
    event.preventDefault();setLoading(true);setError(null);setAnalysis(null);setSaved(false)
    try {
      const value=await errorApi.analyze(projectId,analysisQuery);setAnalysis(value);setSimilarMessage(value.errorMessage)
      if(value.errorMessage) setSimilar(await errorApi.similar(projectId,value.errorMessage,value.errorType,value.symptom))
    } catch(reason){setError(reason instanceof ApiError?reason.message:'오류 분석에 실패했습니다.')}
    finally{setLoading(false)}
  }
  const saveAnalysis = async () => {
    if(!analysis)return;setLoading(true);setError(null)
    try{await errorApi.save(projectId,analysis.analysisId);setSaved(true);await loadHistory({page:0})}
    catch(reason){setError(reason instanceof ApiError?reason.message:'History 저장에 실패했습니다.')}
    finally{setLoading(false)}
  }
  const findSimilar = async (event: React.FormEvent) => {
    event.preventDefault();setLoading(true);setError(null)
    try{setSimilar(await errorApi.similar(projectId,similarMessage))}
    catch(reason){setError(reason instanceof ApiError?reason.message:'유사 오류 검색에 실패했습니다.')}
    finally{setLoading(false)}
  }
  const updateStatus = async (status: ErrorStatus, note: string, cause: string, solution: string) => {
    if(!detail)return
    setLoading(true);setError(null)
    try{const updated=await errorApi.updateStatus(projectId,detail.history,status,note,cause||null,solution||null);await openDetail(updated);await loadHistory(filters)}
    catch(reason){setError(reason instanceof ApiError?reason.message:'상태 변경에 실패했습니다.');setLoading(false)}
  }

  return <>
    <PageHeader eyebrow="오류 관리" title="근거로 확인하는 오류" description={`${project?.name ?? projectId}의 오류 분석, 검증 이력과 유사 사례를 관리합니다.`} action={<Segmented value={tab} onChange={value=>setTab(value as Tab)} options={[{value:'history',label:'이력'},{value:'analyze',label:'분석'},{value:'similar',label:'유사 오류'}]} />} />
    {error&&<ErrorNotice message={error}/>} {loading&&<LoadingState label={tab==='analyze'?'Tool evidence 수집 및 Local Model 분석 중':'오류 데이터 처리 중'}/>}
    {!loading&&tab==='history'&&<div className="errors-layout"><Panel><form className="filter-bar" onSubmit={event=>{event.preventDefault();void loadHistory({...filters,page:0})}}><select aria-label="오류 상태" value={filters.status??''} onChange={event=>setFilters({...filters,status:event.target.value as ErrorStatus|''})}><option value="">모든 상태</option><option>UNVERIFIED</option><option>VERIFIED</option><option>RESOLVED</option></select><input placeholder="Error type" value={filters.errorType??''} onChange={event=>setFilters({...filters,errorType:event.target.value})}/><input placeholder="Message 검색" value={filters.errorMessage??''} onChange={event=>setFilters({...filters,errorMessage:event.target.value})}/><input placeholder="Related file" value={filters.relatedFile??''} onChange={event=>setFilters({...filters,relatedFile:event.target.value})}/><input placeholder="Commit hash" value={filters.relatedCommit??''} onChange={event=>setFilters({...filters,relatedCommit:event.target.value})}/><button className="button button-secondary">검색</button></form>
      <div className="table-wrap"><table><thead><tr><th>Status</th><th>Error</th><th>Occurred</th><th>File / Commit</th></tr></thead><tbody>{page?.content.map(item=><tr key={item.id} onClick={()=>openDetail(item)} className={detail?.history.id===item.id?'selected-row':''}><td><StatusBadge label={item.status} tone={statusTone(item.status)}/></td><td><strong>{item.errorType??'Unknown error'}</strong><span>{item.errorMessage}</span></td><td>{formatDate(item.occurredAt??item.recordedAt)}</td><td><code>{item.relatedFiles[0]??'—'}</code><small>{shortHash(item.relatedCommits[0])}</small></td></tr>)}</tbody></table>{!page?.content.length&&<div className="inline-empty">조건에 맞는 Error History가 없습니다.</div>}</div>
      <div className="pagination"><button disabled={(page?.page??0)<=0} onClick={()=>{const next={...filters,page:(page?.page??0)-1};setFilters(next);void loadHistory(next)}}>이전</button><span>{(page?.page??0)+1} / {Math.max(1,page?.totalPages??1)} · {page?.totalElements??0}건</span><button disabled={(page?.page??0)+1>=(page?.totalPages??1)} onClick={()=>{const next={...filters,page:(page?.page??0)+1};setFilters(next);void loadHistory(next)}}>다음</button></div></Panel>
      <ErrorDetailPanel detail={detail} onUpdate={updateStatus}/></div>}
    {!loading&&tab==='analyze'&&<div className="analysis-layout"><Panel><form className="analysis-form" onSubmit={analyze}><label>분석 요청<textarea aria-label="오류 분석 요청" rows={5} value={analysisQuery} onChange={event=>setAnalysisQuery(event.target.value)} placeholder="최근 DB 오류를 분석해줘"/></label><button className="button button-primary" disabled={!analysisQuery.trim()}>오류 분석 실행</button></form><InfoNotice>분석 결과는 미검증 모델 출력이며 자동으로 Error History에 저장되지 않습니다.</InfoNotice></Panel>
      <Panel>{analysis?<><div className="panel-heading"><div><span className="eyebrow">UNVERIFIED ANALYSIS</span><h2>{analysis.errorType??'Detected error'}</h2></div><StatusBadge label="UNVERIFIED" tone="warn"/></div><p className="prose">{analysis.analysis}</p><dl className="detail-list"><div><dt>Message</dt><dd>{analysis.errorMessage}</dd></div><div><dt>Evidence</dt><dd>{analysis.evidenceStatus}</dd></div><div><dt>Files</dt><dd>{analysis.relatedFiles.join(', ')||'—'}</dd></div><div><dt>Commits</dt><dd>{analysis.relatedCommits.map(shortHash).join(', ')||'—'}</dd></div><div><dt>LLM</dt><dd>{formatDuration(analysis.llmDurationMillis)}</dd></div></dl><button className="button button-secondary" disabled={saved} onClick={saveAnalysis}>{saved?'UNVERIFIED로 저장됨':'History에 저장'}</button></>:<div className="inline-empty">분석을 실행하면 Evidence와 미검증 분석이 표시됩니다.</div>}</Panel>
      <SimilarResults result={similar}/></div>}
    {!loading&&tab==='similar'&&<><Panel><form className="similar-form" onSubmit={findSimilar}><label>Error message<input aria-label="유사 오류 메시지" value={similarMessage} onChange={event=>setSimilarMessage(event.target.value)} placeholder="Connection refused while connecting to PostgreSQL"/></label><button className="button button-primary" disabled={!similarMessage.trim()}>유사 오류 검색</button></form><InfoNotice>유사도는 텍스트·의미상 가까운 정도이며 같은 원인일 확률이 아닙니다.</InfoNotice></Panel><SimilarResults result={similar}/></>}
  </>
}

function ErrorDetailPanel({detail,onUpdate}:{detail:ErrorHistoryDetail|null;onUpdate:(status:ErrorStatus,note:string,cause:string,solution:string)=>Promise<void>}){
  const[note,setNote]=useState('');const[cause,setCause]=useState('');const[solution,setSolution]=useState('')
  if(!detail)return <Panel><div className="inline-empty">행을 선택하면 Evidence와 Verification Audit을 확인할 수 있습니다.</div></Panel>
  const history=detail.history;const next=history.status==='UNVERIFIED'?'VERIFIED':history.status==='VERIFIED'?'RESOLVED':null
  return <Panel className="detail-panel"><div className="panel-heading"><div><span className="eyebrow">HISTORY #{history.id}</span><h2>{history.errorType??'Error detail'}</h2></div><StatusBadge label={history.status} tone={statusTone(history.status)}/></div><p className="prose">{history.errorMessage}</p><dl className="detail-list"><div><dt>Root cause</dt><dd>{history.rootCause??'아직 검증되지 않음'}</dd></div><div><dt>Solution</dt><dd>{history.solution??'아직 확인되지 않음'}</dd></div><div><dt>Related files</dt><dd>{history.relatedFiles.join(', ')||'—'}</dd></div><div><dt>Related commits</dt><dd>{history.relatedCommits.map(shortHash).join(', ')||'—'}</dd></div></dl><details><summary>Evidence snapshot</summary><CodeBlock>{JSON.stringify(history.evidenceSummary,null,2)}</CodeBlock></details>
    <div className="audit"><span className="eyebrow">VERIFICATION AUDIT</span>{detail.verifications.map(item=><div key={item.id}><StatusBadge label={`${item.fromStatus} → ${item.toStatus}`} tone="info"/><strong>{item.verificationNote}</strong><small>{formatDate(item.changedAt)} · v{item.previousVersion}</small></div>)}{!detail.verifications.length&&<div className="inline-empty">아직 검증 이력이 없습니다.</div>}</div>
    {next&&<form className="verification-form" onSubmit={event=>{event.preventDefault();void onUpdate(next,note,cause,solution)}}><h3>{next==='VERIFIED'?'Verify':'Resolve'} this error</h3><label>확인한 Root cause<textarea value={cause} onChange={event=>setCause(event.target.value)} rows={2}/></label>{next==='RESOLVED'&&<label>확인한 Solution<textarea value={solution} onChange={event=>setSolution(event.target.value)} rows={2}/></label>}<label>Verification note<textarea value={note} onChange={event=>setNote(event.target.value)} rows={2}/></label><button className="button button-primary" disabled={!note.trim()||!cause.trim()||(next==='RESOLVED'&&!solution.trim())}>{next==='VERIFIED'?'검증 완료':'해결 완료'}</button></form>}
  </Panel>
}

function SimilarResults({result}:{result:SimilarErrorResponse|null}){
  return <Panel className="similar-results"><div className="panel-heading"><div><span className="eyebrow">SIMILAR HISTORY</span><h2>Past cases</h2></div>{result&&<span>{result.resultCount} matches · {formatDuration(result.totalDurationMillis)}</span>}</div>{result?.results.map(item=><article key={item.errorHistoryId}><div><StatusBadge label={item.status==='UNVERIFIED'?'미검증':item.status} tone={statusTone(item.status)}/><strong>유사도 {item.similarity.toFixed(2)}</strong></div><h3>{item.errorType??'Unknown error'}</h3><p>{item.errorMessage}</p>{item.rootCause&&<dl className="detail-list"><div><dt>Root cause</dt><dd>{item.rootCause}</dd></div>{item.solution&&<div><dt>Solution</dt><dd>{item.solution}</dd></div>}</dl>}</article>)}{!result&&<div className="inline-empty">분석 또는 검색 후 과거 유사 사례가 표시됩니다.</div>}{result&&result.results.length===0&&<div className="inline-empty">threshold {result.threshold} 이상인 유사 사례가 없습니다.</div>}{result?.caution&&<div className="notice notice-warn">{result.caution}</div>}</Panel>
}
