<script setup lang="ts">
import { Collection, Plus, RefreshRight, Search, UploadFilled } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { AuthenticationRequiredError, hasAccessToken } from '@/api/http'
import { listNovels, uploadNovel } from '@/api/creation'
import { useAuth } from '@/composables/useAuth'
import NovelCard from '@/components/NovelCard.vue'
import type { NovelSummary } from '@/types/novel'

type NovelScope = 'ALL' | 'MINE'

const { openAuthDialog } = useAuth()
const scope = ref<NovelScope>('ALL')
const keyword = ref('')
const appliedKeyword = ref('')
const novels = ref<NovelSummary[]>([])
const total = ref(0)
const page = ref(1)
const file = ref<File>()
const fileInput = ref<HTMLInputElement>()
const uploadDialogVisible = ref(false)
const libraryLoading = ref(false)
const uploading = ref(false)
const libraryError = ref('')
const actionError = ref('')
const pageSize = 12

/** 从公开小说库或当前用户上传列表读取一页卡片数据。 */
async function loadNovels(nextPage = 1) {
  libraryLoading.value = true
  libraryError.value = ''
  try {
    const result = await listNovels(scope.value, nextPage, pageSize, appliedKeyword.value)
    novels.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (reason) {
    if (reason instanceof AuthenticationRequiredError) {
      scope.value = 'ALL'
      openAuthDialog()
      return
    }
    libraryError.value = reason instanceof Error ? reason.message : '小说库暂时无法加载'
  } finally {
    libraryLoading.value = false
  }
}

/** 应用当前关键词并回到搜索结果的第一页。 */
function searchNovels() {
  appliedKeyword.value = keyword.value.trim()
  void loadNovels(1)
}

/** 切换公开小说库或我的上传列表。 */
function changeScope(nextScope: NovelScope) {
  if (nextScope === 'MINE' && !hasAccessToken()) {
    openAuthDialog()
    return
  }
  scope.value = nextScope
  void loadNovels(1)
}

/** 打开上传弹窗前先确认当前浏览器已保存登录凭证。 */
function openUploadDialog() {
  if (!hasAccessToken()) {
    openAuthDialog()
    return
  }
  actionError.value = ''
  uploadDialogVisible.value = true
}

function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0]
}

