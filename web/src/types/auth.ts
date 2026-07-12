export interface CurrentUser {
  id: number
  username: string
  nickname: string | null
  status: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface LoginInput { account: string; password: string }
export interface RegisterInput { username: string; nickname?: string; email: string; password: string }
