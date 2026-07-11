import { authenticatedRequest } from '@/api/http'
import type { ChatMessage, ChatSessionSummary } from '@/types/chat'

interface ChatSessionCreateResponse { id: number }

/** 创建公共基线或个人版本绑定的聊天会话。 */
export async function createChatSession(characterId: number, userRoleVersionId?: number | null) {
  const sessionId = await authenticatedRequest<number>('/chat/sessions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ characterId, userRoleVersionId: userRoleVersionId ?? null }),
  })
  return { id: sessionId } satisfies ChatSessionCreateResponse
}

/** 查询当前用户最近聊天会话。 */
export function listChatSessions() { return authenticatedRequest<ChatSessionSummary[]>('/chat/sessions') }

/** 读取一个会话中已保存的完整消息。 */
export function listChatMessages(sessionId: string | number) {
  return authenticatedRequest<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
}

/** 发送同步消息，当前模型完成后返回完整角色回复。 */
export function sendChatMessage(sessionId: string | number, content: string) {
  return authenticatedRequest<{ messageId: number; content: string }>(`/chat/sessions/${sessionId}/messages`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }),
  })
}

/**
 * 预留 SSE 消息通道，未来后端改为 token 流时调用方无需更换 URL 或请求体。
 */
export async function streamChatMessage(
  sessionId: string | number,
  content: string,
  onEvent: (name: string, payload: string) => void,
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
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) return
    buffer += decoder.decode(chunk.value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() ?? ''
    events.forEach(event => {
      const name = event.match(/^event:\s*(.+)$/m)?.[1] ?? 'message'
      const payload = event.match(/^data:\s*(.+)$/m)?.[1]
      if (payload) onEvent(name, payload)
    })
  }
}
