<script setup lang="ts">
import { ArrowLeft, Plus, Promotion } from '@element-plus/icons-vue'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listChatMessages, listChatSessions, streamChatMessage } from '@/api/chat'
import { getPublicRolePreview } from '@/api/roles'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import type { ChatMessage, ChatSessionSummary } from '@/types/chat'
import type { RolePublicPreview } from '@/types/role'

const props = defineProps<{ sessionId: string }>()
const route = useRoute()
const router = useRouter()
const sessions = ref<ChatSessionSummary[]>([])
const messages = ref<ChatMessage[]>([])
const role = ref<RolePublicPreview>()
const roleNames = ref<Record<number, string>>({})
const draft = ref('')
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const sendError = ref('')
const requiresLogin = ref(!hasAccessToken())
const messageContainer = ref<HTMLElement>()
const streamingAssistantId = ref<number>()
const activeSession = computed(() => sessions.value.find(item => item.sessionId === Number(props.sessionId)))
const streamingAssistant = computed(() => messages.value.find(message => message.id === streamingAssistantId.value))
const TYPEWRITER_INTERVAL_MS = 18

/** 同时加载会话列表、当前会话消息及其公开角色侧影。 */
async function loadChat() {
  if (requiresLogin.value) return
  loading.value = true; error.value = ''
  try {
    sessions.value = await listChatSessions()
    const characterId = activeSession.value?.characterId ?? Number(route.query.characterId)
    if (!characterId) throw new Error('聊天会话缺少角色信息')
    const [history, preview] = await Promise.all([listChatMessages(props.sessionId), getPublicRolePreview(characterId)])
    messages.value = history
    role.value = preview
    const names = await Promise.all(sessions.value.map(async session => [session.characterId, (await getPublicRolePreview(session.characterId)).characterName] as const))
    roleNames.value = Object.fromEntries(names)
    await scrollToLatest()
  } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '聊天暂时无法加载' } finally { loading.value = false }
}

/** 发送用户输入，将 SSE delta 放入队列后以稳定节奏写入助手回复。 */
async function sendMessage() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  const optimistic: ChatMessage = { id: Date.now(), sessionId: Number(props.sessionId), role: 'user', content, createTime: new Date().toISOString() }
  const assistant: ChatMessage = { id: optimistic.id + 1, sessionId: Number(props.sessionId), role: 'assistant', content: '', createTime: new Date().toISOString() }
  messages.value.push(optimistic, assistant); draft.value = ''; sending.value = true; sendError.value = ''; streamingAssistantId.value = assistant.id; await scrollToLatest()
  let completed = false
  let cancelled = false
  let pendingText = ''
  let drainPromise: Promise<void> | undefined
  const startTypewriter = () => {
    if (drainPromise) return
    const drain = (async () => {
      while (!cancelled && pendingText) {
        // 队列积压时小幅加快，避免网络短暂堆积后长时间落后于模型输出。
        const size = pendingText.length > 36 ? 3 : 1
        const segment = pendingText.slice(0, size)
        pendingText = pendingText.slice(size)
        const streamingMessage = messages.value.find(message => message.id === assistant.id)
        if (!streamingMessage) return
        streamingMessage.content += segment
        void scrollToLatest()
        await new Promise(resolve => window.setTimeout(resolve, TYPEWRITER_INTERVAL_MS))
      }
    })()
    drainPromise = drain
    void drain.finally(() => {
      if (drainPromise === drain) drainPromise = undefined
    })
  }
  try {
    await streamChatMessage(props.sessionId, content, event => {
      if (event.name === 'delta') {
        pendingText += event.payload
        startTypewriter()
      } else if (event.name === 'complete') {
        completed = true
      } else {
        throw new Error(event.payload)
      }
    })
    while (drainPromise) await drainPromise
    if (!completed) throw new Error('聊天流意外中断，请稍后重试')
  } catch (reason) {
    cancelled = true
    pendingText = ''
    messages.value = messages.value.filter(message => message.id !== assistant.id)
    sendError.value = reason instanceof Error ? reason.message : '消息发送未完成'
  } finally { streamingAssistantId.value = undefined; sending.value = false }
}

/** 将阅读流滚动到最新一条消息。 */
async function scrollToLatest() { await nextTick(); messageContainer.value?.scrollTo({ top: messageContainer.value.scrollHeight, behavior: 'smooth' }) }
function openSession(session: ChatSessionSummary) { void router.push({ path: `/chat/${session.sessionId}`, query: { characterId: session.characterId } }) }
function labelFor(session: ChatSessionSummary) { return roleNames.value[session.characterId] ?? `角色 ${session.characterId}` }

