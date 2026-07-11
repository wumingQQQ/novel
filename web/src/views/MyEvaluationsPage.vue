<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listRoleWorkspaces } from '@/api/workspaces'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import WorkspaceCard from '@/components/WorkspaceCard.vue'
import type { RoleWorkspaceSummary } from '@/types/workspace'

const workspaces = ref<RoleWorkspaceSummary[]>([])
const loading = ref(false)
const error = ref('')
const requiresLogin = ref(!hasAccessToken())

/** 加载按公共角色分组的当前用户评测入口。 */
async function loadWorkspaces() {
  if (requiresLogin.value) return
  loading.value = true; error.value = ''
  try { workspaces.value = await listRoleWorkspaces() } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '我的评测暂时无法加载' } finally { loading.value = false }
}

onMounted(() => void loadWorkspaces())
</script>

<template>
  <main class="personal-page"><section class="intro"><p class="eyebrow">EVALUATION ARCHIVE</p><h1>一段角色，一组评测。</h1><p>选择一个角色，继续审核案例、运行评测，并从低分证据中生成有限的规则改进建议。</p></section><AuthenticationRequiredState v-if="requiresLogin" /><section v-else v-loading="loading"><div v-if="error" class="state"><p>{{ error }}</p><button @click="loadWorkspaces">重新尝试</button></div><div v-else-if="!loading && workspaces.length === 0" class="state"><p class="eyebrow">NO EVALUATION YET</p><h2>从角色大厅选择一个角色开始。</h2><RouterLink to="/">前往角色大厅 →</RouterLink></div><div v-else class="workspace-list"><WorkspaceCard v-for="workspace in workspaces" :key="workspace.character.id" :workspace="workspace" mode="evaluations" /></div></section></main>
</template>

<style scoped>
.personal-page { width: min(1000px, calc(100% - 48px)); margin: 0 auto; padding: 75px 0 100px; }.intro { max-width: 610px; margin-bottom: 39px; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.intro h1, .state h2 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 38px; font-weight: 600; }.intro > p:last-child { color: #737e82; font-size: 14px; line-height: 1.85; }.workspace-list { display: grid; gap: 13px; }.state { display: grid; min-height: 300px; place-content: center; justify-items: center; border: 1px dashed #ded7cf; border-radius: 9px; color: #788286; text-align: center; }.state h2 { font-size: 26px; }.state button { padding: 8px 13px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; }.state a { color: #a55d43; font-size: 13px; } @media (max-width: 600px) { .personal-page { width: min(100% - 30px, 1000px); padding-top: 47px; }.intro h1 { font-size: 32px; } }
</style>
