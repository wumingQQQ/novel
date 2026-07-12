import type { RolePublicPreview } from '@/types/role'

/** 当前用户在一个公共角色下可直接开聊的最新个人版本摘要。 */
export interface PersonalRoleSummary {
  character: RolePublicPreview
  versionId: number
  versionNo: number
  sourceRequestId: number | null
  createTime: string | null
}
