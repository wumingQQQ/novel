<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import type { NovelSummary } from '@/types/novel'

defineProps<{ novel: NovelSummary }>()

/** 将上传时间格式化为小说卡片右上角的简短标签。 */
function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date(value)) : '已上传'
}

function formatFileSize(value: number | null) {
  if (!value) return '文本小说'
  return value < 1024 * 1024 ? `${Math.max(1, Math.round(value / 1024))} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <RouterLink class="novel-card" :to="`/creation/novels/${novel.id}`">
    <div class="card-portrait" aria-hidden="true"><span>{{ novel.name.slice(0, 1) || '书' }}</span></div>
    <div class="card-copy"><p class="source-label">{{ novel.mine ? 'MY UPLOAD' : 'PUBLIC NOVEL' }}</p><h3>{{ novel.name }}</h3><p class="filename">{{ novel.originalFilename || '小说文本' }}</p><div class="novel-meta"><span>{{ formatFileSize(novel.fileSize) }}</span><span>{{ novel.mine ? '由我上传' : '公共小说库' }}</span></div><span class="view-link">查看小说 <el-icon><ArrowRight /></el-icon></span></div>
    <time class="uploaded-time">{{ formatDate(novel.createTime) }}</time>
  </RouterLink>
</template>

<style scoped>
.novel-card { position: relative; display: block; min-height: 260px; overflow: hidden; border: 1px solid #e9e5dd; border-radius: 10px; color: inherit; background: #fff; text-decoration: none; transition: transform .25s ease, box-shadow .25s ease, border-color .25s ease; }.novel-card:hover { border-color: #c89582; box-shadow: 0 16px 38px rgb(71 47 38 / 10%); transform: translateY(-4px); }.card-portrait { display: grid; height: 126px; place-items: center; color: rgb(255 255 255 / 82%); background: linear-gradient(145deg, #e9d9cc 0%, #b77d66 53%, #3f4a4e 54%, #29383b 100%); }.novel-card:nth-child(3n+2) .card-portrait { background: linear-gradient(145deg, #e6e4d5 0%, #8e9c81 50%, #455758 51%, #263639 100%); }.novel-card:nth-child(3n) .card-portrait { background: linear-gradient(145deg, #ebddd5 0%, #bd9072 50%, #584440 51%, #313536 100%); }.card-portrait span { display: grid; width: 69px; height: 69px; place-items: center; border: 1px solid rgb(255 255 255 / 55%); border-radius: 50%; background: rgb(43 48 48 / 16%); font-family: "Noto Serif SC", serif; font-size: 29px; }.card-copy { padding: 17px 17px 15px; }.source-label { margin: 0; color: #a96247; font: 500 10px "DM Mono", monospace; letter-spacing: .08em; }.card-copy h3 { margin: 5px 0 7px; overflow: hidden; font-family: "Noto Serif SC", serif; font-size: 19px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.filename { min-height: 40px; margin: 0; overflow: hidden; color: #6d787c; font-size: 12px; line-height: 1.65; }.novel-meta { display: flex; gap: 10px; margin-top: 13px; color: #8c9598; font-size: 10px; }.view-link { display: inline-flex; gap: 5px; align-items: center; margin-top: 13px; color: #3e5b5c; font-size: 11px; font-weight: 600; }.view-link :deep(.el-icon) { font-size: 13px; }.uploaded-time { position: absolute; top: 10px; right: 10px; padding: 3px 6px; border-radius: 3px; color: #fff; background: rgb(42 52 53 / 66%); font-size: 9px; }
</style>
