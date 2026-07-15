<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import type { RolePublicSummary } from '@/types/role'

defineProps<{ role: RolePublicSummary; featured?: boolean }>()

/** 将完成时间格式化为大厅中简短的中文日期。 */
function formatDate(value: string | null) {
  if (!value) return '已完成构建'
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date(value))
}

/** 卡片只展示角色侧影摘要，完整介绍留在详情页阅读。 */
function briefIntroduction(value: string, featured?: boolean) {
  const text = value?.trim()
  if (!text) return '公开侧影待完善。'
  const limit = featured ? 56 : 34
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}
</script>

<template>
  <RouterLink :class="['role-card', { featured }]" :to="`/roles/${role.id}`">
    <div class="card-portrait" aria-hidden="true"><span>{{ role.characterName.slice(0, 1) }}</span></div>
    <div class="card-copy">
      <p class="novel-name">{{ role.novelName || '未知作品' }}</p><h3>{{ role.characterName }}</h3><p class="introduction">{{ briefIntroduction(role.introduction, featured) }}</p>
      <div class="role-meta"><span>{{ role.exampleCount }} 段互动素材</span><span>{{ role.ruleCount }} 条角色线索</span></div>
      <span class="view-link">查看角色 <el-icon><ArrowRight /></el-icon></span>
    </div>
    <time v-if="!featured" class="completed-time">{{ formatDate(role.completedTime) }}</time>
  </RouterLink>
</template>

<style scoped>
.role-card { position: relative; display: block; min-height: 260px; overflow: hidden; border: 1px solid #e9e5dd; border-radius: 10px; background: #fff; transition: transform .25s ease, box-shadow .25s ease, border-color .25s ease; }.role-card:hover { border-color: #c89582; box-shadow: 0 16px 38px rgb(71 47 38 / 10%); transform: translateY(-4px); }
.card-portrait { display: grid; height: 126px; place-items: center; color: rgb(255 255 255 / 82%); background: linear-gradient(145deg, #e9d9cc 0%, #b77d66 53%, #3f4a4e 54%, #29383b 100%); }.role-card:nth-child(3n+2) .card-portrait { background: linear-gradient(145deg, #e6e4d5 0%, #8e9c81 50%, #455758 51%, #263639 100%); }.role-card:nth-child(3n) .card-portrait { background: linear-gradient(145deg, #ebddd5 0%, #bd9072 50%, #584440 51%, #313536 100%); }.card-portrait span { width: 69px; height: 69px; display: grid; place-items: center; border: 1px solid rgb(255 255 255 / 55%); border-radius: 50%; background: rgb(43 48 48 / 16%); font-family: "Noto Serif SC", serif; font-size: 29px; }
.card-copy { padding: 17px 17px 15px; }.novel-name { margin: 0; color: #a96247; font: 500 10px "DM Mono", monospace; letter-spacing: .08em; }.card-copy h3 { margin: 5px 0 7px; font-family: "Noto Serif SC", serif; font-size: 19px; font-weight: 600; }.introduction { display: -webkit-box; min-height: 40px; margin: 0; overflow: hidden; color: #6d787c; font-size: 12px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.role-meta { display: flex; gap: 10px; margin-top: 13px; color: #8c9598; font-size: 10px; }.view-link { display: inline-flex; gap: 5px; align-items: center; margin-top: 13px; color: #3e5b5c; font-size: 11px; font-weight: 600; }.view-link :deep(.el-icon) { font-size: 13px; }.completed-time { position: absolute; top: 10px; right: 10px; padding: 3px 6px; border-radius: 3px; color: #fff; background: rgb(42 52 53 / 66%); font-size: 9px; }
.featured { display: grid; grid-template-columns: minmax(230px, 1fr) 1.3fr; min-height: 300px; border: 0; background: #f0e4da; }.featured .card-portrait { height: 100%; min-height: 300px; background: linear-gradient(145deg, #302f2f 0%, #a76655 42%, #deb18d 43%, #c17662 65%, #4a4140 66%, #31383a 100%); }.featured .card-portrait span { width: 115px; height: 115px; font-size: 48px; }.featured .card-copy { align-self: center; padding: 30px; }.featured .card-copy h3 { font-size: 28px; }.featured .introduction { min-height: auto; font-size: 13px; -webkit-line-clamp: 3; }.featured .role-meta { margin-top: 18px; }.featured .view-link { margin-top: 22px; padding: 9px 12px; border-radius: 5px; color: #fff; background: #344a4b; }
@media (max-width: 650px) { .featured { grid-template-columns: 1fr; }.featured .card-portrait { min-height: 170px; }.featured .card-copy { padding: 22px; }.featured .card-copy h3 { font-size: 24px; } }
</style>
