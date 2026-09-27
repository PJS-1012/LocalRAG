// Final bounded verification: five requests, no retries, indexing or corpus changes.
import {mkdir,writeFile} from 'node:fs/promises'
import {resolve} from 'node:path'
const output=resolve(import.meta.dirname,'../../build/unified-quality/release-20260928')
await mkdir(output,{recursive:true})
const spring='Room_Reservation/RoomReservation'
const cases=[
 ['unity-broad','DungeonMerchant','이 프로젝트 처음 보는데 전체적으로 설명해줘'],
 ['missing-symbol','DungeonMerchant','ReservationLockService는 무슨 역할이야?'],
 ['spring-code',spring,'ReservationLockService는 무슨 역할이야?'],
 ['spring-broad',spring,'이 프로젝트 처음 보는데 전체적으로 설명해줘'],
 ['general',spring,'Java HashMap 설명해줘']
]
const summary=[]
for(const [id,projectId,query] of cases){
 console.log(JSON.stringify({starting:id}))
 const response=await fetch('http://127.0.0.1:18081/api/workspaces/projects/chat/unified',{
  method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({projectId,query}),signal:AbortSignal.timeout(90000)})
 const body=await response.json()
 await writeFile(resolve(output,id+'.json'),JSON.stringify({projectId,query,body},null,2),{flag:'wx'})
 if(!response.ok)throw new Error('HTTP '+response.status)
 const row={id,status:body.result.status,answer:body.result.answer,millis:body.result.totalDurationMillis,
  sources:body.result.knowledgeSourceCount,calls:body.diagnostics.llmCalls.map(c=>({finish:c.finishReason,success:c.success}))}
 summary.push(row)
 console.log(JSON.stringify(row))
}
await writeFile(resolve(output,'summary.json'),JSON.stringify(summary,null,2),{flag:'wx'})
