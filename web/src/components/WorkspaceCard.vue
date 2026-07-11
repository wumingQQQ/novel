<script setup lang="ts">
import { ArrowRight, DocumentChecked, FolderOpened } from '@element-plus/icons-vue'
import type { RoleWorkspaceSummary } from '@/types/workspace'

defineProps<{ workspace: RoleWorkspaceSummary; mode: 'roles' | 'evaluations' }>()

/** 将最新评测时间格式化为简短日期。 */
function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date(value)) : '尚未开始'
}
</script>

<template>
  <RouterLink class="workspace-card" :to="`/my-evaluations/${workspace.character.id}`">
    <div class="mark" aria-hidden="true">{{ workspace.character.characterName.slice(0, 1) }}</div>
    <div class="copy"><p class="novel">《{{ workspace.character.novelName || '未知作品' }}》</p><h2>{{ workspace.character.characterName }}</h2><p>{{ workspace.character.introduction }}</p><div class="meta"><span><el-icon><DocumentChecked /></el-icon>{{ workspace.evaluationCount }} 次评测</span><span v-if="workspace.latestVersionNo"><el-icon><FolderOpened /></el-icon>个人版本 v{{ workspace.latestVersionNo }}</span><span v-else>尚未创建个人版本</span></div></div>
    <div class="aside"><time>{{ formatDate(workspace.latestEvaluationTime) }}</time><span>{{ mode === 'roles' ? '继续评测' : '查看评测' }} <el-icon><ArrowRight /></el-icon></span></div>
  </RouterLink>
</template>

<style scoped>
.workspace-card { display: grid; grid-template-columns: 96px 1fr auto; gap: 18px; align-items: center; padding: 16px; border: 1px solid #e8e3dc; border-radius: 9px; background: #fff; transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }.workspace-card:hover { border-color: #c8927f; box-shadow: 0 13px 31px rgb(63 43 36 / 8%); transform: translateY(-2px); }.mark { display: grid; width: 96px; height: 96px; place-items: center; border-radius: 6px; color: rgb(255 255 255 / 84%); background: linear-gradient(145deg, #e0cdbc, #aa735f 52%, #334547 53%); font-family: "Noto Serif SC", serif; font-size: 36px; }.novel { margin: 0; color: #a55d43; font: 500 10px "DM Mono", monospace; letter-spacing: .07em; }.copy h2 { margin: 4px 0; font-family: "Noto Serif SC", serif; font-size: 20px; }.copy > p:not(.novel) { margin: 0; color: #748084; font-size: 12px; line-height: 1.65; }.meta { display: flex; gap: 13px; margin-top: 10px; color: #778287; font-size: 10px; }.meta span { display: inline-flex; gap: 4px; align-items: center; }.aside { display: grid; gap: 11px; justify-items: end; color: #899296; font-size: 10px; }.aside span { display: inline-flex; gap: 4px; align-items: center; color: #3c5a5b; font-size: 11px; font-weight: 600; }
@media (max-width: 620px) { .workspace-card { grid-template-columns: 68px 1fr; gap: 13px; }.mark { width: 68px; height: 68px; font-size: 28px; }.aside { grid-column: 2; justify-items: start; grid-template-columns: 1fr 1fr; }.meta { flex-wrap: wrap; } }
</style>