/** 上传成功后切换到“我的上传”，新小说可从列表进入详情页创建任务。 */
async function submitUpload() {
  if (!file.value || uploading.value) return
  if (!file.value.name.endsWith('.txt') || file.value.size > 10 * 1024 * 1024) {
    actionError.value = '请上传不超过 10MB 的 .txt 小说文件'
    return
  }
  uploading.value = true
  actionError.value = ''
  try {
    await uploadNovel(file.value)
    file.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    uploadDialogVisible.value = false
    scope.value = 'MINE'
    await loadNovels(1)
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

function formatFileSize(value: number | undefined) {
  if (!value) return ''
  return value < 1024 * 1024 ? `${Math.max(1, Math.round(value / 1024))} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(() => void loadNovels())
</script>

<template>
  <main class="creation-page">
    <section class="creation-intro"><p class="eyebrow">PUBLIC NOVEL ARCHIVE</p><h1>从一部小说开始。</h1><p>每部作品都可以成为角色构建的起点。进入小说详情，填写人物名称，即可在原作基础上创建角色构建任务。</p></section>
    <section class="library-content" v-loading="libraryLoading" aria-live="polite">
      <header class="section-heading"><div><p class="eyebrow">THE OPEN SHELF</p><h2>{{ appliedKeyword ? '搜索结果' : '已上传小说' }}</h2></div><form class="search-form" @submit.prevent="searchNovels"><el-input v-model="keyword" aria-label="搜索小说" placeholder="搜索小说名、文件名" clearable @clear="searchNovels"><template #prefix><el-icon><Search /></el-icon></template></el-input><button type="submit">搜索</button></form></header>
      <div class="scope-tabs" role="tablist" aria-label="小说范围"><button :class="{ active: scope === 'ALL' }" type="button" role="tab" :aria-selected="scope === 'ALL'" @click="changeScope('ALL')">全部小说</button><button :class="{ active: scope === 'MINE' }" type="button" role="tab" :aria-selected="scope === 'MINE'" @click="changeScope('MINE')">我的上传</button></div>
      <div v-if="libraryError" class="state-card error-state"><p>{{ libraryError }}</p><button type="button" @click="loadNovels(page)"><el-icon><RefreshRight /></el-icon>重新尝试</button></div>
      <template v-else><div class="novel-grid"><button class="upload-card" type="button" @click="openUploadDialog"><span class="upload-portrait"><el-icon><Plus /></el-icon></span><span class="upload-copy"><span class="source-label">ADD TO ARCHIVE</span><b>上传小说</b><span>选择一部新作品</span></span></button><NovelCard v-for="novel in novels" :key="novel.id" :novel="novel" /></div><div v-if="!libraryLoading && novels.length === 0" class="state-card compact-state"><el-icon><Collection /></el-icon><h2>{{ appliedKeyword ? '没有找到匹配的小说。' : scope === 'MINE' ? '你还没有上传小说。' : '小说库暂时为空。' }}</h2><span>{{ appliedKeyword ? '换一个关键词继续查找。' : scope === 'MINE' ? '上传一部作品后，即可进入详情创建角色构建任务。' : '第一部上传的小说会从这里开始。' }}</span></div><el-pagination v-if="total > pageSize" v-model:current-page="page" class="pagination" layout="prev, pager, next" :page-size="pageSize" :total="total" @current-change="loadNovels" /></template>
    </section>
    <p v-if="actionError" class="action-error">{{ actionError }}</p>
    <el-dialog v-model="uploadDialogVisible" width="min(470px, calc(100% - 32px))" align-center><template #header><span class="dialog-title">上传小说</span></template><section class="upload-dialog"><input ref="fileInput" class="file-input" type="file" accept=".txt,text/plain" @change="chooseFile"><button type="button" class="choose-file" @click="fileInput?.click()"><el-icon><UploadFilled /></el-icon><span>{{ file ? '重新选择文件' : '选择文本小说' }}</span></button><div v-if="file" class="selected-file"><el-icon><Collection /></el-icon><span><b>{{ file.name }}</b><small>{{ formatFileSize(file.size) }}</small></span></div></section><template #footer><el-button @click="uploadDialogVisible = false">取消</el-button><el-button type="primary" :disabled="!file" :loading="uploading" @click="submitUpload">上传小说</el-button></template></el-dialog>
  </main>
</template>

<style scoped>
.creation-page { width: min(1180px, calc(100% - 48px)); margin: 0 auto; padding: 82px 0 100px; }.creation-intro { max-width: 685px; margin: 0 auto 69px; text-align: center; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.creation-intro h1, .section-heading h2, .state-card h2 { font-family: "Noto Serif SC", serif; font-weight: 600; }.creation-intro h1 { margin: 14px 0 13px; font-size: clamp(35px, 5vw, 55px); line-height: 1.2; }.creation-intro > p:last-child { max-width: 560px; margin: auto; color: #727d81; font-size: 14px; line-height: 1.85; }.library-content { min-height: 300px; }.section-heading { display: flex; gap: 18px; align-items: end; justify-content: space-between; margin-bottom: 17px; }.section-heading h2 { margin: 5px 0 0; font-size: 27px; }.search-form { display: flex; gap: 6px; width: 320px; padding: 4px; border: 1px solid #e6e1d9; border-radius: 8px; background: #fff; box-shadow: 0 10px 25px rgb(48 44 37 / 4%); }.search-form :deep(.el-input__wrapper) { box-shadow: none; }.search-form button, .state-card button { display: inline-flex; gap: 5px; align-items: center; justify-content: center; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 11px; }.search-form button { padding: 0 12px; }.scope-tabs { display: flex; gap: 20px; margin: 7px 0 17px; border-bottom: 1px solid #e8e2da; }.scope-tabs button { position: relative; padding: 0 0 10px; border: 0; color: #7d878a; background: transparent; cursor: pointer; font-size: 12px; }.scope-tabs button.active { color: #374b4c; font-weight: 600; }.scope-tabs button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: #ae6044; content: ""; }.novel-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }.upload-card { display: block; min-height: 260px; overflow: hidden; padding: 0; border: 1px dashed #cdbfb4; border-radius: 10px; color: inherit; background: #fffdfa; cursor: pointer; text-align: left; transition: transform .25s ease, box-shadow .25s ease, border-color .25s ease; }.upload-card:hover { border-color: #b7755f; box-shadow: 0 16px 38px rgb(71 47 38 / 8%); transform: translateY(-4px); }.upload-portrait { display: grid; height: 126px; place-items: center; color: rgb(255 255 255 / 85%); background: linear-gradient(145deg, #d8c7b9, #b77d66 53%, #556567 54%, #344548 100%); }.upload-portrait :deep(.el-icon) { display: grid; width: 69px; height: 69px; place-items: center; border: 1px solid rgb(255 255 255 / 55%); border-radius: 50%; background: rgb(43 48 48 / 16%); font-size: 29px; }.upload-copy { display: grid; gap: 5px; padding: 17px; }.source-label { color: #a96247; font: 500 10px "DM Mono", monospace; letter-spacing: .08em; }.upload-copy b { font-family: "Noto Serif SC", serif; font-size: 19px; font-weight: 600; }.upload-copy > span:not(.source-label) { min-height: 40px; color: #6d787c; font-size: 12px; line-height: 1.65; }.pagination { display: flex; justify-content: center; margin-top: 38px; }.pagination :deep(.is-active) { color: #a55d43; }.state-card { display: grid; min-height: 260px; place-content: center; justify-items: center; padding: 30px; border: 1px dashed #dcd5cd; border-radius: 10px; text-align: center; }.compact-state { margin-top: 18px; }.state-card :deep(.el-icon) { color: #a55d43; font-size: 22px; }.state-card h2 { margin: 8px 0; font-size: 25px; }.state-card span, .state-card p:not(.eyebrow) { color: #7b8589; font-size: 13px; }.state-card button { height: 34px; margin-top: 15px; padding: 0 11px; }.error-state { border-color: #dfb4a6; background: #fffaf8; }.action-error { margin: 14px 0 0; color: #a14d42; font-size: 12px; text-align: center; }.dialog-title { font-family: "Noto Serif SC", serif; font-size: 20px; }.upload-dialog { display: grid; min-height: 150px; place-content: center; justify-items: center; gap: 13px; padding: 10px; }.file-input { position: absolute; width: 1px; height: 1px; opacity: 0; }.choose-file { display: inline-flex; gap: 10px; align-items: center; justify-content: center; width: min(310px, 100%); min-height: 96px; padding: 14px; border: 1px dashed #cba18e; border-radius: 7px; color: #8f5d49; background: #fffdfa; cursor: pointer; font-size: 13px; }.choose-file :deep(.el-icon) { font-size: 21px; }.selected-file { display: flex; gap: 10px; align-items: center; width: min(310px, 100%); padding: 11px 13px; border-left: 2px solid #b77d66; background: #f9f5f0; }.selected-file :deep(.el-icon) { color: #a55d43; font-size: 17px; }.selected-file b, .selected-file small { display: block; }.selected-file b { overflow: hidden; color: #526164; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.selected-file small { margin-top: 3px; color: #879094; font-size: 10px; }.el-button--primary { background: #344b4c; border-color: #344b4c; }
@media (max-width: 900px) { .section-heading { align-items: start; flex-direction: column; }.search-form { width: 100%; }.novel-grid { grid-template-columns: repeat(2, 1fr); } } @media (max-width: 580px) { .creation-page { width: min(100% - 30px, 1180px); padding-top: 53px; }.creation-intro { margin-bottom: 48px; }.search-form { min-height: 42px; }.novel-grid { grid-template-columns: 1fr; } }
</style>
