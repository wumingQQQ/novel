<script setup lang="ts">
import { ArrowLeft, Check, Close, EditPen, MagicStick, Position, RefreshRight } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { createChatSession } from '@/api/chat'
import { generateRoleAdjustCandidates, getRoleAdjustRequest, reviewRoleAdjustItems, reviseRoleAdjustItems } from '@/api/role-adjust'
import { getPublicRolePreview } from '@/api/roles'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import type { RoleAdjustRequestDetail, RoleAdjustStatus } from '@/types/role-adjust'
import type { RolePublicPreview } from '@/types/role'

const props = defineProps<{ requestId: string }>()
const router = useRouter()
const request = ref<RoleAdjustRequestDetail>()
const role = ref<RolePublicPreview>()
const loading = ref(false)
const working = ref(false)
const error = ref('')
const notice = ref('')
const requiresLogin = ref(!hasAccessToken())
const reviewDrafts = ref<Record<number, { status: RoleAdjustStatus; revisionFeedback?: string }>>({})

const revisingItems = computed(() => request.value?.items.filter(item => item.status === 'REVISING') ?? [])
const pendingItems = computed(() => request.value?.items.filter(item => item.status === 'PENDING') ?? [])
const draftCount = computed(() => Object.keys(reviewDrafts.value).length)

/** 读取调整请求及对应角色的公开侧影，候选细节保持按请求隔离。 */
async function loadRequest() {
  if (requiresLogin.value) return
  loading.value = true; error.value = ''
  try {
    const detail = await getRoleAdjustRequest(props.requestId)
    request.value = detail
    role.value = await getPublicRolePreview(detail.characterId)
    reviewDrafts.value = {}
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true
    else error.value = reason instanceof Error ? reason.message : '调整请求暂时无法加载'
  } finally { loading.value = false }
}

/** 触发一次候选补丁生成，完成后重新读取最终状态。 */
async function generateCandidates() {
  if (!request.value || working.value) return
  working.value = true; notice.value = ''
  try { await generateRoleAdjustCandidates(request.value.id); notice.value = '候选调整项已生成。'; await loadRequest() } catch (reason) { error.value = reason instanceof Error ? reason.message : '候选项生成未完成' } finally { working.value = false }
}

/** 在本地暂存一个候选项的评审决定，允许用户分批提交。 */
function setReview(itemId: number, status: RoleAdjustStatus) {
  reviewDrafts.value = { ...reviewDrafts.value, [itemId]: { status, revisionFeedback: reviewDrafts.value[itemId]?.revisionFeedback } }
}

/** 更新“请求修订”所需的反馈，不在输入时立即请求后端。 */
function setRevisionFeedback(itemId: number, revisionFeedback: string) {
  reviewDrafts.value = { ...reviewDrafts.value, [itemId]: { status: 'REVISING', revisionFeedback } }
}

/** 将当前暂存的评审结果提交，后端可能在全部完成时自动创建个人版本。 */
async function submitReviews() {
  if (!request.value || !draftCount.value || working.value) return
  working.value = true; notice.value = ''
  try {
    const items = Object.entries(reviewDrafts.value).map(([itemId, draft]) => ({ itemId: Number(itemId), ...draft }))
    const result = await reviewRoleAdjustItems(request.value.id, items)
    notice.value = result.createdVersionId ? '个人角色版本已自动创建。' : result.itemErrors.length ? result.itemErrors.map(item => item.message).join('；') : '评审结果已保存。'
    await loadRequest()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '评审提交未完成' } finally { working.value = false }
}

/** 依据已提交的反馈，重新生成全部待修订候选项。 */
async function reviseItems() {
  if (!request.value || !revisingItems.value.length || working.value) return
  working.value = true; notice.value = ''
  try { await reviseRoleAdjustItems(request.value.id); notice.value = '待修订候选项已重新生成。'; await loadRequest() } catch (reason) { error.value = reason instanceof Error ? reason.message : '候选项修订未完成' } finally { working.value = false }
}

/** 创建绑定本次自动生成个人版本的聊天会话。 */
async function beginChat() {
  if (!request.value?.createdVersionId || working.value) return
  working.value = true
  try {
    const session = await createChatSession(request.value.characterId, request.value.createdVersionId)
    await router.push({ path: `/chat/${session.sessionId}`, query: { characterId: request.value.characterId } })
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '创建聊天会话未完成' } finally { working.value = false }
}

