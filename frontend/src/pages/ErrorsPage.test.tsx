import { render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import ErrorsPage from './ErrorsPage'

vi.mock('../api/errorApi',()=>({errorApi:{list:vi.fn().mockResolvedValue({content:[{id:1,analysisId:'a',projectId:'P',occurredAt:'2026-09-01T00:00:00Z',recordedAt:'2026-09-01T00:00:00Z',errorType:'NullPointerException',errorMessage:'user was null',symptom:null,rootCause:null,solution:null,status:'UNVERIFIED',relatedFiles:['src/A.java'],relatedCommits:['abcdef123'],evidenceSummary:{},verificationNote:null,statusChangedAt:null,version:0}],totalElements:1,totalPages:1,page:0,size:20,queryDurationMillis:1}),detail:vi.fn(),analyze:vi.fn(),save:vi.fn(),updateStatus:vi.fn(),similar:vi.fn()}}))
it('renders project-scoped Error History with verification status',async()=>{
  render(<ErrorsPage projectId="P" project={null} overview={null} refreshOverview={vi.fn()}/>)
  expect(await screen.findByText('NullPointerException')).toBeInTheDocument()
  expect(screen.getByText('미검증')).toBeInTheDocument()
  expect(screen.getByText('src/A.java')).toBeInTheDocument()
})
