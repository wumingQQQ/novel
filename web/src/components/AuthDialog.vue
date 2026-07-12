<script setup lang="ts">
import { ref } from 'vue'
import { login, register } from '@/api/auth'
import { saveAccessToken } from '@/api/http'
import { useAuth } from '@/composables/useAuth'

const { authDialogVisible, refreshCurrentUser } = useAuth()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const error = ref('')
const loginForm = ref({ account: '', password: '' })
const registerForm = ref({ username: '', nickname: '', email: '', password: '' })

/** 登录成功后立即刷新全局用户状态，并关闭认证弹窗。 */
async function submitLogin() {
  if (!loginForm.value.account.trim() || !loginForm.value.password || loading.value) return
  loading.value = true; error.value = ''
  try {
    const response = await login({ account: loginForm.value.account.trim(), password: loginForm.value.password })
    saveAccessToken(response.accessToken)
    await refreshCurrentUser()
    authDialogVisible.value = false
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '登录未完成' } finally { loading.value = false }
}

/** 注册完成后预填账号并切回登录，避免假设后端会自动签发令牌。 */
async function submitRegister() {
  if (!registerForm.value.username.trim() || !registerForm.value.email.trim() || !registerForm.value.password || loading.value) return
  loading.value = true; error.value = ''
  try {
    await register({ ...registerForm.value, username: registerForm.value.username.trim(), email: registerForm.value.email.trim(), nickname: registerForm.value.nickname.trim() || undefined })
    loginForm.value.account = registerForm.value.username.trim()
    loginForm.value.password = registerForm.value.password
    mode.value = 'login'
    error.value = '注册成功，请登录。'
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '注册未完成' } finally { loading.value = false }
}
</script>

<template>
  <el-dialog v-model="authDialogVisible" width="min(430px, calc(100% - 32px))" align-center><template #header><span class="dialog-title">{{ mode === 'login' ? '登录镜中角色' : '创建账号' }}</span></template><form v-if="mode === 'login'" class="auth-form" @submit.prevent="submitLogin"><label>账号或邮箱</label><el-input v-model="loginForm.account" autocomplete="username" placeholder="输入用户名或邮箱" /><label>密码</label><el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" placeholder="输入密码" /><p v-if="error" class="form-message">{{ error }}</p><el-button native-type="submit" type="primary" :loading="loading">登录</el-button><button type="button" class="switch" @click="mode = 'register'; error = ''">还没有账号？注册</button></form><form v-else class="auth-form" @submit.prevent="submitRegister"><label>用户名</label><el-input v-model="registerForm.username" autocomplete="username" placeholder="用于登录" /><label>昵称（可选）</label><el-input v-model="registerForm.nickname" placeholder="页面显示名称" /><label>邮箱</label><el-input v-model="registerForm.email" autocomplete="email" placeholder="用于接收通知" /><label>密码</label><el-input v-model="registerForm.password" type="password" show-password autocomplete="new-password" placeholder="设置密码" /><p v-if="error" class="form-message">{{ error }}</p><el-button native-type="submit" type="primary" :loading="loading">注册并继续</el-button><button type="button" class="switch" @click="mode = 'login'; error = ''">已有账号？登录</button></form></el-dialog>
</template>

<style scoped>
.dialog-title { font-family: "Noto Serif SC", serif; font-size: 21px; }.auth-form { display: grid; gap: 8px; }.auth-form label { margin-top: 7px; color: #596569; font-size: 13px; }.auth-form :deep(.el-button--primary) { width: 100%; margin-top: 12px; background: #344b4c; border-color: #344b4c; }.form-message { margin: 5px 0 0; color: #a55d43; font-size: 12px; }.switch { justify-self: center; border: 0; color: #8e604d; background: none; cursor: pointer; font-size: 12px; }
</style>
