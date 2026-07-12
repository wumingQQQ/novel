import { authenticatedRequest } from '@/api/http'
import type { PersonalRoleSummary, PersonalRoleVersion } from '@/types/personal-role'

/** 查询当前用户每个公共角色下的最新个人版本。 */
export function listPersonalRoles() {
  return authenticatedRequest<PersonalRoleSummary[]>('/personal-roles')
}

/** 查询当前用户在指定公共角色下可选择对话的全部个人版本。 */
export function listPersonalRoleVersions(characterId: number) {
  return authenticatedRequest<PersonalRoleVersion[]>(`/personal-roles/characters/${characterId}/versions`)
}
