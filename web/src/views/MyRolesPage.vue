<script setup lang="ts">
import { ArrowRight, Clock, FolderOpened } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listPersonalRoles } from '@/api/personal-roles'
import AuthenticationRequiredState from '@/components/AuthenticationRequiredState.vue'
import type { PersonalRoleSummary } from '@/types/personal-role'

const roles = ref<PersonalRoleSummary[]>([])
const loading = ref(false)
const error = ref('')
const requiresLogin = ref(!hasAccessToken())

/** 读取当前用户每个公共角色下的最新个人版本。 */
async function loadRoles() {
  if (requiresLogin.value) return
  loading.value = true
  error.value = ''
  try { roles.value = await listPersonalRoles() } catch (reason) { if (reason instanceof AuthenticationRequiredError) requiresLogin.value = true; else error.value = reason instanceof Error ? reason.message : '我的角色暂时无法加载' } finally { loading.value = false }
}

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date(value)) : '刚刚创建'
}

onMounted(() => void loadRoles())
</script>

<template>
  <main class="personal-page"><section class="intro"><p class="eyebrow">MY CHARACTER JOURNEY</p><h1>我调整过的角色。</h1><p>公共角色属于所有人；这里保留你已确认的最新个人版本。进入角色详情后，可以选择用该版本开始对话。</p></section><AuthenticationRequiredState v-if="requiresLogin" /><section v-else v-loading="loading"><div v-if="error" class="state"><p>{{ error }}</p><button @click="loadRoles">重新尝试</button></div><div v-else-if="!loading && roles.length === 0" class="state"><p class="eyebrow">NO PERSONAL VERSION YET</p><h2>你还没有确认任何个人角色调整。</h2><RouterLink to="/">去角色大厅看看 →</RouterLink></div><div v-else class="role-list"><RouterLink v-for="role in roles" :key="role.versionId" class="role-card" :to="{ path: `/roles/${role.character.id}`, query: { userRoleVersionId: role.versionId, versionNo: role.versionNo } }"><div class="mark" aria-hidden="true">{{ role.character.characterName.slice(0, 1) }}</div><div class="copy"><p class="novel">《{{ role.character.novelName || '未知作品' }}》</p><h2>{{ role.character.characterName }}</h2><p>{{ role.character.introduction }}</p><div class="meta"><span><el-icon><FolderOpened /></el-icon>个人版本 v{{ role.versionNo }}</span><span><el-icon><Clock /></el-icon>{{ formatDate(role.createTime) }}</span></div></div><span class="aside">查看角色 <el-icon><ArrowRight /></el-icon></span></RouterLink></div></section></main>
</template>

<style scoped>
.personal-page { width: min(1000px, calc(100% - 48px)); margin: 0 auto; padding: 75px 0 100px; }.intro { max-width: 620px; margin-bottom: 39px; }.eyebrow,.novel { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.intro h1, .state h2 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 38px; font-weight: 600; }.intro > p:last-child { color: #737e82; font-size: 14px; line-height: 1.85; }.role-list { display: grid; gap: 13px; }.role-card { display: grid; grid-template-columns: 96px minmax(0, 1fr) auto; gap: 18px; align-items: center; padding: 16px; border: 1px solid #e8e3dc; border-radius: 8px; color: inherit; background: #fff; transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }.role-card:hover { border-color: #c8927f; box-shadow: 0 13px 31px rgb(63 43 36 / 8%); transform: translateY(-2px); }.mark { display: grid; width: 96px; height: 96px; place-items: center; border-radius: 6px; color: rgb(255 255 255 / 84%); background: linear-gradient(145deg, #e0cdbc, #aa735f 52%, #334547 53%); font-family: "Noto Serif SC", serif; font-size: 36px; }.copy h2 { margin: 4px 0; font-family: "Noto Serif SC", serif; font-size: 20px; }.copy > p:not(.novel) { margin: 0; color: #748084; font-size: 12px; line-height: 1.65; }.meta { display: flex; flex-wrap: wrap; gap: 13px; margin-top: 10px; color: #778287; font-size: 10px; }.meta span,.aside { display: inline-flex; gap: 4px; align-items: center; }.aside { color: #3c5a5b; font-size: 11px; font-weight: 600; }.state { display: grid; min-height: 300px; place-content: center; justify-items: center; border: 1px dashed #ded7cf; border-radius: 8px; color: #788286; text-align: center; }.state h2 { font-size: 26px; }.state button { padding: 8px 13px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; }.state a { color: #a55d43; font-size: 13px; } @media (max-width: 600px) { .personal-page { width: min(100% - 30px, 1000px); padding-top: 47px; }.intro h1 { font-size: 32px; }.role-card { grid-template-columns: 68px minmax(0, 1fr); gap: 13px; }.mark { width: 68px; height: 68px; font-size: 28px; }.aside { grid-column: 2; } }
</style>
