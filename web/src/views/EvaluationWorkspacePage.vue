<script setup lang="ts">
import { ArrowLeft, Check, MagicStick, Position, VideoPlay } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { createChatSession } from '@/api/chat'
import { approveEvaluationCase, generateEvaluationCases, listEvaluationCases, listEvaluationRuns, rejectEvaluationCase, runEvaluationCase } from '@/api/evaluations'
import { getRoleWorkspace } from '@/api/workspaces'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import type { EvaluationCase, EvaluationRun, RoleWorkspaceDetailResponse } from '@/types/workspace'

const props = defineProps<{ characterId: string }>()
const router = useRouter()
const workspace = ref<RoleWorkspaceDetailResponse>()
const selectedEvaluationId = ref<number>()
const cases = ref<EvaluationCase[]>([])
const runsByCase = ref<Record<number, EvaluationRun[]>>({})
const loading = ref(false)
const casesLoading = ref(false)
const error = ref('')
const requiresLogin = ref(!hasAccessToken())
const creatingCases = ref(false)
const creatingChat = ref(false)
const datasetVersion = ref('v1')

const selectedEvaluation = computed(() => workspace.value?.evaluations.find(item => item.evaluationId === selectedEvaluationId.value))

/** 加载当前用户在选中角色下的评测轮次。 */
async function loadWorkspace() {
  if (requiresLogin.value) return
  loading.value = true; error.value = ''
  try {
    workspace.value = await getRoleWorkspace(props.characterId)
    selectedEvaluationId.value ??= workspace.value.evaluations[0]?.evaluationId
    if (selectedEvaluationId.value) await loadCases()
  } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '评测工作区暂时无法加载' } finally { loading.value = false }
}

/** 加载选中评测轮次的案例及已有运行记录。 */
async function loadCases() {
  if (!selectedEvaluationId.value) return
  casesLoading.value = true
  try {
    cases.value = await listEvaluationCases(selectedEvaluationId.value)
    const entries = await Promise.all(cases.value.map(async item => [item.id, await listEvaluationRuns(selectedEvaluationId.value!, item.id)] as const))
    runsByCase.value = Object.fromEntries(entries)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '评测案例暂时无法加载' } finally { casesLoading.value = false }
}

/** 生成当前评测轮次的少量待审核案例。 */
async function createCases() {
  if (!selectedEvaluationId.value) return
  creatingCases.value = true
  try { await generateEvaluationCases(selectedEvaluationId.value, datasetVersion.value.trim() || 'v1', 4); await loadWorkspace() } catch (reason) { error.value = reason instanceof Error ? reason.message : '案例生成未完成' } finally { creatingCases.value = false }
}

/** 审核或拒绝案例后刷新其运行前状态。 */
async function reviewCase(item: EvaluationCase, approved: boolean) {
  if (!selectedEvaluationId.value) return
  try { if (approved) await approveEvaluationCase(selectedEvaluationId.value, item.id); else await rejectEvaluationCase(selectedEvaluationId.value, item.id); await loadCases() } catch (reason) { error.value = reason instanceof Error ? reason.message : '案例审核未完成' }
}

/** 执行已审核案例并刷新 Judge 运行记录。 */
async function runCase(item: EvaluationCase) {
  if (!selectedEvaluationId.value) return
  try { await runEvaluationCase(selectedEvaluationId.value, item.id); await loadCases(); await loadWorkspace() } catch (reason) { error.value = reason instanceof Error ? reason.message : '评测运行未完成' }
}

/** 切换评测轮次时重新读取其案例和运行记录。 */
function selectEvaluation(id: number) { selectedEvaluationId.value = id; void loadCases() }

function latestRun(caseId: number) { return runsByCase.value[caseId]?.[0] }
function scoreLabel(score: number | null | undefined) { return score == null ? '等待评分' : `${score.toFixed(1)} / 5` }

/** 基于当前工作区的最新个人版本创建聊天会话；没有个人版本时使用公共基线。 */
async function beginChat() {
  if (!workspace.value || creatingChat.value) return
  creatingChat.value = true
  try {
    const session = await createChatSession(
      workspace.value.workspace.character.id,
      workspace.value.workspace.latestUserRoleVersionId,
    )
    await router.push({
      path: `/chat/${session.id}`,
      query: { characterId: workspace.value.workspace.character.id },
    })
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true
    else error.value = reason instanceof Error ? reason.message : '创建聊天会话未完成'
  } finally {
    creatingChat.value = false
  }
}

onMounted(() => void loadWorkspace())
</script>

