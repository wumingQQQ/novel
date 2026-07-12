package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;

import java.time.LocalDateTime;

/**
 * 角色调整请求的轻量摘要，用于当前用户的调整列表展示。
 */
public record RoleAdjustRequestSummaryResponse(
        Long id,
        RolePublicPreview character,
        Long baseVersionId,
        String requirement,
        RoleAdjustRequestStatus status,
        String failureReason,
        Long createdVersionId,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
