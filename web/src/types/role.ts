export interface RolePublicSummary {
  id: number
  characterName: string
  novelName: string
  introduction: string
  ruleCount: number
  exampleCount: number
  completedTime: string | null
}

export interface RolePublicPreview extends RolePublicSummary {}

export interface RolePublicPageResponse {
  items: RolePublicSummary[]
  total: number
  page: number
  size: number
}
