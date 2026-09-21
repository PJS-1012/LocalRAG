import { render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import AutomationPage from './AutomationPage'

vi.mock('../api/automationApi',()=>({automationApi:{get:vi.fn().mockResolvedValue({projectId:'P',enabled:true,progressSummaryEnabled:true,activitySummaryEnabled:true,errorWatchEnabled:true,environmentWatchEnabled:true,intervalSeconds:300,lastRunAt:null,nextRunAt:null,createdAt:'2026-09-01T00:00:00Z',updatedAt:'2026-09-01T00:00:00Z',version:0}),runs:vi.fn().mockResolvedValue({content:[],totalElements:0,totalPages:0,page:0,size:20}),save:vi.fn(),run:vi.fn(),notifications:vi.fn()}}))
it('loads persisted automation switches and interval',async()=>{
  render(<AutomationPage projectId="P" project={null} overview={null} refreshOverview={vi.fn()}/>)
  expect(await screen.findByText('사용 중')).toBeInTheDocument()
  expect(screen.getByLabelText('실행 간격')).toHaveValue(300)
  expect(screen.getByText('Progress Summary').closest('label')?.querySelector('input')).toBeChecked()
})
