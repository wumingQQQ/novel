<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { listPublicRoles } from '@/api/roles'
import RoleCard from '@/components/RoleCard.vue'
import type { RolePublicSummary } from '@/types/role'

const keyword = ref('')
const appliedKeyword = ref('')
const roles = ref<RolePublicSummary[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const pageSize = 12
const featuredRole = computed(() => roles.value[0])
const remainingRoles = computed(() => roles.value.slice(1))

/** 从公共脱敏接口加载角色大厅内容。 */
async function loadRoles(nextPage = 1) {
  loading.value = true
  error.value = ''
  try {
    const result = await listPublicRoles(appliedKeyword.value, nextPage, pageSize)
    roles.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '角色大厅暂时无法加载'
  } finally {
    loading.value = false
  }
}

/** 应用用户输入的关键词并回到第一页。 */
function searchRoles() {
  appliedKeyword.value = keyword.value
  void loadRoles(1)
}

onMounted(() => void loadRoles())
</script>

<template>
  <main class="hall-page">
    <section class="hall-intro">
      <p class="eyebrow">PUBLIC CHARACTER ARCHIVE</p>
      <h1>值得和他们说话的人。</h1>
      <p class="subtitle">每一位角色都从小说原文中构建而来。你可以浏览他们的公开侧影，并决定是否开始自己的评测。</p>
      <form class="search-form" @submit.prevent="searchRoles">
        <el-input v-model="keyword" aria-label="搜索角色或小说" placeholder="搜索角色名、小说名" clearable @clear="searchRoles"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <button type="submit">开始探索</button>
      </form>
    </section>
    <section v-loading="loading" class="hall-content" aria-live="polite">
      <div v-if="error" class="state-card error-state"><p>{{ error }}</p><button type="button" @click="loadRoles(page)">重新尝试</button></div>
      <div v-else-if="!loading && roles.length === 0" class="state-card"><p class="eyebrow">ARCHIVE IS QUIET</p><h2>暂时没有可公开浏览的角色。</h2><span>已完成构建的角色会在这里出现。</span></div>
      <template v-else>
        <section v-if="featuredRole" class="featured-section"><div class="section-heading"><div><p class="eyebrow">CURATED RECENTLY</p><h2>{{ appliedKeyword ? '匹配到的角色' : '最近完成构建' }}</h2></div><span>{{ total }} 位公共角色</span></div><RoleCard :role="featuredRole" featured /></section>
        <section v-if="remainingRoles.length" class="archive-section"><div class="section-heading"><div><p class="eyebrow">THE OPEN ARCHIVE</p><h2>继续遇见更多角色</h2></div><span>公开预览不展示完整角色资产</span></div><div class="role-grid"><RoleCard v-for="role in remainingRoles" :key="role.id" :role="role" /></div><el-pagination v-if="total > pageSize" v-model:current-page="page" class="pagination" layout="prev, pager, next" :page-size="pageSize" :total="total" @current-change="loadRoles" /></section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.hall-page { width: min(1180px, calc(100% - 48px)); margin: 0 auto; padding: 82px 0 100px; }.hall-intro { max-width: 685px; margin: 0 auto 69px; text-align: center; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.hall-intro h1, .section-heading h2, .state-card h2 { font-family: "Noto Serif SC", serif; font-weight: 600; }.hall-intro h1 { margin: 14px 0 13px; font-size: clamp(35px, 5vw, 55px); letter-spacing: -.04em; line-height: 1.2; }.subtitle { max-width: 510px; margin: auto; color: #727d81; font-size: 14px; line-height: 1.85; }.search-form { display: flex; gap: 8px; width: min(510px, 100%); margin: 29px auto 0; padding: 5px; border: 1px solid #e6e1d9; border-radius: 8px; background: #fff; box-shadow: 0 10px 25px rgb(48 44 37 / 4%); }.search-form :deep(.el-input__wrapper) { box-shadow: none; }.search-form button, .state-card button { flex: none; padding: 0 18px; border: 0; border-radius: 5px; color: white; background: #344b4c; cursor: pointer; font-size: 13px; transition: background .2s ease; }.search-form button:hover, .state-card button:hover { background: #253a3b; }.hall-content { min-height: 300px; }.featured-section { margin-bottom: 74px; }.section-heading { display: flex; gap: 18px; align-items: end; justify-content: space-between; margin-bottom: 17px; }.section-heading h2 { margin: 5px 0 0; font-size: 27px; }.section-heading > span { padding-bottom: 4px; color: #879094; font-size: 11px; }.role-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }.pagination { display: flex; justify-content: center; margin-top: 38px; }.pagination :deep(.is-active) { color: #a55d43; }.state-card { display: grid; min-height: 260px; place-content: center; justify-items: center; padding: 30px; border: 1px dashed #dcd5cd; border-radius: 10px; text-align: center; }.state-card h2 { margin: 8px 0; font-size: 25px; }.state-card span, .state-card p:not(.eyebrow) { color: #7b8589; font-size: 13px; }.state-card button { height: 34px; margin-top: 15px; }.error-state { border-color: #dfb4a6; background: #fffaf8; }
@media (max-width: 850px) { .role-grid { grid-template-columns: repeat(2, 1fr); } } @media (max-width: 580px) { .hall-page { width: min(100% - 30px, 1180px); padding-top: 53px; }.hall-intro { margin-bottom: 48px; }.search-form { height: 49px; }.search-form button { padding: 0 13px; }.role-grid { grid-template-columns: 1fr; }.section-heading { display: block; }.section-heading > span { display: block; margin-top: 7px; }.featured-section { margin-bottom: 50px; } }
</style>
