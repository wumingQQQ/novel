package com.wuming.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.chat.domain.dto.SendChatMessageResponse;
import com.wuming.chat.domain.dto.ChatSessionSummary;
import com.wuming.chat.domain.entity.ChatMessage;
import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.domain.enums.ChatRole;
import com.wuming.chat.domain.enums.ChatSessionStatus;
import com.wuming.chat.exception.ChatStreamClientClosedException;
import com.wuming.chat.integration.rpc.role.RoleRuntimeContextService;
import com.wuming.chat.infrastructure.mapper.ChatMessageMapper;
import com.wuming.chat.infrastructure.mapper.ChatSessionMapper;
import com.wuming.chat.infrastructure.observability.TraceContext;
import com.wuming.chat.integration.rpc.user.UserContextService;
import com.wuming.chat.infrastructure.cache.ChatMessageCacheService;
import com.wuming.chat.service.reply.ChatAssistantReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageCacheService chatMessageCacheService;
    private final RoleRuntimeContextService roleRuntimeContextService;
    private final UserContextService userContextService;
    private final ChatAssistantReplyService assistantReplyService;

    /**
     * 创建公共基线或用户个人版本绑定的聊天会话。
     *
     * @param userId 当前用户主键
     * @param characterId 公共角色主键
     * @param userRoleVersionId 可选个人角色版本主键
     * @return 新建会话主键
     */
    @Transactional
    public Long createSession(Long userId, Long characterId, Long userRoleVersionId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            log.info("开始创建聊天会话");
            try {
                Long sessionId = doCreateSession(userId, characterId, userRoleVersionId);
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
     * 发送用户消息，并等待LLM生成完整角色回复后返回。
     *
     * @param sessionId 聊天会话id
     * @param content 用户输入
     * @return 角色回复消息id和内容
     */
    public SendChatMessageResponse sendMessageWithCompleteReply(Long userId, Long sessionId, String content) {
        ChatSession session = requireOwnedSession(sessionId, userId);
        try (TraceContext.MdcScope ignoredSession = TraceContext.putSessionId(sessionId);
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(session.getUserId())) {
            long start = System.currentTimeMillis();
            log.info("开始处理聊天消息");
            try {
                String userContent = saveUserMessageAndReturnContent(session, content);
                String assistantContent = assistantReplyService.generateCompleteReply(session, userContent);

                ChatMessage assistantMessage = saveMessage(
                        sessionId,
                        ChatRole.ASSISTANT.code(),
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
     * 发送用户消息，并以流式方式生成角色回复；完整回复仍会在流结束后保存为助手消息。
     *
     * <p>如果上游在输出首个片段前失败，会降级为同步调用；一旦已经向客户端发送过片段，
     * 就不再降级，避免客户端收到重复或不连续的回复。</p>
     *
     * @param userId 当前用户主键
     * @param sessionId 聊天会话id
     * @param content 用户输入
     * @param chunkConsumer 单个LLM片段发送回调，返回false表示客户端已断开
     */
    public void sendMessageWithStreamingReply(Long userId,
                                              Long sessionId,
                                              String content,
                                              Predicate<String> chunkConsumer) {
        ChatSession session = requireOwnedSession(sessionId, userId);
        try (TraceContext.MdcScope ignoredSession = TraceContext.putSessionId(sessionId);
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(session.getUserId())) {
            long start = System.currentTimeMillis();
            log.info("开始处理流式聊天消息");
            try {
                String userContent = saveUserMessageAndReturnContent(session, content);
                String assistantContent = assistantReplyService.generateStreamingReply(session, userContent, chunkConsumer);
                ChatMessage assistantMessage = saveMessage(sessionId, ChatRole.ASSISTANT.code(), assistantContent);
                log.info("流式聊天消息处理完成，assistantMessageId: {}, costMs: {}",
                        assistantMessage.getId(), System.currentTimeMillis() - start);
            } catch (ChatStreamClientClosedException e) {
                log.info("流式聊天客户端已断开，停止保存助手回复，costMs: {}",
                        System.currentTimeMillis() - start);
                throw e;
            } catch (RuntimeException e) {
                log.warn("流式聊天消息处理失败，costMs: {}",
                        System.currentTimeMillis() - start, e);
                throw e;
            }
        }
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
     * 查询当前用户的会话摘要，供聊天页左侧会话列表使用。
     */
    public List<ChatSessionSummary> listSessions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        return chatSessionMapper.selectActiveSessionSummaries(userId);
    }

    /**
     * 执行聊天会话创建的核心逻辑，外层负责日志上下文和耗时统计。
     */
    private Long doCreateSession(Long userId, Long characterId, Long userRoleVersionId) {
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
        if (userRoleVersionId != null && !roleRuntimeContextService
                .validateUserRoleVersion(userId, runtimeContext.getCharacterId(), userRoleVersionId)) {
            throw new IllegalArgumentException("个人角色版本不属于当前用户或目标角色");
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setCharacterId(runtimeContext.getCharacterId());
        session.setUserRoleVersionId(userRoleVersionId);
        session.setStatus(ChatSessionStatus.ACTIVE.code());
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
        // 刷新排序时间，使会话列表始终按最近互动排列。
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        chatSessionMapper.updateById(session);
        chatMessageCacheService.append(message);
        return message;
    }

    /**
     * 校验用户输入后立即保存，使后续记忆构建能够读到本轮消息。
     */
    private String saveUserMessageAndReturnContent(ChatSession session, String content) {
        String userContent = requireContent(content);
        saveMessage(session.getId(), ChatRole.USER.code(), userContent);
        return userContent;
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
        if (session == null || !ChatSessionStatus.ACTIVE.matches(session.getStatus())) {
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

}


