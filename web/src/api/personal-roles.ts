import { authenticatedRequest } from '@/api/http'
import type { PersonalRoleSummary } from '@/types/personal-role'

/** 查询当前用户每个公共角色下的最新个人版本。 */
export function listPersonalRoles() {
  return authenticatedRequest<PersonalRoleSummary[]>('/personal-roles')
}
