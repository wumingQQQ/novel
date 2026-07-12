package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户个人角色版本响应，用于前端展示可选择开聊的个人版本。
 */
public record PersonalRoleVersionResponse(
        Long versionId,
        Long characterId,
        Integer versionNo,
        Long parentVersionId,
        Long sourceRequestId,
        boolean latest,
        LocalDateTime createTime,
        List<BehaviorAdjustment> behaviorAdjustments
) {
    /**
     * 个人版本中当前生效的行为调整补丁摘要。
     */
    public record BehaviorAdjustment(
            String adjustmentId,
            Long sourceAdjustItemId,
            String applicability,
            String expectedBehavior,
            String forbiddenBehavior,
            Integer displayOrder
    ) {
    }
}
