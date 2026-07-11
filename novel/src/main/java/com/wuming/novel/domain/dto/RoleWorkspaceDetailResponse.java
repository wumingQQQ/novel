package com.wuming.novel.domain.dto;

import java.util.List;

/**
 * 我的评测页面使用的单个角色工作区详情。
 */
public record RoleWorkspaceDetailResponse(
        RoleWorkspaceSummary workspace,
        List<RoleWorkspaceEvaluationSummary> evaluations
) {
}
