import { computed, ref } from 'vue'
import { getCurrentUser } from '@/api/auth'
import { clearAccessToken, hasAccessToken } from '@/api/http'
import type { CurrentUser } from '@/types/auth'

const currentUser = ref<CurrentUser>()
const authDialogVisible = ref(false)
const initialized = ref(false)

/** 共享当前登录用户状态，避免各页面各自读取令牌。 */
export function useAuth() {
  const isAuthenticated = computed(() => Boolean(currentUser.value))

  async function initializeAuth() {
    if (initialized.value) return
    await refreshCurrentUser()
    initialized.value = true
  }

  async function refreshCurrentUser() {
    if (!hasAccessToken()) {
      currentUser.value = undefined
      return
    }
    try { currentUser.value = await getCurrentUser() } catch { clearAccessToken(); currentUser.value = undefined }
  }

  function openAuthDialog() { authDialogVisible.value = true }
  function logout() { clearAccessToken(); currentUser.value = undefined }

  return { currentUser, isAuthenticated, authDialogVisible, initializeAuth, refreshCurrentUser, openAuthDialog, logout }
}