onMounted(() => void loadChat())
</script>

<template>
  <main class="chat-shell"><AuthenticationRequiredState v-if="requiresLogin" /><template v-else><aside class="sessions"><RouterLink class="brand" to="/">镜中角色</RouterLink><RouterLink class="new-chat" to="/"><el-icon><Plus /></el-icon> 开始新的对话</RouterLink><p class="side-label">RECENT CONVERSATIONS</p><button v-for="session in sessions" :key="session.sessionId" :class="['session', { active: session.sessionId === Number(sessionId) }]" @click="openSession(session)"><span class="session-mark">{{ labelFor(session).slice(0, 1) }}</span><span><b>{{ labelFor(session) }}</b><small>{{ session.userRoleVersionId ? '个人版本会话' : '公共角色基线' }}</small></span></button></aside><section class="conversation" v-loading="loading"><header v-if="role" class="chat-header"><span class="role-mark">{{ role.characterName.slice(0, 1) }}</span><div><b>{{ role.characterName }}</b><small>《{{ role.novelName }}》 · {{ activeSession?.userRoleVersionId ? '个人版本已绑定' : '公共角色基线' }}</small></div><span class="ready">● 角色已准备好</span></header><div v-if="error" class="error"><p>{{ error }}</p><button @click="loadChat">重新尝试</button></div><section v-else ref="messageContainer" class="message-stream"><p class="date">当前对话</p><div v-if="role" class="scene">你正在与 {{ role.characterName }} 对话。角色会保持其受保护的运行时画像，公开页面不会展示完整规则或原作样本。</div><article v-for="message in messages" :key="message.id" :class="['message', message.role]"><p class="speaker">{{ message.role === 'assistant' ? role?.characterName : '我' }}</p><p v-if="message.content" class="content">{{ message.content }}</p></article><article v-if="sending && !streamingAssistant?.content" class="message assistant"><p class="speaker">{{ role?.characterName }}</p><p class="typing">正在斟酌回复<span></span><span></span><span></span></p></article><p v-if="sendError" class="stream-error">{{ sendError }}</p></section><form class="composer" @submit.prevent="sendMessage"><el-input v-model="draft" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" placeholder="写下你想对角色说的话…" @keydown.enter.exact.prevent="sendMessage" /><button :disabled="sending || !draft.trim()" type="submit"><el-icon><Promotion /></el-icon>发送</button></form></section><aside v-if="role" class="role-panel"><div class="portrait">{{ role.characterName.slice(0, 1) }}</div><p class="side-label">PUBLIC CHARACTER</p><h1>{{ role.characterName }}</h1><p>{{ role.introduction }}</p><div class="facts"><span>互动素材 <b>{{ role.exampleCount }} 段</b></span><span>角色线索 <b>{{ role.ruleCount }} 条</b></span><span>会话版本 <b>{{ activeSession?.userRoleVersionId ? '个人版本' : '公共角色基线' }}</b></span></div><RouterLink :to="`/roles/${role.id}`">查看公开侧影 →</RouterLink></aside></template></main>
</template>

