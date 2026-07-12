<script setup lang="ts">
import { ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const isHall = computed(() => route.name === 'role-hall' || route.name === 'role-preview')
const isMyRoles = computed(() => route.name === 'my-roles')
const isMyEvaluations = computed(() => route.name === 'my-evaluations' || route.name === 'evaluation-workspace')
const isCreation = computed(() => route.name === 'creation' || route.name === 'creation-job')
const accountMenuVisible = ref(false)
const { currentUser, initializeAuth, openAuthDialog, logout } = useAuth()
const accountLabel = computed(() => currentUser.value?.nickname || currentUser.value?.username || '登录')

onMounted(() => void initializeAuth())
</script>

<template>
  <header class="app-header">
    <RouterLink class="brand" to="/">镜中角色</RouterLink>
    <nav class="main-nav" aria-label="主导航">
      <RouterLink :class="{ active: isCreation }" to="/creation">创作大厅</RouterLink>
      <RouterLink :class="{ active: isHall }" to="/">角色大厅</RouterLink>
      <RouterLink :class="{ active: isMyRoles }" to="/my-roles">我的角色</RouterLink>
      <RouterLink :class="{ active: isMyEvaluations }" to="/my-evaluations">我的调整</RouterLink>
    </nav>
    <button class="account" type="button" aria-label="打开账户菜单" @click="currentUser ? accountMenuVisible = !accountMenuVisible : openAuthDialog()">
      <span class="account-mark">镜</span>
      <span>{{ accountLabel }}</span>
      <el-icon><ArrowDown /></el-icon>
    </button>
    <div v-if="accountMenuVisible && currentUser" class="account-menu"><b>{{ currentUser.username }}</b><span>{{ currentUser.status }}</span><button type="button" @click="logout(); accountMenuVisible = false"><el-icon><SwitchButton /></el-icon>退出登录</button></div>
  </header>
</template>

<style scoped>
.app-header { position: sticky; z-index: 10; top: 0; height: 70px; display: flex; align-items: center; padding: 0 max(28px, calc((100vw - 1240px) / 2)); border-bottom: 1px solid #ebe8e1; background: rgb(255 254 250 / 94%); backdrop-filter: blur(14px); }
.brand { color: #253233; font-family: "Noto Serif SC", serif; font-size: 20px; font-weight: 700; letter-spacing: .08em; white-space: nowrap; }
.main-nav { display: flex; gap: 31px; align-self: stretch; margin-left: 64px; }.main-nav a { position: relative; display: grid; place-items: center; color: #717c80; font-size: 14px; transition: color .2s ease; }.main-nav a:not(.disabled):hover, .main-nav a.active { color: #263637; font-weight: 600; }.main-nav a.active::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: #ae6044; content: ""; }.disabled { cursor: not-allowed; opacity: .45; }
.account { display: inline-flex; gap: 8px; align-items: center; margin-left: auto; padding: 6px 0 6px 9px; color: #596468; border: 0; background: none; cursor: pointer; }.account-mark { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #af6a53, #334649); font-family: "Noto Serif SC", serif; font-size: 13px; }.account :deep(.el-icon) { font-size: 13px; }.account-menu { position: absolute; top: 59px; right: max(28px, calc((100vw - 1240px) / 2)); display: grid; min-width: 160px; gap: 5px; padding: 12px; border: 1px solid #e7e1da; border-radius: 7px; color: #657174; background: #fffdfa; box-shadow: 0 14px 30px rgb(54 44 36 / 10%); font-size: 11px; }.account-menu span { color: #9a8880; font-size: 10px; }.account-menu button { display: inline-flex; gap: 5px; align-items: center; margin-top: 8px; padding: 6px 0; border: 0; color: #93614c; background: none; cursor: pointer; font-size: 11px; text-align: left; }
@media (max-width: 680px) { .app-header { height: 60px; padding: 0 18px; }.brand { font-size: 17px; }.main-nav { gap: 17px; margin-left: 25px; }.main-nav a { font-size: 12px; }.account > span:not(.account-mark), .account :deep(.el-icon) { display: none; } }
</style>
