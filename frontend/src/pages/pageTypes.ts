import type { DetectedProject, ProjectOverview } from '../types'
export interface ProjectPageProps {
  projectId: string
  project: DetectedProject | null
  overview: ProjectOverview | null
  refreshOverview: () => Promise<void>
}
