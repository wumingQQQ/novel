import type { RolePublicPreview } from '@/types/role'

export interface RoleWorkspaceSummary {
  character: RolePublicPreview
  evaluationCount: number
  latestEvaluationId: number
  userRoleTrackId: number | null
  latestUserRoleVersionId: number | null
  latestVersionNo: number | null
  latestEvaluationTime: string | null
}

export interface RoleWorkspaceEvaluationSummary {
  evaluationId: number
  userRoleVersionId: number | null
  caseCount: number
  approvedCaseCount: number
  succeededRunCount: number
  draftImprovementBatchCount: number
  latestScore: number | null
  createTime: string | null
}

export interface RoleWorkspaceDetailResponse {
  workspace: RoleWorkspaceSummary
  evaluations: RoleWorkspaceEvaluationSummary[]
}

export interface EvaluationCase {
  id: number
  datasetVersion: string
  testInput: string
  expectedBehaviors: string | null
  scoringRubric: string | null
  difficulty: string | null
  status: 'DRAFT' | 'APPROVED' | 'REJECTED'
  createTime: string | null
}

export interface EvaluationRun {
  id: number
  caseId: number
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'INVALID' | 'FAILED'
  totalScore: number | null
  judgeReason: string | null
  failureReason: string | null
  createTime: string | null
}