function statusLabel(status: string) { return ({ PENDING: '等待生成', GENERATING: '正在生成', READY: '等待评审', CONFIRMED: '已确认', COMPLETED: '个人版本已创建', FAILED: '生成失败', CANCELLED: '已取消', ACCEPTED: '已接受', REJECTED: '已拒绝', REVISING: '等待修订' })[status] ?? status }
function changeLabel(changeType: string) { return ({ ADD: '新增补丁', REPLACE: '替换补丁', DISABLE: '停用补丁' })[changeType] ?? changeType }
onMounted(() => void loadRequest())
</script>

<template>
  <main class="workspace-page"><RouterLink class="back" to="/my-evaluations"><el-icon><ArrowLeft /></el-icon> 返回我的调整</RouterLink><AuthenticationRequiredState v-if="requiresLogin" /><section v-else v-loading="loading"><div v-if="error" class="state error"><p>{{ error }}</p><button @click="loadRequest">重新尝试</button></div><template v-else-if="request && role"><header class="workspace-header"><div><p class="eyebrow">PERSONAL ROLE ADJUSTMENT</p><p class="novel">《{{ role.novelName || '未知作品' }}》</p><h1>{{ role.characterName }}</h1><p>{{ request.requirement }}</p><p v-if="request.chatText" class="context">对话上下文：{{ request.chatText }}</p></div><div class="header-actions"><span :class="['status', request.status.toLowerCase()]">{{ statusLabel(request.status) }}</span><button v-if="request.createdVersionId" class="primary" :disabled="working" @click="beginChat"><el-icon><Position /></el-icon>使用个人版本对话</button></div></header><p v-if="notice" class="notice">{{ notice }}</p><section v-if="request.status === 'PENDING' || request.status === 'GENERATING'" class="action-state"><el-icon><MagicStick /></el-icon><h2>{{ request.status === 'PENDING' ? '生成候选调整项' : '正在生成候选项' }}</h2><p>系统会结合角色现有运行时资产生成有限的补丁候选项，原作内容不会在这里展示。</p><button v-if="request.status === 'PENDING'" class="primary" :disabled="working" @click="generateCandidates">{{ working ? '正在生成…' : '生成候选项' }}</button><button v-else class="quiet" @click="loadRequest"><el-icon><RefreshRight /></el-icon>刷新状态</button></section><section v-else-if="request.status === 'FAILED'" class="action-state"><h2>候选项生成失败</h2><p>{{ request.failureReason || '请稍后重新创建调整请求。' }}</p></section><template v-else><section class="review-head"><div><p class="eyebrow">CANDIDATE REVIEW</p><h2>确认要保留的角色补丁</h2><p>可分批评审。全部候选项被接受或拒绝后，个人版本将自动创建。</p></div><div class="review-actions"><button v-if="revisingItems.length" class="quiet" :disabled="working" @click="reviseItems"><el-icon><RefreshRight /></el-icon>修订 {{ revisingItems.length }} 项</button><button v-if="draftCount" class="primary" :disabled="working" @click="submitReviews"><el-icon><Check /></el-icon>提交 {{ draftCount }} 项评审</button></div></section><div v-if="request.items.length === 0" class="state">候选项尚未准备完成。</div><div v-else class="candidate-list"><article v-for="item in request.items" :key="item.id" class="candidate"><div class="candidate-top"><span class="change">{{ changeLabel(item.changeType) }}</span><span :class="['status', item.status.toLowerCase()]">{{ statusLabel(item.status) }}</span><span>参考 {{ item.passageIds.length }} 个文本块</span></div><h3>{{ item.applicability }}</h3><dl><div><dt>期望行为</dt><dd>{{ item.expectedBehavior }}</dd></div><div><dt>避免行为</dt><dd>{{ item.forbiddenBehavior }}</dd></div></dl><p v-if="item.revisionError" class="item-error">{{ item.revisionError }}</p><template v-if="item.status === 'PENDING'"><div v-if="reviewDrafts[item.id]?.status === 'REVISING'" class="revision"><el-input :model-value="reviewDrafts[item.id]?.revisionFeedback" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" maxlength="500" show-word-limit placeholder="说明希望如何改写这项候选补丁。" @update:model-value="setRevisionFeedback(item.id, $event)" /></div><div class="candidate-actions"><button class="accept" @click="setReview(item.id, 'ACCEPTED')"><el-icon><Check /></el-icon>接受</button><button @click="setReview(item.id, 'REJECTED')"><el-icon><Close /></el-icon>拒绝</button><button @click="setReview(item.id, 'REVISING')"><el-icon><EditPen /></el-icon>请求修订</button></div></template><p v-else-if="item.status === 'REVISING'" class="revision-note">反馈：{{ item.revisionFeedback }}</p></article></div></template></template></section></main>
