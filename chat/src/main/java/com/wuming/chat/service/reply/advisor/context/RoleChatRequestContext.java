package com.wuming.chat.service.reply.advisor.context;

import java.util.Objects;

/**
 * 单次角色聊天调用在Advisor链中共享的业务上下文。
 *
 * @param userId 当前用户主键
 * @param sessionId 当前聊天会话主键
 * @param characterId 公共角色主键
 * @param userRoleVersionId 可选的个人角色版本主键
 * @param currentUserMessageId 本轮已经保存的用户消息主键
 */
public record RoleChatRequestContext(
        Long userId,
        Long sessionId,
        Long characterId,
        Long userRoleVersionId,
        Long currentUserMessageId
) {
    public RoleChatRequestContext {
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(characterId, "characterId不能为空");
        Objects.requireNonNull(currentUserMessageId, "currentUserMessageId不能为空");
    }
}
