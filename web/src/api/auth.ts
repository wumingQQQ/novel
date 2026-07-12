import { authenticatedRequest, request } from '@/api/http'
import type { CurrentUser, LoginInput, LoginResponse, RegisterInput } from '@/types/auth'

/** 注册新用户；注册完成后仍需使用账号密码登录。 */
export function register(input: RegisterInput) {
  return request<number>('/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

/** 登录并取得 Bearer 访问令牌。 */
export function login(input: LoginInput) {
  return request<LoginResponse>('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}

/** 查询当前令牌所代表的用户。 */
export function getCurrentUser() { return authenticatedRequest<CurrentUser>('/auth/me') }
