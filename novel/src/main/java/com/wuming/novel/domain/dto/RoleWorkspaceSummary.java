package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 我的角色页面使用的角色工作区摘要，只聚合当前用户数据。
 */
public record RoleWorkspaceSummary(
        RolePublicPreview character,
        long evaluationCount,
        Long latestEvaluationId,
        Long userRoleTrackId,
        Long latestUserRoleVersionId,
        Integer latestVersionNo,
        LocalDateTime latestEvaluationTime
) {
}
