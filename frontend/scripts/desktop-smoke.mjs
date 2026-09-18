// Developer-only production WebView smoke harness; never included in the app runtime.
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const mode = process.argv[2] ?? 'startup'
const output = resolve(import.meta.dirname, '../../build/desktop-qa')
await mkdir(output, { recursive: true })
const targets = await fetch('http://127.0.0.1:9223/json/list').then(r => r.json())
const target = targets.find(t => t.url === 'http://tauri.localhost/')
if (!target) throw new Error('Production LocalRAG WebView target not found')
const ws = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once:true }); ws.addEventListener('error', reject, { once:true }) })
let sequence = 0
const pending = new Map(), errors = []
ws.addEventListener('message', event => {
  const message = JSON.parse(String(event.data))
  if (message.id && pending.has(message.id)) {
    const { resolve, reject, timer } = pending.get(message.id); clearTimeout(timer); pending.delete(message.id)
    message.error ? reject(new Error(JSON.stringify(message.error))) : resolve(message.result)
  }
  if (message.method === 'Runtime.exceptionThrown' || (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error')) errors.push(message)
})
function call(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: '+method)) }, 15000)
    pending.set(id, { resolve, reject, timer }); ws.send(JSON.stringify({ id, method, params }))
  })
}
async function evaluate(expression) {
  const r = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails))
  return r.result.value
}
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function screenshot(name) {
  const r = await call('Page.captureScreenshot', { format: 'png' })
  await writeFile(resolve(output,name+'.png'), Buffer.from(r.data,'base64'))
}
await call('Runtime.enable'); await call('Page.enable')
await call('Emulation.setDeviceMetricsOverride', { width:1440, height:1000, deviceScaleFactor:1, mobile:false })
try {
  if (mode === 'startup') {
    let last = '', result
    for (let i=0; i<255; i++) {
      result = await evaluate("window.__TAURI_INTERNALS__.invoke('desktop_backend_status')")
      const status = JSON.stringify(result)
      if (status !== last) { console.log(status); last=status; await screenshot('startup') }
      if (result.state !== 'STARTING') break
      await delay(2000)
    }
    await writeFile(resolve(output,'startup.json'),JSON.stringify(result,null,2))
    if (result?.state !== 'READY') throw new Error('ONE_CLICK_STARTUP failed')
    console.log('ONE_CLICK_STARTUP=PASS')
  } else if (mode === 'dashboard') {
    await delay(1000)
    console.log(await evaluate("({text:document.body.innerText, buttons:[...document.querySelectorAll('button')].map(b=>b.textContent),overflow:document.documentElement.scrollWidth-innerWidth})"))
    await screenshot('dashboard')
    const metrics = await evaluate("({width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth-innerWidth,projectRows:document.querySelectorAll('.project-row').length,scrollHeight:document.querySelector('.project-scroll')?.scrollHeight,clientHeight:document.querySelector('.project-scroll')?.clientHeight})")
    console.log(metrics)
    await writeFile(resolve(output,'dashboard.json'),JSON.stringify({metrics,errors},null,2))
  } else if (mode === 'detail') {
    await evaluate("document.querySelectorAll('.project-row').forEach(b=>{if(b.querySelector('strong')?.textContent==='Local_Ai_Work')b.click()})")
    await delay(500)
    await evaluate("document.querySelector('.project-detail').scrollIntoView({block:'start'})")
    await screenshot('project-detail')
    console.log(await evaluate("({text:document.querySelector('.project-detail')?.innerText,overflow:document.documentElement.scrollWidth-innerWidth,commits:[...document.querySelectorAll('.project-detail .commit-list code')].map(c=>({text:c.textContent,size:getComputedStyle(c).fontSize}))})"))
  } else if (mode === 'smoke') {
    await evaluate("(()=>{const e=document.querySelector('#project-select');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,'Local_Ai_Work');e.dispatchEvent(new Event('change',{bubbles:true}))})()")
    await delay(500)
    const results=[]
    for (const test of [{nav:'Chat / Knowledge',label:'RAG 질문',query:'Project 타입을 어떻게 탐지해?',button:'전송',name:'rag'},
                        {nav:'Agent',label:'Agent 질문',query:'현재 Git 상태 알려줘',button:'Agent 실행',name:'agent'}]) {
      await evaluate(`[...document.querySelectorAll('nav button')].find(b=>b.textContent.includes(${JSON.stringify(test.nav)})).click()`)
      await delay(200)
      await evaluate(`(()=>{const e=document.querySelector('textarea[aria-label="${test.label}"]');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,${JSON.stringify(test.query)});e.dispatchEvent(new Event('input',{bubbles:true}))})()`)
      await delay(100)
      await evaluate(`[...document.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(test.button)}).click()`)
      let answer=''
      for(let i=0;i<140;i++){await delay(1000);answer=await evaluate("document.querySelector('.message-assistant')?.innerText ?? document.querySelector('.notice-error')?.innerText ?? ''");if(answer)break}
      const result={name:test.name,answer,trace:await evaluate("document.querySelector('.tool-trace')?.innerText ?? document.querySelector('.source-count')?.innerText"),overflow:await evaluate("document.documentElement.scrollWidth-innerWidth")}
      results.push(result); console.log(result); await screenshot(test.name)
    }
    await writeFile(resolve(output,'smoke.json'),JSON.stringify({results,errors},null,2))
    if(results.some(r=>!r.answer||!r.answer.includes('SUCCESS')))throw new Error('Smoke response requires review')
  } else if (mode === 'checks') {
    for(let i=0;i<40;i++){if(await evaluate("document.querySelectorAll('.project-row').length>0"))break;await delay(500)}
    await evaluate("[...document.querySelectorAll('nav button')].find(b=>b.textContent.includes('Dashboard')).click()")
    await delay(500)
    const scroll = await evaluate("(()=>{const e=document.querySelector('.project-scroll');e.scrollTop=e.scrollHeight;return {top:e.scrollTop,height:e.clientHeight,total:e.scrollHeight}})()")
    await evaluate("[...document.querySelectorAll('.project-row')].find(b=>b.querySelector('strong')?.textContent==='room-reservation-front').click()")
    await delay(700)
    const pushed = await evaluate("document.querySelector('.project-detail')?.innerText")
    await screenshot('pushed-project')
    if (!pushed?.includes('PUSHED') || scroll.top <= 0) throw new Error('Project checks failed')
    await evaluate("[...document.querySelectorAll('nav button')].find(b=>b.textContent.includes('Settings')).click()")
    await delay(300)
    const before = await evaluate("window.__TAURI_INTERNALS__.invoke('desktop_backend_status')")
    await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='Retry Startup').click()")
    let after
    for(let i=0;i<20;i++){await delay(1000);after=await evaluate("window.__TAURI_INTERNALS__.invoke('desktop_backend_status')");if(after.state!=='STARTING')break}
    if(after.state!=='READY'||before.pid!==after.pid)throw new Error('Reuse check failed')
    const result={scroll,pushed,before,after,errors}
    await writeFile(resolve(output,'checks.json'),JSON.stringify(result,null,2))
    console.log(result)
    await evaluate("[...document.querySelectorAll('nav button')].find(b=>b.textContent.includes('Dashboard')).click()")
    await delay(300)
    await evaluate("window.scrollTo(0,0)")
  } else if (mode === 'retry') {
    await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='Retry Startup').click()")
    await delay(1000)
    console.log(await evaluate("window.__TAURI_INTERNALS__.invoke('desktop_backend_status')"))
  }
  console.log('Console errors:',errors.length)
} finally { ws.close() }
