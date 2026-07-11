import { request } from '@/api/http'
import type { RolePublicPageResponse, RolePublicPreview } from '@/types/role'

/**
 * 查询只包含脱敏字段的公共角色大厅数据。
 */
export function listPublicRoles(keyword = '', page = 1, size = 12) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword.trim()) {
    params.set('keyword', keyword.trim())
  }
  return request<RolePublicPageResponse>(`/roles?${params}`)
}

/**
 * 查询公共角色的受限预览，完整角色资产不会由该接口返回。
 */
export function getPublicRolePreview(id: string | number) {
  return request<RolePublicPreview>(`/roles/${id}`)
}
