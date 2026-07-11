package com.wuming.novel.domain.dto;

import java.util.List;

/**
 * 公共角色大厅的分页查询结果。
 */
public record RolePublicPageResponse(
        List<RolePublicSummary> items,
        long total,
        long page,
        long size
) {
}
