import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react'
import { afterEach,beforeEach,expect,it,vi } from 'vitest'
import UnifiedChatPage from './UnifiedChatPage'
import { apiRequest } from '../api/client'
vi.mock('../api/client',()=>({apiRequest:vi.fn()}))
afterEach(cleanup)
beforeEach(()=>vi.resetAllMocks())
const props={projectId:'A/P',project:null,overview:null,refreshOverview:vi.fn()}
const response={result:{projectId:'A/P',query:'Git 상태',answer:'getGitStatus: 변경 없음.',status:'SUCCESS',toolsUsed:['getGitStatus'],toolCalls:[],warnings:[],knowledgeSources:[],knowledgeSourceCount:0,totalDurationMillis:10,toolExecutionDurationMillis:2,llmDurationMillis:8},routing:'EXISTING_AGENT_TOOL_SELECTION',routes:['getGitStatus'],evidence:[]}
it('sends via unified endpoint on Enter and displays a tool answer with zero RAG sources',async()=>{
 vi.mocked(apiRequest).mockResolvedValue(response);render(<UnifiedChatPage {...props}/>)
 const input=screen.getByLabelText('채팅 질문');fireEvent.change(input,{target:{value:'Git 상태'}})
 fireEvent.keyDown(input,{key:'Enter'});await screen.findByText('getGitStatus: 변경 없음.')
 expect(apiRequest).toHaveBeenCalledWith('/api/workspaces/projects/chat/unified',{method:'POST',body:JSON.stringify({projectId:'A/P',query:'Git 상태'})})
 expect(apiRequest).toHaveBeenCalledTimes(1)
 expect(screen.queryByText('근거 부족')).not.toBeInTheDocument()
})
it('does not submit composition, Shift+Enter or duplicate in-flight requests',async()=>{
 vi.mocked(apiRequest).mockImplementation(()=>new Promise(()=>{}));render(<UnifiedChatPage {...props}/>)
 const input=screen.getByLabelText('채팅 질문');fireEvent.change(input,{target:{value:'설명'}})
 fireEvent.keyDown(input,{key:'Enter',isComposing:true});fireEvent.keyDown(input,{key:'Enter',shiftKey:true})
 expect(apiRequest).not.toHaveBeenCalled()
 fireEvent.keyDown(input,{key:'Enter'});fireEvent.submit(input.closest('form')!)
 await waitFor(()=>expect(apiRequest).toHaveBeenCalledTimes(1))
})
it('retains source citation links and tool evidence details',async()=>{
 vi.mocked(apiRequest).mockResolvedValue({...response,result:{...response.result,answer:'예약 서비스 [K1-S1]',knowledgeSourceCount:1,knowledgeSources:[{id:'K1-S1',filePath:'src/Reservation.java',startLine:1,endLine:3}]},evidence:[{sequence:1,toolName:'searchProjectKnowledge',result:{status:'SUCCESS'}}]})
 render(<UnifiedChatPage {...props}/>);fireEvent.change(screen.getByLabelText('채팅 질문'),{target:{value:'설명'}});fireEvent.click(screen.getByText('전송'))
 expect(await screen.findByRole('link',{name:'[K1-S1]'})).toHaveAttribute('href','#source-K1-S1')
 expect(screen.getByText('src/Reservation.java')).toBeInTheDocument()
 expect(screen.getByText('searchProjectKnowledge 근거 상세')).toBeInTheDocument()
})
