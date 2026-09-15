import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import AgentPage from './AgentPage'

vi.mock('../api/agentApi',()=>({agentApi:{ask:vi.fn().mockResolvedValue({projectId:'P',query:'Git?',answer:'현재 branch는 main입니다.',toolsUsed:['getGitStatus'],toolExecutionDurationMillis:10,llmDurationMillis:20,totalDurationMillis:30,status:'SUCCESS',warnings:[],toolCalls:[{sequence:1,toolName:'getGitStatus',durationMillis:10,outcome:'SUCCESS',successful:true,sameArgumentsAs:null}],knowledgeSourceCount:0,knowledgeSources:[]})}}))
it('shows the Agent answer and read-only Tool trace',async()=>{
  render(<AgentPage projectId="P" project={null} overview={null} refreshOverview={vi.fn()}/>)
  await userEvent.clear(screen.getByLabelText('Agent 질문'));await userEvent.type(screen.getByLabelText('Agent 질문'),'Git?');await userEvent.click(screen.getByRole('button',{name:'Agent 실행'}))
  expect(await screen.findByText('현재 branch는 main입니다.')).toBeInTheDocument()
  expect(screen.getByText('getGitStatus')).toBeInTheDocument()
})
