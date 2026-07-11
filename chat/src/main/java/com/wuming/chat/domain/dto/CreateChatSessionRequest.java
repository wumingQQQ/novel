package com.wuming.chat.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建聊天会话的请求，可选绑定当前用户的个人角色版本。
 */
@Data
public class CreateChatSessionRequest {
    @NotNull
    private Long characterId;

    /** 为空时创建公共角色基线会话。 */
    private Long userRoleVersionId;
}
