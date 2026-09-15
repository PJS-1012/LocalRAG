import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import App from './App'

const projects=[
  {name:'Local_Ai_Work',projectId:'Local_Ai_Work',rootPath:'C:/workspace/Local_Ai_Work',projectType:'JAVA',detectedFramework:'Spring Boot',gitRepository:true,enabled:true,detectionHints:[]},
  {name:'client',projectId:'Room/client',rootPath:'C:/workspace/Room/client',projectType:'JAVASCRIPT',detectedFramework:'React',gitRepository:true,enabled:true,detectionHints:[]},
]
beforeEach(()=>{localStorage.clear();vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
  const url=String(input)
  if(url.includes('/discovery'))return new Response(JSON.stringify({workspaceRoot:'C:/workspace',projects,containers:[]}))
  if(url.includes('/overview'))return new Response(JSON.stringify({project:projects[url.includes('Room%2Fclient')?1:0],index:null,git:null,recentCommits:null,docker:null,projectContainers:null,ollama:null,database:null,errors:null,latestAutomation:null,notificationCandidateCount:0,unacknowledgedNotificationCount:0,warnings:[],collectedAt:new Date().toISOString(),durationMillis:1}))
  return new Response('{}')
}))
})

it('loads Projects and changes the global Project selection',async()=>{
  render(<App/>);const picker=await screen.findByLabelText('Project 선택')
  expect(picker).toHaveValue('Local_Ai_Work')
  await userEvent.selectOptions(picker,'Room/client')
  await waitFor(()=>expect(picker).toHaveValue('Room/client'))
  expect(localStorage.getItem('localrag.project')).toBe('Room/client')
})
