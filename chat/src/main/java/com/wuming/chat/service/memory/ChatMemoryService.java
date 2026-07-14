package com.wuming.chat.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.chat.config.ChatMemoryProperties;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.domain.entity.ChatSessionMemory;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import com.wuming.chat.domain.model.ChatMemoryContext;
import com.wuming.chat.infrastructure.mapper.ChatMessageMapper;
import com.wuming.chat.infrastructure.mapper.ChatSessionMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 维护会话长期摘要，并为角色回复提供长期记忆和最近原文组成的快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {
    private static final int MAX_UPDATE_ATTEMPTS = 2;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMemoryMapper memoryMapper;
    private final ChatMemorySummarizer summarizer;
    private final ChatMemoryProperties properties;

    /**
     * 读取本轮可用记忆；达到阈值时先压缩较早消息，再保留最近原文窗口。
     *
     * @param sessionId 聊天会话主键
     * @return 可直接注入角色提示词的记忆快照
     */
    public ChatMemoryContext prepareContext(Long sessionId) {
        for (int attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            // 会话中存在的长期记忆与未被长期记忆覆盖的消息
            ChatSessionMemory existingMemory = memoryMapper.selectById(sessionId);
            List<ChatMessage> uncoveredMessages = loadUncoveredMessages(sessionId, existingMemory);
            if (uncoveredMessages.size() < properties.summaryTrigger()) {
                return toContext(existingMemory, uncoveredMessages);
            }

            int splitIndex = uncoveredMessages.size() - properties.recentLimit();
            List<ChatMessage> messagesToCompact = uncoveredMessages.subList(0, splitIndex);
            try {
                // 对最近消息窗口外的消息+已有长期记忆构造新长期记忆
                String summary = summarizer.summarize(
                        existingMemory == null ? "" : existingMemory.getSummaryContent(),
                        toHistoryMessages(messagesToCompact));
                ChatSessionMemory nextMemory = nextMemory(sessionId, existingMemory,
                        messagesToCompact.getLast().getId(), summary);
                if (saveMemory(nextMemory, existingMemory)) {
                    log.info("会话长期记忆压缩完成，sessionId: {}, compactedMessageCount: {}, recentMessageCount: {}, coveredMessageId: {}, version: {}",
                            sessionId, messagesToCompact.size(), uncoveredMessages.size() - splitIndex,
                            nextMemory.getCoveredMessageId(), nextMemory.getVersion());
                    return new ChatMemoryContext(summary,
                            toHistoryMessages(uncoveredMessages.subList(splitIndex, uncoveredMessages.size())));
                }
            } catch (RuntimeException e) {
                log.warn("会话长期记忆摘要失败，使用最近原文降级，sessionId: {}", sessionId, e);
                return fallbackContext(existingMemory, uncoveredMessages);
            }
            log.info("会话长期记忆发生并发更新，将重新读取，sessionId: {}, attempt: {}", sessionId, attempt + 1);
        }

        ChatSessionMemory latestMemory = memoryMapper.selectById(sessionId);
        List<ChatMessage> uncoveredMessages = loadUncoveredMessages(sessionId, latestMemory);
        log.warn("会话长期记忆更新未完成，使用最近原文降级，sessionId: {}", sessionId);
        return fallbackContext(latestMemory, uncoveredMessages);
    }

    /** 查询尚未被当前长期摘要覆盖的原始消息。 */
    private List<ChatMessage> loadUncoveredMessages(Long sessionId, ChatSessionMemory memory) {
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId);
        if (memory != null && memory.getCoveredMessageId() != null) {
            query.gt(ChatMessage::getId, memory.getCoveredMessageId());
        }
        return chatMessageMapper.selectList(query);
    }

    /** 将数据库消息映射为只含角色和文本的提示词历史。 */
    private List<ChatHistoryMessage> toHistoryMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> new ChatHistoryMessage(message.getRole(), message.getContent()))
                .toList();
    }

    /** 构造待插入或待更新的长期记忆记录。 */
    private ChatSessionMemory nextMemory(Long sessionId, ChatSessionMemory existingMemory,
                                         Long coveredMessageId, String summaryContent) {
        ChatSessionMemory memory = new ChatSessionMemory();
        memory.setSessionId(sessionId);
        memory.setCoveredMessageId(coveredMessageId);
        memory.setSummaryContent(summaryContent);
        memory.setVersion(existingMemory == null ? 1 : existingMemory.getVersion() + 1);
        return memory;
    }

    /** 插入首份摘要或按版本更新已有摘要。 */
    private boolean saveMemory(ChatSessionMemory nextMemory, ChatSessionMemory existingMemory) {
        if (existingMemory == null) {
            return memoryMapper.insert(nextMemory) == 1;
        }
        return memoryMapper.updateIfVersionMatches(nextMemory, existingMemory.getVersion()) == 1;
    }

    /** 返回最多指定数量的尾部消息，用于摘要写入失败时的保守降级。 */
    private List<ChatMessage> tail(List<ChatMessage> messages, int maxSize) {
        int fromIndex = Math.max(0, messages.size() - maxSize);
        return messages.subList(fromIndex, messages.size());
    }

    /** 摘要不可用或并发重试耗尽时，保留已有摘要并仅注入最近原文。 */
    private ChatMemoryContext fallbackContext(ChatSessionMemory memory, List<ChatMessage> messages) {
        return new ChatMemoryContext(memory == null ? "" : memory.getSummaryContent(),
                toHistoryMessages(tail(messages, properties.recentLimit())));
    }

    /** 组合已有摘要和未压缩消息，且不改变它们的原始顺序。 */
    private ChatMemoryContext toContext(ChatSessionMemory memory, List<ChatMessage> messages) {
        return new ChatMemoryContext(memory == null ? "" : memory.getSummaryContent(), toHistoryMessages(messages));
    }
}
