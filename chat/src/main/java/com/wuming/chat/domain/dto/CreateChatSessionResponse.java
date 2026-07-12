package com.wuming.chat.domain.dto;

/**
 * 创建聊天会话响应，供前端直接确认本次会话绑定的公共角色和个人版本。
 */
public record CreateChatSessionResponse(
        Long sessionId,
        Long characterId,
        Long userRoleVersionId,
        boolean personalVersionBound
) {
    public static CreateChatSessionResponse of(Long sessionId, Long characterId, Long userRoleVersionId) {
        return new CreateChatSessionResponse(
                sessionId,
                characterId,
                userRoleVersionId,
                userRoleVersionId != null
        );
    }
}
