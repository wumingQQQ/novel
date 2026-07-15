package com.wuming.chat.service.reply.advisor.context;

import org.springframework.ai.chat.client.ChatClientRequest;

/**
 * 安全读取Advisor上下文中的强类型数据。
 */
public final class ChatAdvisorContextAccessor {

    public static <T> T require(
            ChatClientRequest request,
            String key,
            Class<T> expectedType
    ) {
        Object value = request.context().get(key);
        if (value == null) {
            throw new IllegalStateException("缺少Advisor上下文参数: " + key);
        }
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException("Advisor上下文参数类型错误，key: %s, expectedType: %s, actualType: %s"
                    .formatted(key, expectedType.getName(), value.getClass().getName())
            );
        }
        return expectedType.cast(value);
    }

    private ChatAdvisorContextAccessor() {
    }
}
