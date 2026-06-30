package com.wuming.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.chat.config.llm.LlmClientFactory;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import com.wuming.chat.mapper.ChatMessageMapper;
import com.wuming.chat.mapper.ChatSessionMapper;
import com.wuming.chat.observability.TraceContext;
import com.wuming.chat.rag.prompt.RagPromptBuilder;
import com.wuming.chat.rag.retrieve.RagRetrieveService;
import com.wuming.chat.rpc.profile.ProfileContextService;
import com.wuming.chat.rpc.user.UserContextService;
import com.wuming.chat.service.cache.ChatMessageCacheService;
import com.wuming.chat.service.cache.ProfilePromptCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
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
    private final UserContextService userContextService;
    private final RoleChatPromptBuilder promptBuilder;
    private final LlmClientFactory llmClientFactory;
    private final RagPromptBuilder ragPromptBuilder;
    private final RagRetrieveService retrieveService;

    @Value("${chat.history-limit:20}")
    private int historyLimit;

    /**
     * 创建聊天会话前先确认用户可用且对应任务已经生成完整画像。
     */
    @Transactional
    public Long createSession(Long userId, Long jobId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId);
             TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            long start = System.currentTimeMillis();
            log.info("开始创建聊天会话");
            try {
                Long sessionId = doCreateSession(userId, jobId);
                log.info("聊天会话创建完成，sessionId: {}, costMs: {}",
                        sessionId, System.currentTimeMillis() - start);
                return sessionId;
            } catch (RuntimeException e) {
                log.warn("聊天会话创建失败，costMs: {}",
                        System.currentTimeMillis() - start, e);
                throw e;
            }
        }
    }

    /**
     * 保存用户消息，携带画像、RAG上下文和最近历史请求 LLM，再保存角色回复。
     *
     * @param sessionId 聊天会话id
     * @param content 用户输入
     * @return 角色回复消息id和内容
     */
    public SendChatMessageResponse sendMessage(Long sessionId, String content) {
        ChatSession session = requireSession(sessionId);
        try (TraceContext.MdcScope ignoredSession = TraceContext.putSessionId(sessionId);
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(session.getUserId());
             TraceContext.MdcScope ignoredJob = TraceContext.putJobId(session.getJobId())) {
            long start = System.currentTimeMillis();
            log.info("开始处理聊天消息");
            try {
                String userContent = requireContent(content);

                saveMessage(sessionId, ROLE_USER, userContent);

                String ragPrompt = ragPromptBuilder.buildContextPrompt(
                        retrieveService.retrieve(session.getJobId(), userContent)
                );
                String assistantContent = requestAssistantReply(
                        systemPrompt(session.getJobId()),
                        recentMessages(sessionId),
                        ragPrompt
                );

                ChatMessage assistantMessage = saveMessage(
                        sessionId,
                        ROLE_ASSISTANT,
                        assistantContent
                );
                log.info("聊天消息处理完成，assistantMessageId: {}, costMs: {}",
                        assistantMessage.getId(), System.currentTimeMillis() - start);
                return new SendChatMessageResponse(assistantMessage.getId(), assistantContent);
            } catch (RuntimeException e) {
                log.warn("聊天消息处理失败，costMs: {}",
                        System.currentTimeMillis() - start, e);
                throw e;
            }
        }
    }

    /**
     * 执行聊天会话创建的核心逻辑，外层负责日志上下文和耗时统计。
     */
    private Long doCreateSession(Long userId, Long jobId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }

        UserResultDto result = userContextService.getRequiredUser(userId);
        if (!result.isSuccess()) {
            throw new IllegalArgumentException(result.getMessage());
        }

        profileContextService.getProfileContext(jobId);

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setJobId(jobId);
        session.setStatus(SESSION_ACTIVE);
        chatSessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 保存单条消息；这里不包裹长事务，避免 LLM 调用占用数据库连接。
     *
     * @param sessionId 聊天会话id
     * @param role 分为USER与ASSISTANT
     * @param content 经过规整(requireContent)后的用户输入
     * @return 已保存的聊天消息
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
     *
     * @param content 用户输入
     * @return 去除首尾空白后的用户输入
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
            log.debug("聊天历史缓存命中，messageCount: {}", cachedMessages.size());
            return cachedMessages;
        }

        log.debug("聊天历史缓存未命中，回源数据库");
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("limit " + Math.max(1, historyLimit))
        );
        Collections.reverse(messages);
        chatMessageCacheService.refresh(sessionId, messages);
        log.debug("聊天历史缓存已重建，messageCount: {}", messages.size());
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
            log.debug("角色画像提示词缓存命中");
            return cachedPrompt;
        }

        log.debug("角色画像提示词缓存未命中，回源画像服务");
        RoleContextDto profileContext = profileContextService.getProfileContext(jobId);
        String systemPrompt = promptBuilder.buildSystemPrompt(profileContext);
        profilePromptCacheService.put(jobId, systemPrompt);
        log.debug("角色画像提示词缓存已写入");
        return systemPrompt;
    }

    /**
     * 使用角色系统提示词、最近对话和RAG上下文请求 LLM 生成角色回复。
     *
     * @param systemPrompt 角色系统提示词
     * @param messages 该会话对话历史
     * @param ragPrompt RAG召回上下文提示词
     * @return LLM生成的角色回复
     */
    private String requestAssistantReply(String systemPrompt,
                                         List<ChatHistoryMessage> messages,
                                         String ragPrompt) {
        long start = System.currentTimeMillis();
        try {
            String content = llmClientFactory
                    .taskClient(LlmClientFactory.TASK_ROLE_CHAT)
                    .prompt()
                    .system(systemPrompt)
                    .user(formatConversation(messages, ragPrompt))
                    .call()
                    .content();
            log.info("角色聊天LLM调用完成，historyCount: {}, ragEnabled: {}, costMs: {}",
                    messages.size(), ragPrompt != null && !ragPrompt.isBlank(),
                    System.currentTimeMillis() - start);
            return content;
        } catch (RuntimeException e) {
            log.warn("角色聊天LLM调用失败，historyCount: {}, ragEnabled: {}, costMs: {}",
                    messages.size(), ragPrompt != null && !ragPrompt.isBlank(),
                    System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    /**
     * 将历史消息折叠成普通文本，兼容当前 ChatClient API。
     */
    private String formatConversation(List<ChatHistoryMessage> messages, String ragPrompt) {
        StringBuilder builder = new StringBuilder();
        builder.append("【最近对话】\n");
        for (ChatHistoryMessage message : messages) {
            if (ROLE_USER.equals(message.role())) {
                builder.append("用户：").append(message.content()).append('\n');
            } else if (ROLE_ASSISTANT.equals(message.role())) {
                builder.append("你：").append(message.content()).append('\n');
            }
        }

        if (ragPrompt != null && !ragPrompt.isBlank()) {
            builder.append("\n").append(ragPrompt).append("\n");
        }
        builder.append("\n请继续回复最后一条用户消息，只输出角色本人的回复。");
        return builder.toString();
    }
}