</template>

<style scoped>
.workspace-page{width:min(1000px,calc(100% - 48px));margin:0 auto;padding:42px 0 100px}.back{display:inline-flex;gap:6px;align-items:center;color:#687579;font-size:13px}.workspace-header{display:flex;justify-content:space-between;gap:20px;align-items:start;margin:34px 0 24px;padding:30px;border:1px solid #e4dfd8;border-radius:8px;background:linear-gradient(130deg,#f1e6dc,#fbfaf7 66%)}.eyebrow{margin:0;color:#a55d43;font:500 11px "DM Mono",monospace;letter-spacing:.14em}.novel{margin:13px 0 0;color:#768186;font-size:12px}.workspace-header h1,.review-head h2,.action-state h2{margin:5px 0;font-family:"Noto Serif SC",serif;font-weight:600}.workspace-header h1{font-size:39px}.workspace-header p:last-child{max-width:580px;margin-bottom:0;color:#687478;font-size:13px;line-height:1.75}.context{margin-top:12px!important;padding-left:12px;border-left:2px solid #d4b3a4;color:#7f898b!important}.header-actions,.review-actions{display:flex;flex:none;gap:9px;align-items:center}.status,.change{padding:4px 7px;border-radius:3px;font:500 9px "DM Mono",monospace}.ready,.accepted,.completed{color:#3f6864;background:#e2efeb}.pending,.generating,.revising{color:#9c654a;background:#f8e9df}.failed,.rejected,.cancelled{color:#8c7370;background:#eee9e6}.primary,.quiet,.candidate-actions button,.state button{display:inline-flex;gap:5px;align-items:center;padding:8px 12px;border:0;border-radius:5px;cursor:pointer;font-size:12px}.primary{color:#fff;background:#344b4c}.quiet{color:#805f53;background:#f6ece5}.notice{margin:0 0 18px;padding:10px 13px;border-left:2px solid #75938b;color:#4f6663;background:#f1f7f4;font-size:12px}.action-state,.state{display:grid;min-height:230px;place-content:center;justify-items:center;padding:22px;border:1px dashed #ded7ce;border-radius:8px;color:#788286;text-align:center}.action-state :deep(.el-icon){color:#a55d43;font-size:28px}.action-state p{max-width:460px;line-height:1.8}.review-head{display:flex;justify-content:space-between;gap:20px;align-items:end;margin:46px 0 17px}.review-head h2{font-size:28px}.review-head p{margin:6px 0 0;color:#778287;font-size:12px}.candidate-list{display:grid;gap:12px}.candidate{padding:18px;border:1px solid #e7e2dc;border-radius:8px;background:#fff}.candidate-top{display:flex;flex-wrap:wrap;gap:7px;align-items:center;color:#8a9497;font-size:10px}.change{color:#8b5d4d;background:#f6ebe4}.candidate h3{margin:13px 0 10px;font-family:"Noto Serif SC",serif;font-size:19px}.candidate dl{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:0}.candidate dl div{padding:10px 12px;background:#f8f7f4}.candidate dt{color:#8a9497;font-size:10px}.candidate dd{margin:5px 0 0;color:#4f5c60;font-size:12px;line-height:1.65}.candidate-actions{display:flex;flex-wrap:wrap;gap:7px;margin-top:15px}.candidate-actions button{color:#7a6c67;background:#f5f2ed}.candidate-actions .accept{color:#fff;background:#3d6862}.revision{margin-top:14px}.revision-note,.item-error{margin:14px 0 0;color:#916150;font-size:12px}.item-error{color:#a14d42}@media(max-width:650px){.workspace-page{width:min(100% - 30px,1000px)}.workspace-header,.review-head{display:block;padding:22px}.workspace-header h1{font-size:32px}.header-actions,.review-actions{margin-top:15px;flex-wrap:wrap}.candidate dl{grid-template-columns:1fr}}
</style>
