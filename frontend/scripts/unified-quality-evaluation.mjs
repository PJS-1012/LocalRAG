// Explicit live evaluation. Sequential requests; no indexing, mutation or automatic retries.
import {mkdir,writeFile,access} from 'node:fs/promises'
import {resolve} from 'node:path'
const stage=process.argv[2]
if(!['before','after'].includes(stage))throw new Error('Usage: node unified-quality-evaluation.mjs before|after [run-name]')
const name=process.argv[3]??stage
if(!/^[a-zA-Z0-9_-]+$/.test(name))throw new Error('Invalid run name')
const output=resolve(import.meta.dirname,'../../build/unified-quality',name)
try{await access(resolve(output,'summary.json'));throw new Error('Run already exists; choose a new name, never overwrite evidence')}
catch(error){if(error.code!=='ENOENT')throw error}
await mkdir(output,{recursive:true})
const base=process.env.LOCALRAG_EVAL_BASE??'http://127.0.0.1:18081'
const questions=[
'이 프로젝트 처음 보는데 전체적으로 설명해줘',
'이 프로젝트 전체 구조 알려줘',
'주요 기능이 뭐야?',
'현재 어디까지 구현됐어?',
'최근 작업이랑 남은 문제 알려줘',
'이 프로젝트 인수인계 받았다고 생각하고 설명해줘',
'처음 보면 어떤 파일부터 보면 돼?',
'회원가입은 어떤 식으로 이루어지나?',
'ReservationLockService는 무슨 역할이야?',
'현재 Git 상태 알려줘',
'DB 지금 정상적으로 떠 있어?',
'Java HashMap 설명해줘',
'이거 뭐하는 프로젝트임?',
'처음 왔는데 뭐부터 보면 됨?',
'대충 어디까지 만들어짐?',
'전체 흐름 좀 알려줘',
'TextMesh Pro는 어떻게 사용해?',
'현재 어느 상태에서 정체되어 있어?',
'이 프로젝트 실행하려면 뭐가 필요해?',
'이 프로젝트에서 TextMesh Pro가 어떻게 사용되는지 근거를 보여줘']
const projects=[
 {key:'spring',id:'Room_Reservation/RoomReservation',before:[1,6,8,9]},
 {key:'unity',id:'DungeonMerchant',before:[1,6,7]},
]
const summary=[]
function countCitations(result,evidence){
 const available=new Set([...(result.knowledgeSources??[]).map(s=>s.id),...(result.toolsUsed??[])])
 function visit(value){
  if(!value||typeof value!=='object')return
  if(value.id&&value.sourceType)available.add(value.id)
  Object.values(value).forEach(visit)
 }
 evidence.forEach(visit)
 return new Set([...String(result.answer??'').matchAll(/\[([^\]]+)\]/g)]
  .flatMap(m=>m[1].split(/[,\s]+/)).filter(id=>available.has(id))).size
}
function origin(path=''){
 const p=path.replaceAll('\\','/').toLowerCase()
 if(/(^|\/)(library|temp|build|target|dist|generated|obj|bin)(\/|$)|^performance\/.*\/results\//.test(p))return 'GENERATED'
 if(/textmesh pro|packagecache|node_modules|(^|\/)(vendor|third.party|plugins)(\/|$)|license|attribution|emoji|ofl\.txt/.test(p))return 'THIRD_PARTY'
 if(/\.md$|(^|\/)(docs|documentation)\//.test(p))return 'DOCUMENTATION'
 return 'PROJECT'
}
await writeFile(resolve(output,'manifest.json'),JSON.stringify({stage,createdAt:new Date().toISOString(),base,projects,questions,
 model:'qwen3:8b',note:'No model/threshold/corpus mutation. before uses a matched representative subset; after runs full set. HTTP timeouts stop the batch.'},null,2))
for(const project of projects)for(const [index,query]of questions.entries()){
 const id=index+1
 if(stage==='before'&&!project.before.includes(id))continue
 if(stage==='after'&&[17,20].includes(id)&&project.key!=='unity')continue
 const key=project.key+'-'+String(id).padStart(2,'0')
 console.log(JSON.stringify({starting:key,query}))
 const started=performance.now()
 try{
  const response=await fetch(base+'/api/workspaces/projects/chat/unified',{method:'POST',
   headers:{'Content-Type':'application/json'},body:JSON.stringify({projectId:project.id,query}),
   signal:AbortSignal.timeout(120000)})
  const body=await response.json()
  const r=body.result??{},d=body.diagnostics
  await writeFile(resolve(output,key+'.json'),JSON.stringify({query,projectId:project.id,httpStatus:response.status,wallMs:Math.round(performance.now()-started),body},null,2))
  if(!response.ok||!d)throw new Error('Response failed or real-call diagnostics missing')
  const sources=r.knowledgeSources??[]
  const classified=sources.map(s=>({path:s.filePath,origin:s.sourceOrigin??origin(s.filePath)}))
  const finalCalls=d.llmCalls.filter(c=>c.purpose==='FINAL_ANSWER')
  const row={key,query,projectId:project.id,status:r.status,
   intent:d.intent??'MODEL_TOOL_SELECTION_NOT_SEPARATELY_CLASSIFIED',route:body.routes,toolsUsed:r.toolsUsed,
   evidenceCount:sources.length+(body.evidence??[]).filter(e=>e.toolName!=='searchProjectKnowledge').length,
   fileEvidenceCount:sources.length,projectEvidenceCount:classified.filter(s=>s.origin==='PROJECT').length,
   documentationEvidenceCount:classified.filter(s=>s.origin==='DOCUMENTATION').length,
   thirdPartyEvidenceCount:classified.filter(s=>s.origin==='THIRD_PARTY').length,
   generatedEvidenceCount:classified.filter(s=>s.origin==='GENERATED').length,originBasis:'API_SOURCE_ORIGIN_WITH_PATH_FALLBACK',
   knowledgeCitationCount:(r.usedSourceIds??[]).length,
   citationCount:countCitations(r,body.evidence??[]),llmCallCount:d.llmCallCount,
   evidenceCollectionMs:d.evidenceCollectionMs,llmMs:d.llmMs,totalMs:d.totalMs,
   finalInputChars:finalCalls.map(c=>c.inputContextChars),finalOutputChars:finalCalls.map(c=>c.outputChars),
   warnings:r.warnings,sources:classified}
  summary.push(row)
  console.log(JSON.stringify(row))
  await writeFile(resolve(output,'summary.json'),JSON.stringify(summary,null,2))
 }catch(error){
  summary.push({key,query,projectId:project.id,error:String(error),wallMs:Math.round(performance.now()-started)})
  await writeFile(resolve(output,'summary.json'),JSON.stringify(summary,null,2))
  console.error(String(error))
  process.exitCode=1
  // A timeout does not prove server inference stopped. Never pile another request onto it.
  process.exit()
 }
}
