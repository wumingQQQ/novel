<script setup lang="ts">
import { ArrowLeft, DocumentChecked, EditPen, Position, Tickets } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { useAuth } from '@/composables/useAuth'
import { createChatSession } from '@/api/chat'
import { createRoleAdjustRequest } from '@/api/role-adjust'
import { getPublicRolePreview } from '@/api/roles'
import type { RolePublicPreview } from '@/types/role'

const route = useRoute()
const router = useRouter()
const role = ref<RolePublicPreview>()
const loading = ref(false)
const error = ref('')
const isCreatingAdjustment = ref(false)
const isCreatingChat = ref(false)
const isAdjustmentDialogVisible = ref(false)
const adjustmentRequirement = ref('')
const adjustmentChatText = ref('')
const { openAuthDialog } = useAuth()
const characterId = computed(() => String(route.params.id))
const userRoleVersionId = computed(() => {
  const value = Number(route.query.userRoleVersionId)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const versionNo = computed(() => {
  const value = Number(route.query.versionNo)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})

/** 加载公共角色的受限预览。 */
async function loadPreview() {
  loading.value = true
  error.value = ''
  try { role.value = await getPublicRolePreview(characterId.value) } catch (reason) { error.value = reason instanceof Error ? reason.message : '角色预览暂时无法加载' } finally { loading.value = false }
}

/** 打开调整目标填写框，个人版本详情会自动作为本次调整的基线。 */
function openAdjustmentDialog() {
  if (!role.value || !hasAccessToken()) {
    openAuthDialog()
    return
  }
  isAdjustmentDialogVisible.value = true
}

/** 创建调整请求后进入候选项生成与评审工作台。 */
async function createAdjustment() {
  if (!role.value || !adjustmentRequirement.value.trim() || isCreatingAdjustment.value) return
  isCreatingAdjustment.value = true
  try {
    const requestId = await createRoleAdjustRequest({
      characterId: role.value.id,
      requirement: adjustmentRequirement.value.trim(),
      chatText: adjustmentChatText.value.trim() || undefined,
      baseVersionId: userRoleVersionId.value,
    })
    isAdjustmentDialogVisible.value = false
    await router.push(`/my-evaluations/${requestId}`)
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) openAuthDialog()
    else error.value = reason instanceof Error ? reason.message : '创建调整请求未完成'
  } finally {
    isCreatingAdjustment.value = false
  }
}

/** 有既有 JWT 时创建公共角色或指定个人版本绑定的会话。 */
async function beginChat() {
  if (!role.value || !hasAccessToken()) {
    openAuthDialog()
    return
  }
  isCreatingChat.value = true
  try {
    const session = await createChatSession(role.value.id, userRoleVersionId.value)
    await router.push({ path: `/chat/${session.sessionId}`, query: { characterId: role.value.id } })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '创建聊天会话未完成'
  } finally {
    isCreatingChat.value = false
  }
}

onMounted(() => void loadPreview())
</script>

<template>
  <main class="preview-page" v-loading="loading">
    <RouterLink class="back-link" to="/"><el-icon><ArrowLeft /></el-icon> 返回角色大厅</RouterLink>
    <section v-if="error" class="state-card"><p>{{ error }}</p><button type="button" @click="loadPreview">重新尝试</button></section>
    <template v-else-if="role">
      <section class="role-hero"><div class="hero-mark" aria-hidden="true">{{ role.characterName.slice(0, 1) }}</div><div class="hero-copy"><p class="eyebrow">{{ userRoleVersionId ? 'PERSONAL ROLE VERSION' : 'PUBLIC CHARACTER PREVIEW' }}</p><p class="novel-name">来自《{{ role.novelName || '未知作品' }}》</p><h1>{{ role.characterName }}</h1><p v-if="userRoleVersionId" class="version-note">你将使用个人版本{{ versionNo ? ` v${versionNo}` : '' }}与角色对话。</p><p class="introduction">{{ role.introduction }}</p><p class="privacy-note">这是一份公开侧影。完整画像、反应规则与原作素材仅用于系统内部运行，不会在此展示。</p><div class="actions"><button class="primary-action" type="button" :disabled="isCreatingChat" @click="beginChat">{{ isCreatingChat ? '正在进入…' : userRoleVersionId ? '使用个人版本对话' : '开始对话' }}</button><button class="secondary-action" type="button" @click="openAdjustmentDialog"><el-icon><EditPen /></el-icon>调整这个角色</button></div></div></section>
      <section class="facts"><article><el-icon><Tickets /></el-icon><b>{{ role.exampleCount }}</b><span>互动素材片段</span></article><article><el-icon><DocumentChecked /></el-icon><b>{{ role.ruleCount }}</b><span>角色线索</span></article><article><el-icon><Position /></el-icon><b>公开预览</b><span>完整资产已受保护</span></article></section>
      <section class="about"><p class="eyebrow">WHAT YOU CAN KNOW</p><h2>先认识他的侧影。</h2><p>你看到的是公共角色的有限介绍，用于帮助你判断是否值得开始一次独立评测。评测将从公开角色的受保护资产中生成结果，但不会向任何用户披露原始画像、规则或样本。</p></section>
    </template>
    <el-dialog v-model="isAdjustmentDialogVisible" width="min(560px, calc(100% - 32px))" align-center><template #header><span class="dialog-title">调整 {{ role?.characterName }}</span></template><div class="adjustment-form"><label>希望如何调整</label><el-input v-model="adjustmentRequirement" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" maxlength="200" show-word-limit placeholder="例如：面对质疑时更克制，先说明事实再表达态度。" /><label>相关对话（可选）</label><el-input v-model="adjustmentChatText" type="textarea" :autosize="{ minRows: 3, maxRows: 7 }" maxlength="2000" show-word-limit placeholder="粘贴一段希望角色改进的对话上下文。" /></div><template #footer><el-button @click="isAdjustmentDialogVisible = false">取消</el-button><el-button type="primary" :disabled="!adjustmentRequirement.trim()" :loading="isCreatingAdjustment" @click="createAdjustment">创建调整请求</el-button></template></el-dialog>
  </main>
</template>

<style scoped>
.preview-page { width: min(980px, calc(100% - 48px)); min-height: 600px; margin: 0 auto; padding: 43px 0 100px; }.back-link { display: inline-flex; gap: 6px; align-items: center; color: #617073; font-size: 13px; }.back-link:hover { color: #a55d43; }.role-hero { display: grid; grid-template-columns: 310px 1fr; gap: 56px; align-items: center; margin-top: 37px; padding: 48px; border: 1px solid #e5dfd7; border-radius: 8px; background: linear-gradient(120deg, #f2e8df, #fbfaf7 70%); }.hero-mark { display: grid; height: 330px; place-items: center; border-radius: 8px; color: rgb(255 255 255 / 84%); background: linear-gradient(145deg, #2d3030, #a26151 42%, #dfb28e 43%, #c27660 65%, #44413f 66%, #2e393b); font-family: "Noto Serif SC", serif; font-size: 112px; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.novel-name { margin: 15px 0 0; color: #737d81; font-size: 13px; }.hero-copy h1, .about h2 { font-family: "Noto Serif SC", serif; font-weight: 600; }.hero-copy h1 { margin: 7px 0 12px; font-size: 43px; }.version-note { margin: 0 0 10px; color: #3d6862; font-size: 12px; }.introduction { color: #4f5d60; font-size: 17px; line-height: 1.7; }.privacy-note { padding-left: 13px; border-left: 2px solid #c8917a; color: #7f898b; font-size: 12px; line-height: 1.75; }.actions { display: flex; gap: 9px; margin-top: 15px; }.primary-action, .secondary-action { display: inline-flex; gap: 7px; align-items: center; padding: 11px 16px; border-radius: 5px; cursor: pointer; }.primary-action { border: 0; color: #fff; background: #344b4c; }.secondary-action { border: 1px solid #cba18e; color: #9b5c45; background: transparent; }.facts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin: 23px 0 67px; }.facts article { display: grid; grid-template-columns: auto 1fr; gap: 0 10px; align-items: center; padding: 18px; border: 1px solid #e8e4de; border-radius: 8px; background: #fff; }.facts :deep(.el-icon) { grid-row: span 2; color: #a55d43; font-size: 20px; }.facts b { font-family: "DM Mono", monospace; font-size: 19px; }.facts span { color: #899195; font-size: 11px; }.about { max-width: 620px; margin: auto; text-align: center; }.about h2 { margin: 8px 0 10px; font-size: 31px; }.about p:last-child { color: #717c80; font-size: 14px; line-height: 1.9; }.state-card { display: grid; min-height: 300px; place-content: center; justify-items: center; color: #727d81; }.state-card button { padding: 8px 13px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; }.dialog-title { font-family: "Noto Serif SC", serif; font-size: 20px; }.dialog-copy { color: #697477; line-height: 1.8; }.adjustment-form { display: grid; gap: 8px; }.adjustment-form label { margin-top: 8px; color: #586467; font-size: 13px; }.el-button--primary { background: #344b4c; border-color: #344b4c; }
@media (max-width: 720px) { .preview-page { width: min(100% - 30px, 980px); padding-top: 29px; }.role-hero { grid-template-columns: 1fr; gap: 26px; padding: 24px; }.hero-mark { height: 190px; font-size: 68px; }.hero-copy h1 { font-size: 35px; }.facts { grid-template-columns: 1fr; margin-bottom: 45px; } }
</style>
