import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import RagPage from './RagPage'

vi.mock('../api/ragApi',()=>({ragApi:{ask:vi.fn().mockResolvedValue({projectId:'P',query:'Kafka?',answer:'',sourceCount:0,sources:[],contextCharacters:0,retrievalContextDurationMillis:2,llmDurationMillis:0,totalDurationMillis:2,status:'NO_EVIDENCE',usedSourceIds:[],invalidSourceIds:[],warnings:[]}),source:vi.fn()}}))
it('renders NO_EVIDENCE as a safe answer instead of a generic error',async()=>{
  render(<RagPage projectId="P" project={null} overview={null} refreshOverview={vi.fn()}/>)
  await userEvent.clear(screen.getByLabelText('RAG 질문'));await userEvent.type(screen.getByLabelText('RAG 질문'),'Kafka?');await userEvent.click(screen.getByRole('button',{name:'전송'}))
  expect(await screen.findByText('근거를 찾지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText(/추측하지 않았습니다/)).toBeInTheDocument()
})
