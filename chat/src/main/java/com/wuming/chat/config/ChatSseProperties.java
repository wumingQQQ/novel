package com.wuming.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 聊天 SSE 连接生命周期与上游调用并发保护配置。
 */
@Data
@ConfigurationProperties(prefix = "chat.sse")
public class ChatSseProperties {
    private Duration timeout = Duration.ofMinutes(10);
    private int maxConcurrentStreams = 64;

    /** 返回至少一毫秒的 SSE 超时时间。 */
    public long timeoutMillis() {
        return Math.max(1L, timeout.toMillis());
    }

    /** 返回至少一个许可的流式聊天并发上限。 */
    public int concurrentLimit() {
        return Math.max(1, maxConcurrentStreams);
    }
}
