import { authenticatedRequest } from '@/api/http'
import type { ChatMessage, ChatSessionSummary, CreateChatSessionResponse } from '@/types/chat'

/** 创建公共基线或个人版本绑定的聊天会话。 */
export async function createChatSession(characterId: number, userRoleVersionId?: number | null) {
  return authenticatedRequest<CreateChatSessionResponse>('/chat/sessions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ characterId, userRoleVersionId: userRoleVersionId ?? null }),
  })
}

/** 查询当前用户最近聊天会话。 */
export function listChatSessions() { return authenticatedRequest<ChatSessionSummary[]>('/chat/sessions') }

/** 读取一个会话中已保存的完整消息。 */
export function listChatMessages(sessionId: string | number) {
  return authenticatedRequest<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
}

export type ChatStreamEvent =
  | { name: 'delta'; payload: string }
  | { name: 'complete'; payload: string }
  | { name: 'error'; payload: string }

/** 发送消息并逐段读取角色回复；仅 delta、complete、error 是公开事件。 */
export async function streamChatMessage(
  sessionId: string | number,
  content: string,
  onEvent: (event: ChatStreamEvent) => void,
) {
  const token = localStorage.getItem('access_token')?.trim()
  if (!token) throw new Error('请先登录后再发送消息')
  const response = await fetch(`/api/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ content }),
  })
  if (!response.ok || !response.body) throw new Error('流式消息通道暂时不可用')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const dispatch = (block: string) => {
    const lines = block.split(/\r?\n/)
    const name = lines.find(line => line.startsWith('event:'))?.slice('event:'.length).trim()
    const payload = lines.filter(line => line.startsWith('data:'))
      .map(line => line.slice('data:'.length).trimStart()).join('\n')
    if (!name || !payload || !['delta', 'complete', 'error'].includes(name)) return
    onEvent({ name: name as ChatStreamEvent['name'], payload })
  }
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) {
      buffer += decoder.decode()
      if (buffer.trim()) dispatch(buffer)
      return
    }
    buffer += decoder.decode(chunk.value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() ?? ''
    events.forEach(dispatch)
  }
}
