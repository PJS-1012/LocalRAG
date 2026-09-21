// Developer-only bounded live evaluation. No indexing, writes to application data, or tuning.
import {mkdir,writeFile,readFile} from 'node:fs/promises'
import {resolve} from 'node:path'
const output=resolve(import.meta.dirname,'../../build/unified-evaluation')
await mkdir(output,{recursive:true})
const base='http://127.0.0.1:18080'
const projectId='Room_Reservation/RoomReservation'
const questions=[
'이 프로젝트의 주요 구조와 현재 구현 상태를 설명해줘',
'이 프로젝트 처음 보는데 전체적으로 설명해줘',
'이 프로젝트 인수인계 받았다고 생각하고 설명해줘',
'주요 기능이 뭐야?',
'현재 어디까지 구현됐어?',
'최근 작업이랑 남은 문제 알려줘',
'처음 보면 어떤 파일부터 보면 돼?',
'ReservationLockService 어디 있어?',
'현재 Git 상태 알려줘',
'왜 DB 오류 난 것 같아?',
'Java HashMap 설명해줘',
'이거 뭐하는 프로젝트임?',
'처음 왔는데 뭐부터 보면 됨?',
'대충 어디까지 만들어짐?',
'전체 흐름 좀 알려줘']
const startIndex=Number(process.argv[2]??1)
const summaries=startIndex>1?JSON.parse(await readFile(resolve(output,'summary.json'),'utf8')):[]
for(let i=0;i<2;i++){
 const start=performance.now()
 const response=await fetch(base+'/api/workspaces/overview',{signal:AbortSignal.timeout(60000)})
 const value=await response.json()
 await writeFile(resolve(output,'overview-'+i+'.json'),JSON.stringify({wallMs:Math.round(performance.now()-start),value},null,2))
 console.log(JSON.stringify({overview:i,projects:value.projects?.length,wallMs:Math.round(performance.now()-start),metadataMs:value.durationMillis}))
}
for(const [i,query]of questions.entries()){
 if(i+1<startIndex)continue
 const started=performance.now()
 console.log(JSON.stringify({starting:i+1,query}))
 try{
  const response=await fetch(base+'/api/workspaces/projects/chat/unified',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({projectId,query}),signal:AbortSignal.timeout(120000)})
  const body=await response.json()
  await writeFile(resolve(output,'query-'+String(i+1).padStart(2,'0')+'.json'),JSON.stringify({query,httpStatus:response.status,wallMs:Math.round(performance.now()-started),body},null,2))
  const r=body.result??{}
  const summary={id:i+1,query,httpStatus:response.status,status:r.status,tools:r.toolsUsed,sources:r.knowledgeSourceCount,toolsMs:r.toolExecutionDurationMillis,llmMs:r.llmDurationMillis,totalMs:r.totalDurationMillis,warnings:r.warnings}
  summaries.push(summary);console.log(JSON.stringify(summary))
 }catch(error){
  const failure={id:i+1,query,error:String(error),wallMs:Math.round(performance.now()-started)}
  summaries.push(failure);console.log(JSON.stringify(failure))
  await writeFile(resolve(output,'query-'+String(i+1).padStart(2,'0')+'.json'),JSON.stringify(failure,null,2))
  // Do not pile more inference requests onto a timed-out server.
  await writeFile(resolve(output,'summary.json'),JSON.stringify(summaries,null,2));process.exitCode=1;break
 }
 await writeFile(resolve(output,'summary.json'),JSON.stringify(summaries,null,2))
}