<template>
  <main class="workspace-page">
    <RouterLink class="back" to="/my-evaluations"><el-icon><ArrowLeft /></el-icon> 返回我的评测</RouterLink>
    <AuthenticationRequiredState v-if="requiresLogin" />
    <section v-else v-loading="loading">
      <div v-if="error" class="error"><p>{{ error }}</p><button @click="loadWorkspace">重新尝试</button></div>
      <template v-else-if="workspace">
        <header class="workspace-header">
          <div><p class="eyebrow">EVALUATION WORKBENCH</p><p class="novel">《{{ workspace.workspace.character.novelName }}》</p><h1>{{ workspace.workspace.character.characterName }}</h1><p>{{ workspace.workspace.character.introduction }}</p></div>
          <div class="header-actions"><div class="version-chip">{{ workspace.workspace.latestVersionNo ? `个人版本 v${workspace.workspace.latestVersionNo}` : '公共角色基线' }}</div><button class="chat-button" :disabled="creatingChat" @click="beginChat"><el-icon><Position /></el-icon>{{ creatingChat ? '正在进入…' : '开始对话' }}</button></div>
        </header>
        <section class="evaluation-tabs"><button v-for="evaluation in workspace.evaluations" :key="evaluation.evaluationId" :class="{ selected: selectedEvaluationId === evaluation.evaluationId }" @click="selectEvaluation(evaluation.evaluationId)"><b>评测 {{ evaluation.evaluationId }}</b><span>{{ evaluation.caseCount }} 案例 · {{ evaluation.succeededRunCount }} 运行</span></button></section>
        <section v-if="selectedEvaluation" class="dashboard"><article><span>案例进度</span><b>{{ selectedEvaluation.approvedCaseCount }} / {{ selectedEvaluation.caseCount }}</b><small>已审核案例</small></article><article><span>最近评分</span><b>{{ scoreLabel(selectedEvaluation.latestScore) }}</b><small>仅统计成功运行</small></article><article><span>待处理改进</span><b>{{ selectedEvaluation.draftImprovementBatchCount }}</b><small>规则建议批次</small></article></section>
        <section class="case-head"><div><p class="eyebrow">CASE REVIEW</p><h2>案例与角色反应</h2></div><div class="case-action"><el-input v-model="datasetVersion" aria-label="案例集版本" placeholder="数据集版本" /><button :disabled="creatingCases" @click="createCases"><el-icon><MagicStick /></el-icon>{{ creatingCases ? '正在构造…' : '生成案例' }}</button></div></section>
        <div v-loading="casesLoading" class="case-list"><div v-if="!casesLoading && cases.length === 0" class="empty">尚无案例。请先从受保护的原作资产中生成少量待审核案例。</div><article v-for="item in cases" :key="item.id" class="case-card"><div class="case-main"><div class="case-top"><span :class="['status', item.status.toLowerCase()]">{{ item.status }}</span><span>{{ item.datasetVersion }} · {{ item.difficulty || '未标记难度' }}</span></div><h3>{{ item.testInput }}</h3><p v-if="item.expectedBehaviors">期望：{{ item.expectedBehaviors }}</p><p v-if="item.scoringRubric">评分关注：{{ item.scoringRubric }}</p></div><div class="case-actions"><template v-if="item.status === 'DRAFT'"><button class="text-button accept" @click="reviewCase(item, true)"><el-icon><Check /></el-icon>审核</button><button class="text-button" @click="reviewCase(item, false)">拒绝</button></template><button v-else-if="item.status === 'APPROVED'" class="run-button" @click="runCase(item)"><el-icon><VideoPlay /></el-icon>运行评测</button></div><div v-if="latestRun(item.id)" class="run-result"><b>{{ scoreLabel(latestRun(item.id)?.totalScore) }}</b><span>{{ latestRun(item.id)?.status }}</span><p>{{ latestRun(item.id)?.judgeReason || latestRun(item.id)?.failureReason || '本次运行尚未写入评分说明。' }}</p></div></article></div>
      </template>
    </section>
  </main>
</template>

