<script setup lang="ts">
import { ArrowRight, Clock, MagicStick } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listRoleAdjustRequests } from '@/api/role-adjust'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import type { RoleAdjustRequestSummary } from '@/types/role-adjust'

const requests = ref<RoleAdjustRequestSummary[]>([])
const loading = ref(false)
const error = ref('')
const requiresLogin = ref(!hasAccessToken())

/** 加载当前用户创建的角色调整请求。 */
async function loadRequests() {
  if (requiresLogin.value) return
  loading.value = true; error.value = ''
  try { requests.value = await listRoleAdjustRequests() } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '我的调整暂时无法加载' } finally { loading.value = false }
}

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '刚刚创建'
}

function statusLabel(status: RoleAdjustRequestSummary['status']) {
  return ({ PENDING: '等待生成', GENERATING: '正在生成', READY: '等待评审', CONFIRMED: '已确认', COMPLETED: '个人版本已创建', FAILED: '生成失败', CANCELLED: '已取消' })[status]
}

onMounted(() => void loadRequests())
</script>

<template>
  <main class="personal-page"><section class="intro"><p class="eyebrow">ROLE ADJUSTMENT ARCHIVE</p><h1>我正在塑造的角色。</h1><p>从角色详情提交调整目标。系统基于受保护的角色资产给出候选补丁，只有你确认的内容才会形成个人版本。</p></section><AuthenticationRequiredState v-if="requiresLogin" /><section v-else v-loading="loading"><div v-if="error" class="state"><p>{{ error }}</p><button @click="loadRequests">重新尝试</button></div><div v-else-if="!loading && requests.length === 0" class="state"><p class="eyebrow">NO ADJUSTMENT YET</p><h2>还没有角色调整请求。</h2><RouterLink to="/">去角色大厅发起调整 →</RouterLink></div><div v-else class="request-list"><RouterLink v-for="request in requests" :key="request.id" class="request-card" :to="`/my-evaluations/${request.id}`"><div class="mark" aria-hidden="true">{{ request.character.characterName.slice(0, 1) }}</div><div class="copy"><p class="novel">《{{ request.character.novelName || '未知作品' }}》</p><h2>{{ request.character.characterName }}</h2><p>{{ request.requirement }}</p><div class="meta"><span :class="['status', request.status.toLowerCase()]">{{ statusLabel(request.status) }}</span><span><el-icon><Clock /></el-icon>{{ formatDate(request.updateTime) }}</span></div></div><span class="aside">进入调整 <el-icon><ArrowRight /></el-icon></span></RouterLink></div></section></main>
</template>

<style scoped>
.personal-page { width: min(1000px, calc(100% - 48px)); margin: 0 auto; padding: 75px 0 100px; }.intro { max-width: 640px; margin-bottom: 39px; }.eyebrow,.novel { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.intro h1, .state h2 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 38px; font-weight: 600; }.intro > p:last-child { color: #737e82; font-size: 14px; line-height: 1.85; }.request-list { display: grid; gap: 13px; }.request-card { display: grid; grid-template-columns: 84px minmax(0, 1fr) auto; gap: 18px; align-items: center; padding: 16px; border: 1px solid #e8e3dc; border-radius: 8px; color: inherit; background: #fff; transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }.request-card:hover { border-color: #c8927f; box-shadow: 0 13px 31px rgb(63 43 36 / 8%); transform: translateY(-2px); }.mark { display: grid; width: 84px; height: 84px; place-items: center; border-radius: 6px; color: rgb(255 255 255 / 84%); background: linear-gradient(145deg, #e0cdbc, #aa735f 52%, #334547 53%); font-family: "Noto Serif SC", serif; font-size: 32px; }.copy h2 { margin: 4px 0; font-family: "Noto Serif SC", serif; font-size: 20px; }.copy > p:not(.novel) { margin: 0; color: #748084; font-size: 12px; line-height: 1.65; }.meta { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-top: 10px; color: #778287; font-size: 10px; }.meta span { display: inline-flex; gap: 4px; align-items: center; }.status { padding: 3px 6px; border-radius: 3px; font: 500 9px "DM Mono", monospace; }.ready,.completed { color: #3f6864; background: #e2efeb; }.pending,.generating { color: #9c654a; background: #f8e9df; }.failed,.cancelled { color: #8c7370; background: #eee9e6; }.aside { display: inline-flex; gap: 4px; align-items: center; color: #3c5a5b; font-size: 11px; font-weight: 600; }.state { display: grid; min-height: 300px; place-content: center; justify-items: center; border: 1px dashed #ded7cf; border-radius: 8px; color: #788286; text-align: center; }.state h2 { font-size: 26px; }.state button { padding: 8px 13px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; }.state a { color: #a55d43; font-size: 13px; } @media (max-width: 600px) { .personal-page { width: min(100% - 30px, 1000px); padding-top: 47px; }.intro h1 { font-size: 32px; }.request-card { grid-template-columns: 68px minmax(0, 1fr); gap: 13px; }.mark { width: 68px; height: 68px; font-size: 28px; }.aside { grid-column: 2; } }
</style>
