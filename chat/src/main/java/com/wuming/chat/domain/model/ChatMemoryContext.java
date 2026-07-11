package com.wuming.chat.domain.model;

import java.util.List;

/**
 * 本轮角色回复可见的长期摘要和最近原文消息快照。
 *
 * @param summaryContent 已压缩的长期事实，可为空
 * @param recentMessages 尚未压缩的最近原文消息
 */
public record ChatMemoryContext(String summaryContent, List<ChatHistoryMessage> recentMessages) {

    public ChatMemoryContext {
        summaryContent = summaryContent == null ? "" : summaryContent;
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}