<style scoped>
.workspace-page { width: min(1000px, calc(100% - 48px)); margin: 0 auto; padding: 42px 0 100px; }.back { display: inline-flex; gap: 6px; align-items: center; color: #687579; font-size: 13px; }.workspace-header { display: flex; justify-content: space-between; gap: 20px; align-items: start; margin: 34px 0 24px; padding: 30px; border: 1px solid #e4dfd8; border-radius: 11px; background: linear-gradient(130deg, #f1e6dc, #fbfaf7 66%); }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.novel { margin: 13px 0 0; color: #768186; font-size: 12px; }.workspace-header h1, .case-head h2 { margin: 5px 0; font-family: "Noto Serif SC", serif; font-weight: 600; }.workspace-header h1 { font-size: 39px; }.workspace-header p:last-child { max-width: 540px; margin-bottom: 0; color: #687478; font-size: 13px; line-height: 1.75; }.version-chip { flex: none; padding: 7px 10px; border-radius: 4px; color: #3d595a; background: #e7eeea; font-size: 11px; }.evaluation-tabs { display: flex; gap: 9px; overflow-x: auto; margin-bottom: 20px; padding-bottom: 4px; }.evaluation-tabs button { display: grid; flex: none; gap: 4px; min-width: 145px; padding: 12px; border: 1px solid #e5e0d9; border-radius: 7px; color: #728084; background: #fff; cursor: pointer; text-align: left; }.evaluation-tabs button.selected { border-color: #a9634c; color: #344c4d; background: #f7ede7; }.evaluation-tabs b { font-family: "Noto Serif SC", serif; font-size: 14px; }.evaluation-tabs span { font-size: 10px; }.dashboard { display: grid; grid-template-columns: repeat(3, 1fr); gap: 13px; }.dashboard article { display: grid; gap: 5px; padding: 17px; border: 1px solid #e7e2db; border-radius: 7px; background: #fff; }.dashboard span, .dashboard small { color: #828c90; font-size: 10px; }.dashboard b { color: #344c4d; font: 500 24px "DM Mono", monospace; }.case-head { display: flex; justify-content: space-between; align-items: end; margin: 62px 0 17px; }.case-head h2 { font-size: 28px; }.case-action { display: flex; gap: 7px; }.case-action :deep(.el-input) { width: 130px; }.case-action button, .run-button, .error button { display: inline-flex; gap: 5px; align-items: center; padding: 8px 12px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 12px; }.case-action button:disabled { opacity: .6; cursor: wait; }.case-list { display: grid; gap: 11px; min-height: 100px; }.case-card { display: grid; grid-template-columns: 1fr auto; gap: 16px; padding: 18px; border: 1px solid #e7e2dc; border-radius: 8px; background: #fff; }.case-top { display: flex; gap: 8px; align-items: center; color: #8a9497; font-size: 10px; }.status { padding: 3px 6px; border-radius: 3px; font: 500 9px "DM Mono", monospace; }.draft { color: #a35c43; background: #f7e8df; }.approved { color: #3f6864; background: #e2efeb; }.rejected { color: #8c7370; background: #eee9e6; }.case-main h3 { margin: 9px 0 6px; font-family: "Noto Serif SC", serif; font-size: 18px; }.case-main p { margin: 4px 0; color: #758084; font-size: 12px; line-height: 1.65; }.case-actions { display: flex; gap: 8px; align-items: start; }.text-button { border: 0; color: #8b7773; background: none; cursor: pointer; font-size: 12px; }.text-button.accept { color: #3d6862; }.run-result { grid-column: 1 / -1; display: grid; grid-template-columns: auto auto 1fr; gap: 10px; align-items: center; padding-top: 13px; border-top: 1px solid #eeeae4; }.run-result b { color: #3e5b5b; font: 500 17px "DM Mono", monospace; }.run-result span { padding: 3px 5px; color: #8b9699; background: #f2f1ee; font: 500 9px "DM Mono", monospace; }.run-result p { margin: 0; color: #6f7b7f; font-size: 11px; line-height: 1.55; }.empty, .error { display: grid; min-height: 180px; place-content: center; justify-items: center; border: 1px dashed #ded7ce; border-radius: 8px; color: #7b8689; text-align: center; font-size: 13px; }.error button { margin-top: 10px; }
.header-actions { display: grid; flex: none; justify-items: end; gap: 10px; }.chat-button { display: inline-flex; gap: 5px; align-items: center; padding: 8px 12px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 12px; }.chat-button:disabled { opacity: .6; cursor: wait; }
@media (max-width: 650px) { .workspace-page { width: min(100% - 30px, 1000px); }.workspace-header { display: block; padding: 22px; }.workspace-header h1 { font-size: 32px; }.header-actions { justify-items: start; }.version-chip { display: inline-block; margin-top: 14px; }.chat-button { margin-top: 10px; }.dashboard { grid-template-columns: 1fr; }.case-head { display: block; margin-top: 43px; }.case-action { margin-top: 15px; }.case-card { grid-template-columns: 1fr; }.case-actions { justify-content: start; }.run-result { grid-template-columns: auto 1fr; }.run-result p { grid-column: 1 / -1; } }
</style>
