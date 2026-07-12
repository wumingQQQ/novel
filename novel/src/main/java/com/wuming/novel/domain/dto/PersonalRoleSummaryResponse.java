package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 当前用户在一个公共角色下的最新个人版本摘要，用于“我的角色”列表展示。
 */
public record PersonalRoleSummaryResponse(
        RolePublicPreview character,
        Long versionId,
        Integer versionNo,
        Long sourceRequestId,
        LocalDateTime createTime
) {
}
