package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 当前用户在一个公共角色下创建的单次评测摘要。
 */
public record RoleWorkspaceEvaluationSummary(
        Long evaluationId,
        Long userRoleVersionId,
        long caseCount,
        long approvedCaseCount,
        long succeededRunCount,
        long draftImprovementBatchCount,
        Double latestScore,
        LocalDateTime createTime
) {
}
