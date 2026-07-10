package com.wuming.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import com.wuming.chat.integration.rpc.role.RoleRuntimeContextService;
import com.wuming.chat.infrastructure.mapper.ChatMessageMapper;
import com.wuming.chat.infrastructure.mapper.ChatSessionMapper;
import com.wuming.chat.infrastructure.observability.TraceContext;
import com.wuming.chat.integration.rpc.user.UserContextService;
import com.wuming.chat.infrastructure.cache.ChatMessageCacheService;
import com.wuming.chat.rag.role.RoleRuntimeRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
    private final RoleRuntimeContextService roleRuntimeContextService;
    private final UserContextService userContextService;
    private final RoleChatPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RoleRuntimeRagService roleRuntimeRagService;

    @Value("${chat.history-limit:20}")
    private int historyLimit;

    /**
     * 创建聊天会话前先确认用户可用且对应角色已经生成完整画像。
     */
    @Transactional
    public Long createSession(Long userId, Long characterId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            log.info("开始创建聊天会话");
            try {
                Long sessionId = doCreateSession(userId, characterId);
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
    public SendChatMessageResponse sendMessage(Long userId, Long sessionId, String content) {
        ChatSession session = requireOwnedSession(sessionId, userId);
        try (TraceContext.MdcScope ignoredSession = TraceContext.putSessionId(sessionId);
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(session.getUserId())) {
            long start = System.currentTimeMillis();
            log.info("开始处理聊天消息");
            try {
                String userContent = requireContent(content);

                saveMessage(sessionId, ROLE_USER, userContent);
                RoleRuntimeContextDto runtimeContext = roleRuntimeContextService.getRuntimeContext(session.getCharacterId());

                String ragPrompt = roleRuntimeRagService.buildContextPrompt(runtimeContext.getCharacterId(), userContent);
                String assistantContent = requestAssistantReply(
                        promptBuilder.buildSystemPrompt(runtimeContext),
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
    private Long doCreateSession(Long userId, Long characterId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }

        UserResultDto result = userContextService.getRequiredUser(userId);
        if (!result.isSuccess()) {
            throw toUserBusinessException(result);
        }

        RoleRuntimeContextDto runtimeContext = roleRuntimeContextService.getRuntimeContext(characterId);

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setCharacterId(runtimeContext.getCharacterId());
        session.setStatus(SESSION_ACTIVE);
        chatSessionMapper.insert(session);
        return session.getId();
    }

    private BusinessException toUserBusinessException(UserResultDto result) {
        String code = result.getCode();
        if ("USER_NOT_FOUND".equals(code)) {
            return new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if ("USER_DISABLED".equals(code)) {
            return new BusinessException(ErrorCode.USER_DISABLED, "用户不可用");
        }
        if ("USER_INVALID".equals(code)) {
            return new BusinessException(ErrorCode.PARAM_ERROR, "用户参数无效");
        }
        return new BusinessException(ErrorCode.REMOTE_SERVICE_ERROR, "用户服务暂时不可用");
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
    public List<ChatMessage> listMessages(Long userId, Long sessionId) {
        requireOwnedSession(sessionId, userId);
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId)
        );
    }

    /**
     * 校验会话存在、处于可用状态，且归属于当前登录用户。
     */
    private ChatSession requireOwnedSession(Long sessionId, Long userId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !SESSION_ACTIVE.equals(session.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CHAT_SESSION_NOT_FOUND,
                    "聊天会话不存在或不可用: " + sessionId
            );
        }
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能访问其他用户的聊天会话");
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
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
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
            String content = chatClient
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


