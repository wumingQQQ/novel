<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listPersonalRoles } from '@/api/personal-roles'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import RoleCard from '@/components/RoleCard.vue'
import type { PersonalRoleSummary } from '@/types/personal-role'

const roles = ref<PersonalRoleSummary[]>([])
const loading = ref(false)
const error = ref('')
const requiresLogin = ref(!hasAccessToken())

/** 读取当前用户已经拥有个人版本的公共角色。 */
async function loadRoles() {
  if (requiresLogin.value) return
  loading.value = true
  error.value = ''
  try {
    roles.value = await listPersonalRoles()
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true
    else error.value = reason instanceof Error ? reason.message : '我的角色暂时无法加载'
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadRoles())
</script>

<template>
  <main class="personal-page">
    <section class="intro">
      <p class="eyebrow">MY CHARACTER JOURNEY</p>
      <h1>我调整过的角色。</h1>
      <p>公共角色属于所有人；这里保留你留下过调整轨迹的角色。进入详情后，仍可使用公共版，或选择任一个人版本开始对话。</p>
    </section>
    <AuthenticationRequiredState v-if="requiresLogin" />
    <section v-else v-loading="loading" class="role-content">
      <div v-if="error" class="state-card error-state"><p>{{ error }}</p><button type="button" @click="loadRoles">重新尝试</button></div>
      <div v-else-if="!loading && roles.length === 0" class="state-card"><p class="eyebrow">NO PERSONAL VERSION YET</p><h2>你还没有确认任何个人角色调整。</h2><RouterLink to="/">去角色大厅看看 →</RouterLink></div>
      <div v-else class="role-grid"><RoleCard v-for="role in roles" :key="role.versionId" :role="role.character" /></div>
    </section>
  </main>
</template>

<style scoped>
.personal-page { width: min(1180px, calc(100% - 48px)); margin: 0 auto; padding: 75px 0 100px; }
.intro { max-width: 650px; margin-bottom: 39px; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.intro h1, .state-card h2 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 38px; font-weight: 600; }.intro > p:last-child { color: #737e82; font-size: 14px; line-height: 1.85; }
.role-content { min-height: 300px; }.role-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }.state-card { display: grid; min-height: 300px; place-content: center; justify-items: center; border: 1px dashed #ded7cf; border-radius: 8px; color: #788286; text-align: center; }.state-card h2 { font-size: 26px; }.state-card button { padding: 8px 13px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; }.state-card a { color: #a55d43; font-size: 13px; }.error-state { border-color: #dfb4a6; background: #fffaf8; }
@media (max-width: 850px) { .role-grid { grid-template-columns: repeat(2, 1fr); } } @media (max-width: 580px) { .personal-page { width: min(100% - 30px, 1180px); padding-top: 47px; }.intro h1 { font-size: 32px; }.role-grid { grid-template-columns: 1fr; } }
</style>
