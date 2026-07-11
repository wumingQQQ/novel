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

/** 读取当前用户参与过的角色工作区。 */
async function loadWorkspaces() {
  if (requiresLogin.value) return
  loading.value = true
  error.value = ''
  try { workspaces.value = await listRoleWorkspaces() } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '我的角色暂时无法加载' } finally { loading.value = false }
}

onMounted(() => void loadWorkspaces())
</script>

<template>
  <main class="personal-page"><section class="intro"><p class="eyebrow">MY CHARACTER JOURNEY</p><h1>我参与过的角色。</h1><p>公共角色仍属于所有人；这里记录的是你为他们建立的评测与个人版本轨迹。</p></section><AuthenticationRequiredState v-if="requiresLogin" /><section v-else v-loading="loading"><div v-if="error" class="state"><p>{{ error }}</p><button @click="loadWorkspaces">重新尝试</button></div><div v-else-if="!loading && workspaces.length === 0" class="state"><p class="eyebrow">NO JOURNEY YET</p><h2>你还没有参与任何角色评测。</h2><RouterLink to="/">去角色大厅看看 →</RouterLink></div><div v-else class="workspace-list"><WorkspaceCard v-for="workspace in workspaces" :key="workspace.character.id" :workspace="workspace" mode="roles" /></div></section></main>
</template>

<style scoped>
.personal-page { width: min(1000px, calc(100% - 48px)); margin: 0 auto; padding: 75px 0 100px; }.intro { max-width: 560px; margin-bottom: 39px; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.intro h1, .state h2 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 38px; font-weight: 600; }.intro > p:last-child { color: #737e82; font-size: 14px; line-height: 1.85; }.workspace-list { display: grid; gap: 13px; }.state { display: grid; min-height: 300px; place-content: center; justify-items: center; border: 1px dashed #ded7cf; border-radius: 9px; color: #788286; text-align: center; }.state h2 { font-size: 26px; }.state button { padding: 8px 13px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; }.state a { color: #a55d43; font-size: 13px; } @media (max-width: 600px) { .personal-page { width: min(100% - 30px, 1000px); padding-top: 47px; }.intro h1 { font-size: 32px; } }
</style>
