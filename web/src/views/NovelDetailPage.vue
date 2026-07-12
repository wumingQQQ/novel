<script setup lang="ts">
import { ArrowLeft, DocumentAdd, Files, Tickets } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { createJob, getNovelDetail, processJob } from '@/api/creation'
import { useAuth } from '@/composables/useAuth'
import type { NovelDetail } from '@/types/novel'

const route = useRoute()
const router = useRouter()
const { openAuthDialog } = useAuth()
const novel = ref<NovelDetail>()
const loading = ref(false)
const creatingJob = ref(false)
const error = ref('')
const protagonistName = ref('')
const targetName = ref('')
const novelId = computed(() => String(route.params.id))

/** 加载当前公开小说的独立详情，为扩展作者、摘要等字段预留展示位置。 */
async function loadNovel() {
  loading.value = true
  error.value = ''
  try {
    novel.value = await getNovelDetail(novelId.value)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '小说详情暂时无法加载'
  } finally {
    loading.value = false
  }
}

/** 在当前公开小说详情下创建属于当前用户的角色构建任务。 */
async function submitJob() {
  if (!novel.value || !hasAccessToken()) {
    openAuthDialog()
    return
  }
  if (!protagonistName.value.trim() || !targetName.value.trim() || creatingJob.value) return
  creatingJob.value = true
  error.value = ''
  try {
    const jobId = await createJob(novel.value.id, protagonistName.value.trim(), targetName.value.trim())
    await processJob(jobId)
    await router.push(`/creation/jobs/${jobId}`)
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) openAuthDialog()
    else error.value = reason instanceof Error ? reason.message : '任务创建失败'
  } finally {
    creatingJob.value = false
  }
}

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(value)) : '时间未知'
}

function formatFileSize(value: number | null) {
  if (!value) return '文本小说'
  return value < 1024 * 1024 ? `${Math.max(1, Math.round(value / 1024))} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(() => void loadNovel())
</script>

<template>
  <main class="novel-detail-page" v-loading="loading">
    <RouterLink class="back-link" to="/creation"><el-icon><ArrowLeft /></el-icon> 返回创作大厅</RouterLink>
    <section v-if="error && !novel" class="state-card"><p>{{ error }}</p><button type="button" @click="loadNovel">重新尝试</button></section>
    <template v-else-if="novel">
      <section class="novel-hero"><div class="hero-mark" aria-hidden="true">{{ novel.name.slice(0, 1) || '书' }}</div><div class="hero-copy"><p class="eyebrow">PUBLIC NOVEL</p><h1>{{ novel.name }}</h1><p class="filename">{{ novel.originalFilename || '小说文本' }}</p><p class="introduction">这是一部可用于角色构建的公共小说。基于它创建的任务独立归属于创建者。</p></div></section>
      <section class="facts"><article><el-icon><Files /></el-icon><b>{{ formatFileSize(novel.fileSize) }}</b><span>原始文本大小</span></article><article><el-icon><Tickets /></el-icon><b>{{ formatDate(novel.createTime) }}</b><span>上传至公共小说库</span></article></section>
      <section class="build-panel"><div><p class="eyebrow">BUILD JOB</p><h2>从这部小说构建角色</h2><p>填写故事主角与目标角色，后台会创建一项独立的构建任务。</p></div><form @submit.prevent="submitJob"><label for="protagonist-name">主角名称</label><el-input id="protagonist-name" v-model="protagonistName" placeholder="故事主角" /><label for="target-name">目标角色名称</label><el-input id="target-name" v-model="targetName" placeholder="准备构建的角色" /><button type="submit" :disabled="!protagonistName.trim() || !targetName.trim() || creatingJob"><el-icon><DocumentAdd /></el-icon>{{ creatingJob ? '正在创建…' : '创建并启动任务' }}</button></form></section>
      <p v-if="error" class="error-message">{{ error }}</p>
    </template>
  </main>
</template>

<style scoped>
.novel-detail-page { width: min(980px, calc(100% - 48px)); min-height: 600px; margin: 0 auto; padding: 43px 0 100px; }.back-link { display: inline-flex; gap: 6px; align-items: center; color: #617073; font-size: 13px; text-decoration: none; }.back-link:hover { color: #a55d43; }.novel-hero { display: grid; grid-template-columns: 310px 1fr; gap: 56px; align-items: center; margin-top: 37px; padding: 48px; border: 1px solid #e5dfd7; border-radius: 8px; background: linear-gradient(120deg, #f2e8df, #fbfaf7 70%); }.hero-mark { display: grid; height: 330px; place-items: center; border-radius: 8px; color: rgb(255 255 255 / 84%); background: linear-gradient(145deg, #2d3030, #a26151 42%, #dfb28e 43%, #c27660 65%, #44413f 66%, #2e393b); font-family: "Noto Serif SC", serif; font-size: 112px; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.hero-copy h1, .build-panel h2 { font-family: "Noto Serif SC", serif; font-weight: 600; }.hero-copy h1 { margin: 8px 0 12px; font-size: 43px; }.filename { color: #737d81; font-size: 13px; }.introduction { max-width: 470px; color: #4f5d60; font-size: 16px; line-height: 1.8; }.facts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin: 23px 0 46px; }.facts article { display: grid; grid-template-columns: auto 1fr; gap: 0 10px; align-items: center; padding: 18px; border: 1px solid #e8e4de; border-radius: 8px; background: #fff; }.facts :deep(.el-icon) { grid-row: span 2; color: #a55d43; font-size: 20px; }.facts b { color: #465558; font-size: 14px; }.facts span { color: #899195; font-size: 11px; }.build-panel { display: grid; grid-template-columns: 1fr minmax(300px, .85fr); gap: 42px; padding: 30px; border-top: 2px solid #384e4e; background: #f8f5f1; }.build-panel h2 { margin: 6px 0 9px; font-size: 29px; }.build-panel > div > p:last-child { color: #738084; font-size: 13px; line-height: 1.8; }.build-panel form { display: grid; gap: 7px; align-content: start; }.build-panel label { color: #647174; font-size: 11px; }.build-panel button { display: inline-flex; gap: 5px; align-items: center; justify-content: center; margin-top: 6px; padding: 10px 12px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 12px; }.build-panel button:disabled { cursor: not-allowed; opacity: .5; }.state-card { display: grid; min-height: 300px; place-content: center; justify-items: center; color: #727d81; }.state-card button { padding: 8px 13px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; }.error-message { color: #a14d42; font-size: 12px; }
@media (max-width: 720px) { .novel-detail-page { width: min(100% - 30px, 980px); padding-top: 29px; }.novel-hero, .build-panel { grid-template-columns: 1fr; gap: 26px; padding: 24px; }.hero-mark { height: 190px; font-size: 68px; }.hero-copy h1 { font-size: 35px; }.facts { grid-template-columns: 1fr; } }
</style>
