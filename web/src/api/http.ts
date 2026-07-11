interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 浏览器已保存但不在本前端创建的 JWT 键名。 */
const ACCESS_TOKEN_KEY = 'access_token'

/** 表示需要用户先在其他入口完成登录的请求错误。 */
export class AuthenticationRequiredError extends Error {
  constructor() {
    super('请先登录后再访问个人角色与评测数据')
  }
}

/**
 * 请求后端标准响应并解包业务数据。
 */
export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...options?.headers,
    },
  })
  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok || body.code !== 200) {
    throw new Error(body.message || '请求暂时无法完成')
  }
  return body.data
}

/**
 * 请求仅属于当前用户的资源，并自动附加浏览器已有的 Bearer Token。
 */
export function authenticatedRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)?.trim()
  if (!token) {
    throw new AuthenticationRequiredError()
  }
  return request<T>(path, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...options?.headers,
    },
  })
}

/** 判断当前浏览器是否已保存可用于联调的访问令牌。 */
export function hasAccessToken() {
  return Boolean(localStorage.getItem(ACCESS_TOKEN_KEY)?.trim())
}