<style scoped>
.chat-shell { height: calc(100vh - 70px); display: grid; grid-template-columns: 235px minmax(0, 1fr) 256px; background: #fbfaf7; }.sessions { overflow-y: auto; padding: 27px 15px; border-right: 1px solid #e7e3dc; background: #f5f2ed; }.brand { display: block; margin: 0 9px 28px; color: #283335; font: 700 19px "Noto Serif SC", serif; letter-spacing: .08em; }.new-chat { display: flex; gap: 5px; align-items: center; justify-content: center; height: 35px; margin: 0 5px 24px; border: 1px solid #d8cbc0; border-radius: 5px; color: #995b45; background: #fffaf6; font-size: 11px; }.side-label { margin: 0 8px 10px; color: #9c8177; font: 500 9px "DM Mono", monospace; letter-spacing: .13em; }.session { display: flex; width: 100%; gap: 9px; align-items: center; margin: 4px 0; padding: 9px; border: 0; border-radius: 6px; color: #6d787b; background: none; cursor: pointer; text-align: left; }.session.active { color: #38494a; background: #e8ded4; }.session-mark,.role-mark { display: grid; flex: none; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg,#ad725b,#41585a); font-family: "Noto Serif SC", serif; }.session-mark { width: 25px; height: 25px; font-size: 12px; }.session b { display: block; color: #3c484b; font: 600 12px "Noto Serif SC", serif; }.session small { display: block; margin-top: 2px; font-size: 9px; }.conversation { position: relative; display: flex; min-width: 0; flex-direction: column; background: radial-gradient(circle at 50% 0,#fffefa 0,#fbfaf7 50%,#f8f7f3 100%); }.chat-header { height: 78px; display: flex; flex: none; align-items: center; padding: 0 38px; border-bottom: 1px solid #eeebe5; }.role-mark { width: 38px; height: 38px; font-size: 17px; background: linear-gradient(145deg,#3c3c3c,#b26b55 44%,#e1ae8a 45%,#bd745d 68%,#354346 69%); }.chat-header div { margin-left: 11px; }.chat-header b { display: block; font: 600 17px "Noto Serif SC", serif; }.chat-header small { color: #8a9395; font-size: 10px; }.ready { margin-left: auto; color: #63817a; font: 500 10px "DM Mono", monospace; }.message-stream { flex: 1; overflow-y: auto; width: min(690px, calc(100% - 60px)); margin: 0 auto; padding: 42px 0 150px; }.date { color: #b0aaa1; font: 500 9px "DM Mono", monospace; letter-spacing: .08em; text-align: center; }.scene { margin: 27px 0 34px; padding: 11px 15px; border-left: 2px solid #d4b3a4; color: #8d827b; background: #f7f2ec; font: 500 11px "Noto Serif SC", serif; line-height: 1.8; }.message { margin: 25px 0; }.message.assistant { padding-right: 52px; }.message.user { padding-left: 75px; text-align: right; }.speaker { margin: 0 0 8px; color: #9e6650; font: 500 10px "DM Mono", monospace; letter-spacing: .08em; }.user .speaker { color: #6d8584; }.content { margin: 0; white-space: pre-wrap; font-family: "Noto Serif SC", serif; font-size: 16px; line-height: 2; }.user .content { display: inline-block; padding: 11px 14px; border-radius: 12px 3px 12px 12px; color: #314849; background: #e5efec; font-family: "Source Han Sans CN", sans-serif; font-size: 13px; line-height: 1.7; text-align: left; }.typing { color: #9d8b84; font: 500 12px "Noto Serif SC", serif; }.typing span { display: inline-block; width: 4px; height: 4px; margin-left: 4px; border-radius: 50%; background: #c19b8b; animation: pulse 1s infinite alternate; }.typing span:nth-child(2) { animation-delay: .2s }.typing span:nth-child(3) { animation-delay: .4s }@keyframes pulse { to { opacity: .2; transform: translateY(-2px) } }.stream-error { margin: 10px 0; padding: 10px 12px; border-left: 2px solid #bd715c; color: #925845; background: #fff5f1; font-size: 12px; }.composer { position: absolute; right: 0; bottom: 0; left: 0; display: flex; gap: 9px; align-items: end; padding: 17px 38px 23px; background: linear-gradient(transparent,#fbfaf7 30%); }.composer :deep(.el-textarea__inner) { box-shadow: 0 7px 25px #5b4d4412; border: 1px solid #ded8d0; }.composer button { display: inline-flex; gap: 5px; align-items: center; height: 36px; padding: 0 12px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 11px; }.composer button:disabled { opacity: .5; cursor: not-allowed; }.role-panel { overflow-y: auto; padding: 30px 22px; border-left: 1px solid #e7e3dc; background: #fdfcf9; }.portrait { display: grid; height: 164px; place-items: center; margin-bottom: 20px; border-radius: 8px; color: #fff; background: linear-gradient(145deg,#2d3030,#a26151 42%,#dfb28e 43%,#c27660 65%,#44413f 66%,#2e393b); font: 600 58px "Noto Serif SC", serif; }.role-panel h1 { margin: 5px 0; font: 600 24px "Noto Serif SC", serif; }.role-panel > p:not(.side-label) { color: #717b7e; font-size: 12px; line-height: 1.8; }.facts { display: grid; gap: 10px; margin: 21px 0; padding-top: 17px; border-top: 1px solid #e8e3dc; }.facts span { display: flex; justify-content: space-between; color: #8a9295; font-size: 10px; }.facts b { color: #43595a; font-weight: 600; }.role-panel > a { color: #9b5e46; font-size: 11px; }.error { display: grid; min-height: 250px; place-content: center; justify-items: center; color: #7b8588; }.error button { padding: 8px 12px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; }
@media(max-width:900px){.chat-shell{height:calc(100vh - 60px);grid-template-columns:1fr}.sessions,.role-panel{display:none}.chat-header{padding:0 18px}.message-stream{width:min(100% - 34px,690px)}.composer{padding:15px 17px 19px}.ready{display:none}}
</style>
