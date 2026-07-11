import { authenticatedRequest } from '@/api/http'
import type { EvaluationCase, EvaluationRun } from '@/types/workspace'

export interface RoleEvaluation { id: number; characterId: number; userRoleVersionId: number | null; createTime: string | null }

/** 为公共角色创建当前用户独立的评测记录。 */
export function createRoleEvaluation(characterId: number) {
  return authenticatedRequest<RoleEvaluation>('/role-evaluations', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ characterId }),
  })
}

/** 查询一轮评测已经生成的案例。 */
export function listEvaluationCases(evaluationId: number) {
  return authenticatedRequest<EvaluationCase[]>(`/role-evaluations/${evaluationId}/cases`)
}

/** 根据受保护的角色资产生成待人工审核的案例。 */
export function generateEvaluationCases(evaluationId: number, datasetVersion: string, limit: number) {
  return authenticatedRequest<EvaluationCase[]>(`/role-evaluations/${evaluationId}/cases`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ datasetVersion, limit }),
  })
}

/** 审核一个案例，使其可以执行角色评测。 */
export function approveEvaluationCase(evaluationId: number, caseId: number) {
  return authenticatedRequest<EvaluationCase>(`/role-evaluations/${evaluationId}/cases/${caseId}/approve`, { method: 'POST' })
}

/** 拒绝不适合作为基线的案例。 */
export function rejectEvaluationCase(evaluationId: number, caseId: number) {
  return authenticatedRequest<EvaluationCase>(`/role-evaluations/${evaluationId}/cases/${caseId}/reject`, { method: 'POST' })
}

/** 执行已审核案例并保存 Judge 评分。 */
export function runEvaluationCase(evaluationId: number, caseId: number) {
  return authenticatedRequest<EvaluationRun>(`/role-evaluations/${evaluationId}/cases/${caseId}/runs`, { method: 'POST' })
}

/** 查询一个案例的历史运行记录。 */
export function listEvaluationRuns(evaluationId: number, caseId: number) {
  return authenticatedRequest<EvaluationRun[]>(`/role-evaluations/${evaluationId}/cases/${caseId}/runs`)
}
