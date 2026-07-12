import { authenticatedRequest } from '@/api/http'
import type { CreateRoleAdjustInput, RoleAdjustRequestDetail, RoleAdjustRequestSummary, RoleAdjustReviewResult, ReviewRoleAdjustItemInput } from '@/types/role-adjust'

const basePath = '/role-adjust/requests'

/** 创建一项个人角色调整请求，候选项须由后续生成接口产生。 */
export function createRoleAdjustRequest(input: CreateRoleAdjustInput) {
  return authenticatedRequest<number>(basePath, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  })
}

/** 查询当前用户创建的调整请求摘要。 */
export function listRoleAdjustRequests() { return authenticatedRequest<RoleAdjustRequestSummary[]>(basePath) }

/** 查询一项调整请求及其候选项详情。 */
export function getRoleAdjustRequest(requestId: string | number) {
  return authenticatedRequest<RoleAdjustRequestDetail>(`${basePath}/${requestId}`)
}

/** 同步生成待用户评审的候选调整项。 */
export function generateRoleAdjustCandidates(requestId: string | number) {
  return authenticatedRequest<void>(`${basePath}/${requestId}/generation`, { method: 'POST' })
}

/** 提交本次选择的候选项评审状态。 */
export function reviewRoleAdjustItems(requestId: string | number, items: ReviewRoleAdjustItemInput[]) {
  return authenticatedRequest<RoleAdjustReviewResult>(`${basePath}/${requestId}/reviews`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ items }),
  })
}

/** 根据用户反馈重新生成处于 REVISING 状态的候选项。 */
export function reviseRoleAdjustItems(requestId: string | number) {
  return authenticatedRequest<void>(`${basePath}/${requestId}/revision`, { method: 'POST' })
}
