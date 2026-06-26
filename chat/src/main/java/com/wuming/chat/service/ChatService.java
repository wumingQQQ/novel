package com.wuming.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.chat.config.llm.LlmClientFactory;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import com.wuming.chat.domain.model.RoleProfileContext;
import com.wuming.chat.mapper.ChatMessageMapper;
import com.wuming.chat.mapper.ChatSessionMapper;
import com.wuming.chat.service.cache.ChatMessageCacheService;
import com.wuming.chat.service.cache.ProfilePromptCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String SESSION_ACTIVE = "ACTIVE";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;
    private final ProfilePromptCacheService profilePromptCacheService;
    private final ProfileContextService profileContextService;
    private final RoleChatPromptBuilder promptBuilder;
    private final LlmClientFactory llmClientFactory;

    @Value("${chat.history-limit:20}")
    private int historyLimit;

    /**
     * 创建聊天会话前先确认对应任务已经生成完整画像。
     */
    @Transactional
    public Long createSession(Long jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }
        profileContextService.getProfileContext(jobId);

        ChatSession session = new ChatSession();
        session.setJobId(jobId);
        session.setStatus(SESSION_ACTIVE);
        chatSessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 保存用户消息，携带画像和最近历史请求 LLM，再保存角色回复。
     */
    public SendChatMessageResponse sendMessage(Long sessionId, String content) {
        ChatSession session = requireSession(sessionId);
        String userContent = requireContent(content);

        saveMessage(sessionId, ROLE_USER, userContent);

        String assistantContent = requestAssistantReply(
                systemPrompt(session.getJobId()),
                recentMessages(sessionId)
        );

        ChatMessage assistantMessage = saveMessage(sessionId, ROLE_ASSISTANT, assistantContent);
        return new SendChatMessageResponse(assistantMessage.getId(), assistantContent);
    }

    /**
     * 保存单条消息；这里不包裹长事务，避免 LLM 调用占用数据库连接。
     */
    private ChatMessage saveMessage(Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        chatMessageMapper.insert(message);
        chatMessageCacheService.append(message);
        return message;
    }

    /**
     * 按插入顺序返回会话内的全部消息。
     */
    public List<ChatMessage> listMessages(Long sessionId) {
        requireSession(sessionId);
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId)
        );
    }

    /**
     * 校验会话存在且处于可用状态。
     */
    private ChatSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !SESSION_ACTIVE.equals(session.getStatus())) {
            throw new IllegalArgumentException("聊天会话不存在或不可用: " + sessionId);
        }
        return session;
    }

    /**
     * 校验并规整用户输入，避免空消息进入上下文。
     */
    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return content.trim();
    }

    /**
     * 优先从 Redis 读取最近消息，缓存未命中时回源数据库并重建缓存。
     */
    private List<ChatHistoryMessage> recentMessages(Long sessionId) {
        List<ChatHistoryMessage> cachedMessages = chatMessageCacheService.recentMessages(sessionId);
        if (!cachedMessages.isEmpty()) {
            return cachedMessages;
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("limit " + Math.max(1, historyLimit))
        );
        Collections.reverse(messages);
        chatMessageCacheService.refresh(sessionId, messages);
        return messages.stream()
                .map(message -> new ChatHistoryMessage(message.getRole(), message.getContent()))
                .toList();
    }

    /**
     * 优先读取角色系统提示词缓存，缓存未命中时回源画像表并写入缓存。
     */
    private String systemPrompt(Long jobId) {
        String cachedPrompt = profilePromptCacheService.get(jobId);
        if (cachedPrompt != null && !cachedPrompt.isBlank()) {
            return cachedPrompt;
        }

        RoleProfileContext profileContext = profileContextService.getProfileContext(jobId);
        String systemPrompt = promptBuilder.buildSystemPrompt(profileContext);
        profilePromptCacheService.put(jobId, systemPrompt);
        return systemPrompt;
    }

    /**
     * 使用角色系统提示词和最近对话文本请求 LLM 生成角色回复。
     */
    private String requestAssistantReply(String systemPrompt, List<ChatHistoryMessage> messages) {
        return llmClientFactory
                .taskClient(LlmClientFactory.TASK_ROLE_CHAT)
                .prompt()
                .system(systemPrompt)
                .user(formatConversation(messages))
                .call()
                .content();
    }

    /**
     * 将历史消息折叠成普通文本，兼容当前 ChatClient API。
     */
    private String formatConversation(List<ChatHistoryMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("【最近对话】\n");
        for (ChatHistoryMessage message : messages) {
            if (ROLE_USER.equals(message.role())) {
                builder.append("用户：").append(message.content()).append('\n');
            } else if (ROLE_ASSISTANT.equals(message.role())) {
                builder.append("你：").append(message.content()).append('\n');
            }
        }
        builder.append("\n请继续回复最后一条用户消息，只输出角色本人的回复。");
        return builder.toString();
    }
}
