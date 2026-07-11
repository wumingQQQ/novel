package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 公共角色预览页使用的脱敏详情，不包含画像、规则和原作样本。
 */
public record RolePublicPreview(
        Long id,
        String characterName,
        String novelName,
        String introduction,
        long ruleCount,
        long exampleCount,
        LocalDateTime completedTime
) {
}
