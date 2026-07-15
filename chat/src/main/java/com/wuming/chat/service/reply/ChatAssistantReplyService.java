package com.wuming.chat.service.reply;

import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.exception.ChatStreamClientClosedException;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextKeys;
import com.wuming.chat.service.reply.advisor.context.RoleChatRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 通过角色聊天专用ChatClient执行同步或流式LLM调用。
 *
 * <p>角色、分层记忆和RAG上下文由ChatClient中的Advisor链统一准备。</p>
 */
@Slf4j
@Service
public class ChatAssistantReplyService {

    private final ChatClient roleChatClient;

    public ChatAssistantReplyService(@Qualifier("roleChatClient") ChatClient roleChatClient) {
        this.roleChatClient = roleChatClient;
    }

    /**
     * 使用当前会话和已保存的用户消息同步生成完整角色回复。
     */
    public String generateCompleteReply(
            ChatSession session,
            Long currentUserMessageId,
            String userContent
    ) {
        RoleChatRequestContext requestContext = buildRequestContext(session, currentUserMessageId);
        return requestCompleteReply(requestContext, userContent);
    }

    /**
     * 使用当前会话和已保存的用户消息流式生成角色回复。
     *
     * <p>首个片段前失败时降级为同步调用；已经输出片段后失败时不再降级，
     * 避免客户端收到重复或不连续的回复。</p>
     */
    public String generateStreamingReply(
            ChatSession session,
            Long currentUserMessageId,
            String userContent,
            Predicate<String> chunkConsumer
    ) {
        RoleChatRequestContext requestContext = buildRequestContext(session, currentUserMessageId);
        return requestStreamingReply(requestContext, userContent, chunkConsumer);
    }

    /**
     * 执行带有角色聊天Advisor上下文的同步调用。
     */
    private String requestCompleteReply(RoleChatRequestContext requestContext, String userContent) {
        long start = System.currentTimeMillis();
        try {
            String content = roleChatClient.prompt()
                    .user(userContent)
                    .advisors(spec -> spec.param(ChatAdvisorContextKeys.REQUEST, requestContext))
                    .call()
                    .content();
            log.info("角色聊天LLM调用完成，sessionId: {}, costMs: {}",
                    requestContext.sessionId(), System.currentTimeMillis() - start);
            return content;
        } catch (RuntimeException e) {
            log.warn("角色聊天LLM调用失败，sessionId: {}, costMs: {}",
                    requestContext.sessionId(), System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    /**
     * 执行流式调用并累计完整回复，供流结束后持久化。
     */
    private String requestStreamingReply(
            RoleChatRequestContext requestContext,
            String userContent,
            Predicate<String> chunkConsumer
    ) {
        long start = System.currentTimeMillis();
        StringBuilder content = new StringBuilder();
        AtomicBoolean chunkEmitted = new AtomicBoolean(false);
        try {
            try (Stream<String> chunks = roleChatClient.prompt()
                    .user(userContent)
                    .advisors(spec -> spec.param(ChatAdvisorContextKeys.REQUEST, requestContext))
                    .stream()
                    .content()
                    .toStream()) {
                chunks.forEach(chunk -> appendAndSendChunk(content, chunkEmitted, chunkConsumer, chunk));
            }
            log.info("角色聊天流式LLM调用完成，sessionId: {}, costMs: {}",
                    requestContext.sessionId(), System.currentTimeMillis() - start);
            return content.toString();
        } catch (ChatStreamClientClosedException e) {
            throw e;
        } catch (RuntimeException e) {
            if (!chunkEmitted.get()) {
                log.warn("角色聊天流式调用在首个片段前失败，降级同步调用，sessionId: {}, costMs: {}",
                        requestContext.sessionId(), System.currentTimeMillis() - start, e);
                String fallbackContent = requestCompleteReply(requestContext, userContent);
                appendAndSendChunk(content, chunkEmitted, chunkConsumer, fallbackContent);
                return content.toString();
            }
            log.warn("角色聊天流式调用失败，已输出部分片段，不执行同步降级，sessionId: {}, costMs: {}",
                    requestContext.sessionId(), System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    /**
     * 构造单次角色聊天Advisor链需要的业务上下文。
     */
    private RoleChatRequestContext buildRequestContext(ChatSession session, Long currentUserMessageId) {
        return new RoleChatRequestContext(
                session.getUserId(),
                session.getId(),
                session.getCharacterId(),
                session.getUserRoleVersionId(),
                currentUserMessageId
        );
    }

    /**
     * 累积单个流式片段并发送给客户端；发送失败说明SSE连接已经不可用。
     */
    private void appendAndSendChunk(
            StringBuilder content,
            AtomicBoolean chunkEmitted,
            Predicate<String> chunkConsumer,
            String chunk
    ) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        content.append(chunk);
        chunkEmitted.set(true);
        if (chunkConsumer != null && !chunkConsumer.test(chunk)) {
            throw new ChatStreamClientClosedException();
        }
    }
}
