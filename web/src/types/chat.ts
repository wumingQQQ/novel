export interface ChatSessionSummary {
  sessionId: number
  characterId: number
  userRoleVersionId: number | null
  updateTime: string | null
}

/** 创建会话后确认其绑定的公共角色或个人角色版本。 */
export interface CreateChatSessionResponse {
  sessionId: number
  characterId: number
  userRoleVersionId: number | null
  personalVersionBound: boolean
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  createTime: string | null
}
