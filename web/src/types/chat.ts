export interface ChatSessionSummary {
  sessionId: number
  characterId: number
  userRoleVersionId: number | null
  updateTime: string | null
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  createTime: string | null
}
