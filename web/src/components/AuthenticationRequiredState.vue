<script setup lang="ts">
import { useAuth } from '@/composables/useAuth'

const { openAuthDialog } = useAuth()
</script>

<template>
  <section class="auth-state"><p class="eyebrow">SIGN IN REQUIRED</p><h1>这里收纳的是你的角色轨迹。</h1><p>登录后可以继续查看个人角色、调整请求和创作任务。</p><button type="button" @click="openAuthDialog">登录或注册</button></section>
</template>

<style scoped>
.auth-state { display: grid; min-height: 390px; place-content: center; justify-items: center; padding: 35px; border: 1px dashed #d9d2c9; border-radius: 8px; text-align: center; }.eyebrow { margin: 0; color: #a55d43; font: 500 11px "DM Mono", monospace; letter-spacing: .14em; }.auth-state h1 { margin: 8px 0; font-family: "Noto Serif SC", serif; font-size: 28px; }.auth-state p:last-of-type { max-width: 520px; color: #758084; font-size: 13px; line-height: 1.9; }.auth-state button { margin-top: 12px; padding: 8px 13px; border: 0; border-radius: 5px; color: #fff; background: #344b4c; cursor: pointer; font-size: 12px; }
</style>
