package com.wuming.chat.domain.enums;

/**
 * 聊天会话状态；数据库中以 {@link #code()} 的字符串存储。
 */
public enum ChatSessionStatus {
    ACTIVE("ACTIVE");

    private final String code;

    ChatSessionStatus(String code) {
        this.code = code;
    }

    /** 返回持久化使用的状态字符串。 */
    public String code() {
        return code;
    }

    /** 判断给定状态字符串是否为当前枚举。 */
    public boolean matches(String status) {
        return code.equals(status);
    }
}
