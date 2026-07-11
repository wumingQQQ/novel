package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 公共角色大厅使用的脱敏摘要，不包含可还原角色资产的字段。
 */
public record RolePublicSummary(
        Long id,
        String characterName,
        String novelName,
        String introduction,
        long ruleCount,
        long exampleCount,
        LocalDateTime completedTime
) {
}
