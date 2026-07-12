package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.enums.RoleAdjustChangeType;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.domain.enums.RoleAdjustStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色调整请求详情，用于前端展示候选项评审状态。
 */
public record RoleAdjustRequestDetailResponse(
        Long id,
        Long characterId,
        Long baseVersionId,
        String requirement,
        String chatText,
        RoleAdjustRequestStatus status,
        String failureReason,
        Long createdVersionId,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<Item> items
) {
    /**
     * 单个候选调整项详情。
     */
    public record Item(
            Long id,
            RoleAdjustChangeType changeType,
            String adjustmentId,
            String targetAdjustmentId,
            String applicability,
            String expectedBehavior,
            String forbiddenBehavior,
            RoleAdjustStatus status,
            String revisionFeedback,
            String revisionError,
            Integer displayOrder,
            List<Long> passageIds,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }
}
