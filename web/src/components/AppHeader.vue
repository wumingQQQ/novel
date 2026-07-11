<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isHall = computed(() => route.name === 'role-hall' || route.name === 'role-preview')
const isMyRoles = computed(() => route.name === 'my-roles')
const isMyEvaluations = computed(() => route.name === 'my-evaluations' || route.name === 'evaluation-workspace')
</script>

<template>
  <header class="app-header">
    <RouterLink class="brand" to="/">镜中角色</RouterLink>
    <nav class="main-nav" aria-label="主导航">
      <RouterLink :class="{ active: isHall }" to="/">角色大厅</RouterLink>
      <RouterLink :class="{ active: isMyRoles }" to="/my-roles">我的角色</RouterLink>
      <RouterLink :class="{ active: isMyEvaluations }" to="/my-evaluations">我的评测</RouterLink>
    </nav>
    <button class="account" type="button" aria-label="打开账户菜单">
      <span class="account-mark">镜</span>
      <span>访客</span>
      <el-icon><ArrowDown /></el-icon>
    </button>
  </header>
</template>

<style scoped>
.app-header { position: sticky; z-index: 10; top: 0; height: 70px; display: flex; align-items: center; padding: 0 max(28px, calc((100vw - 1240px) / 2)); border-bottom: 1px solid #ebe8e1; background: rgb(255 254 250 / 94%); backdrop-filter: blur(14px); }
.brand { color: #253233; font-family: "Noto Serif SC", serif; font-size: 20px; font-weight: 700; letter-spacing: .08em; white-space: nowrap; }
.main-nav { display: flex; gap: 31px; align-self: stretch; margin-left: 64px; }.main-nav a { position: relative; display: grid; place-items: center; color: #717c80; font-size: 14px; transition: color .2s ease; }.main-nav a:not(.disabled):hover, .main-nav a.active { color: #263637; font-weight: 600; }.main-nav a.active::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: #ae6044; content: ""; }.disabled { cursor: not-allowed; opacity: .45; }
.account { display: inline-flex; gap: 8px; align-items: center; margin-left: auto; padding: 6px 0 6px 9px; color: #596468; border: 0; background: none; cursor: pointer; }.account-mark { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #af6a53, #334649); font-family: "Noto Serif SC", serif; font-size: 13px; }.account :deep(.el-icon) { font-size: 13px; }
@media (max-width: 680px) { .app-header { height: 60px; padding: 0 18px; }.brand { font-size: 17px; }.main-nav { gap: 17px; margin-left: 25px; }.main-nav a { font-size: 12px; }.account > span:not(.account-mark), .account :deep(.el-icon) { display: none; } }
</style>
