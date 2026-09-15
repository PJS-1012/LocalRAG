import { useEffect, useState } from 'react'
import { automationApi } from '../api/automationApi'
import { ApiError } from '../api/client'
import { EmptyState, ErrorNotice, LoadingState, PageHeader, Panel, StatusBadge } from '../components/ui'
import { formatDate, statusTone } from '../lib/format'
import type { NotificationCandidate, Page } from '../types'
import type { ProjectPageProps } from './pageTypes'

const ICONS:Record<string,string>={ERROR_DETECTED:'!',ENVIRONMENT_FAILURE:'◇',PROJECT_CHANGED:'↗',PROGRESS_UPDATED:'✓'}
export default function NotificationsPage({projectId,project}:ProjectPageProps){
  const[result,setResult]=useState<Page<NotificationCandidate>|null>(null);const[loading,setLoading]=useState(false);const[error,setError]=useState<string|null>(null)
  const load=async()=>{if(!projectId)return;setLoading(true);setError(null);try{setResult(await automationApi.notifications(projectId))}catch(reason){setError(reason instanceof ApiError?reason.message:'알림 후보를 불러오지 못했습니다.')}finally{setLoading(false)}}
  useEffect(()=>{setResult(null);if(projectId)void load()},[projectId])
  if(!projectId)return <EmptyState title="Project가 필요합니다" description="Notification Candidate는 Project별로 조회합니다."/>
  return <><PageHeader eyebrow="NOTIFICATION CANDIDATES" title="Attention queue" description={`${project?.name??projectId}에서 확인이 필요한 자동화 결과입니다.`} action={<button className="button button-secondary" onClick={load} disabled={loading}>새로고침</button>}/>{error&&<ErrorNotice message={error}/>} {loading&&<LoadingState label="알림 후보 불러오는 중"/>}<Panel>{result?.content.length?<div className="notification-list">{result.content.map(item=><article key={item.id}><div className={`notification-icon notification-${statusTone(item.notificationType.includes('FAILURE')?'FAILED':'SUCCESS')}`}>{ICONS[item.notificationType]??'·'}</div><div><div><StatusBadge label={item.notificationType} tone={item.notificationType.includes('ERROR')||item.notificationType.includes('FAILURE')?'bad':'info'}/><span>{formatDate(item.createdAt)}</span></div><h2>{item.title}</h2><p>{item.summary}</p></div>{!item.acknowledgedAt&&<span className="unread-mark">NEW</span>}</article>)}</div>:!loading&&<div className="analysis-placeholder"><span>◇</span><h2>새로운 알림 후보가 없습니다.</h2><p>OS Toast나 이메일은 아직 보내지 않으며 이 화면에서만 조회합니다.</p></div>}</Panel></>
}
