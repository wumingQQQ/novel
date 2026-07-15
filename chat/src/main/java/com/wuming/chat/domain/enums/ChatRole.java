package com.wuming.chat.domain.enums;

/**
 * 聊天消息角色；数据库与提示词历史中以 {@link #code()} 的小写字符串存储。
 */
public enum ChatRole {
    USER("user"),
    ASSISTANT("assistant");

    private final String code;

    ChatRole(String code) {
        this.code = code;
    }

    /** 返回持久化与提示词使用的角色字符串。 */
    public String code() {
        return code;
    }

    /** 判断给定角色字符串是否为当前枚举。 */
    public boolean matches(String role) {
        return code.equals(role);
    }
}
