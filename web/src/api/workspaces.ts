import { authenticatedRequest } from '@/api/http'
import type { RoleWorkspaceDetailResponse, RoleWorkspaceSummary } from '@/types/workspace'

/** 查询当前用户参与过评测的角色工作区。 */
export function listRoleWorkspaces() {
  return authenticatedRequest<RoleWorkspaceSummary[]>('/role-workspaces')
}

/** 查询当前用户在单个公共角色下的评测工作区。 */
export function getRoleWorkspace(characterId: string | number) {
  return authenticatedRequest<RoleWorkspaceDetailResponse>(`/role-workspaces/${characterId}`)
}
