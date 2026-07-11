package com.wuming.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天分层记忆的窗口与压缩阈值配置。
 */
@Data
@ConfigurationProperties(prefix = "chat.memory")
public class ChatMemoryProperties {
    private int recentMessageLimit = 16;
    private int summaryTriggerMessageCount = 24;

    /** 返回至少保留一条的最近原文窗口大小。 */
    public int recentLimit() {
        return Math.max(1, recentMessageLimit);
    }

    /** 返回始终大于最近窗口的摘要触发阈值。 */
    public int summaryTrigger() {
        return Math.max(recentLimit() + 1, summaryTriggerMessageCount);
    }
}
