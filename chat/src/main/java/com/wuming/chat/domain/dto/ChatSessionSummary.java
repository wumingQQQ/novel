package com.wuming.chat.domain.dto;

import java.time.LocalDateTime;

/**
 * 左侧会话列表使用的当前用户会话摘要。
 */
public record ChatSessionSummary(
        Long sessionId,
        Long characterId,
        Long userRoleVersionId,
        String lastMessageRole,
        String lastMessagePreview,
        LocalDateTime updateTime
) {
}
