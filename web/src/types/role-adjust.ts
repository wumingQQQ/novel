import type { RolePublicPreview } from '@/types/role'

export type RoleAdjustRequestStatus = 'PENDING' | 'GENERATING' | 'READY' | 'CONFIRMED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type RoleAdjustStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'REVISING'
export type RoleAdjustChangeType = 'ADD' | 'REPLACE' | 'DISABLE'

export interface RoleAdjustRequestSummary {
  id: number
  character: RolePublicPreview
  baseVersionId: number | null
  requirement: string
  status: RoleAdjustRequestStatus
  failureReason: string | null
  createdVersionId: number | null
  createTime: string | null
  updateTime: string | null
}

export interface RoleAdjustItem {
  id: number
  changeType: RoleAdjustChangeType
  adjustmentId: string | null
  targetAdjustmentId: string | null
  applicability: string
  expectedBehavior: string
  forbiddenBehavior: string
  status: RoleAdjustStatus
  revisionFeedback: string | null
  revisionError: string | null
  displayOrder: number
  passageIds: number[]
  createTime: string | null
  updateTime: string | null
}

export interface RoleAdjustRequestDetail {
  id: number
  characterId: number
  baseVersionId: number | null
  requirement: string
  chatText: string | null
  status: RoleAdjustRequestStatus
  failureReason: string | null
  createdVersionId: number | null
  createTime: string | null
  updateTime: string | null
  items: RoleAdjustItem[]
}

export interface CreateRoleAdjustInput {
  characterId: number
  requirement: string
  chatText?: string
  baseVersionId?: number | null
}

export interface ReviewRoleAdjustItemInput {
  itemId: number
  status: RoleAdjustStatus
  revisionFeedback?: string
}

export interface RoleAdjustReviewResult {
  requestId: number
  confirmed: boolean
  createdVersionId: number | null
  reviewedItemIds: number[]
  itemErrors: Array<{ itemId: number | null; message: string }>
}

export interface RoleAdjustEvidence { passageId: number; chapterId: number; startParagraph: number | null; endParagraph: number | null; content: string }
