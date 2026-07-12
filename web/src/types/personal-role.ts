import type { RolePublicPreview } from '@/types/role'

/** 当前用户在一个公共角色下可直接开聊的最新个人版本摘要。 */
export interface PersonalRoleSummary {
  character: RolePublicPreview
  versionId: number
  versionNo: number
  sourceRequestId: number | null
  createTime: string | null
}

/** 可绑定到聊天会话的个人角色版本，按版本号由新到旧展示。 */
export interface PersonalRoleVersion {
  versionId: number
  characterId: number
  versionNo: number
  parentVersionId: number | null
  sourceRequestId: number | null
  latest: boolean
  createTime: string | null
  behaviorAdjustments: PersonalRoleBehaviorAdjustment[]
}

/** 个人版本中生效的单条行为调整摘要。 */
export interface PersonalRoleBehaviorAdjustment {
  adjustmentId: string
  sourceAdjustItemId: number | null
  applicability: string
  expectedBehavior: string
  forbiddenBehavior: string
  displayOrder: number | null
}
