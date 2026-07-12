package com.wuming.chat.service;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.chat.domain.entity.ChatSession;
import com.wuming.chat.domain.model.ChatMemoryContext;
import com.wuming.chat.exception.ChatStreamClientClosedException;
import com.wuming.chat.integration.rpc.role.RoleRuntimeContextService;
import com.wuming.chat.rag.role.RoleRuntimeRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 负责准备角色回复上下文，并执行同步或流式LLM调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAssistantReplyService {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final ChatMemoryService chatMemoryService;
    private final RoleRuntimeContextService roleRuntimeContextService;
    private final RoleChatPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RoleRuntimeRagService roleRuntimeRagService;

    /**
     * 使用当前会话、用户输入和角色上下文同步请求角色回复。
     */
    String generateCompleteReply(ChatSession session, String userContent) {
        return requestWithContext(prepare(session, userContent));
    }

    /**
     * 使用当前会话、用户输入和角色上下文流式请求角色回复。
     */
    String generateStreamingReply(ChatSession session, String userContent, Predicate<String> chunkConsumer) {
        return requestStreamWithContext(prepare(session, userContent), chunkConsumer);
    }

    /**
     * 构造同步/流式LLM调用共享的上下文。
     *
     * <p>调用方应先保存本轮用户消息，使记忆服务能够读到最新输入。</p>
     */
    private ChatAssistantReplyRequest prepare(ChatSession session, String userContent) {
        RoleRuntimeContextDto runtimeContext = roleRuntimeContextService.getRuntimeContext(
                session.getUserId(), session.getCharacterId(), session.getUserRoleVersionId());
        String ragPrompt = roleRuntimeRagService.buildContextPrompt(runtimeContext.getCharacterId(), userContent);
        ChatMemoryContext memoryContext = chatMemoryService.prepareContext(session.getId());
        return new ChatAssistantReplyRequest(
                promptBuilder.buildSystemPrompt(runtimeContext),
                memoryContext,
                ragPrompt
        );
    }

    /**
     * 使用共享上下文同步请求角色回复。
     */
    private String requestWithContext(ChatAssistantReplyRequest request) {
        long start = System.currentTimeMillis();
        try {
            String content = chatClient
                    .prompt()
                    .system(request.systemPrompt())
                    .user(formatConversation(request.memoryContext(), request.ragPrompt()))
                    .call()
                    .content();
            log.info("角色聊天LLM调用完成，recentMessageCount: {}, memoryEnabled: {}, ragEnabled: {}, costMs: {}",
                    request.memoryContext().recentMessages().size(), isMemoryEnabled(request),
                    isRagEnabled(request), System.currentTimeMillis() - start);
            return content;
        } catch (RuntimeException e) {
            log.warn("角色聊天LLM调用失败，recentMessageCount: {}, memoryEnabled: {}, ragEnabled: {}, costMs: {}",
                    request.memoryContext().recentMessages().size(), isMemoryEnabled(request),
                    isRagEnabled(request), System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    /**
     * 使用共享上下文流式请求角色回复，并在必要时执行同步降级。
     */
    private String requestStreamWithContext(ChatAssistantReplyRequest request, Predicate<String> chunkConsumer) {
        long start = System.currentTimeMillis();
        StringBuilder content = new StringBuilder();
        AtomicBoolean chunkEmitted = new AtomicBoolean(false);
        try {
            try (Stream<String> chunks = chatClient
                    .prompt()
                    .system(request.systemPrompt())
                    .user(formatConversation(request.memoryContext(), request.ragPrompt()))
                    .stream()
                    .content()
                    .toStream()) {
                chunks.forEach(chunk -> appendAndSendChunk(content, chunkEmitted, chunkConsumer, chunk));
            }
            log.info("角色聊天流式LLM调用完成，recentMessageCount: {}, memoryEnabled: {}, ragEnabled: {}, costMs: {}",
                    request.memoryContext().recentMessages().size(), isMemoryEnabled(request),
                    isRagEnabled(request), System.currentTimeMillis() - start);
            return content.toString();
        } catch (ChatStreamClientClosedException e) {
            throw e;
        } catch (RuntimeException e) {
            if (!chunkEmitted.get()) {
                log.warn("角色聊天流式LLM调用在首个片段前失败，降级同步调用，recentMessageCount: {}, memoryEnabled: {}, ragEnabled: {}, costMs: {}",
                        request.memoryContext().recentMessages().size(), isMemoryEnabled(request),
                        isRagEnabled(request), System.currentTimeMillis() - start, e);
                return requestWithContext(request);
            }
            log.warn("角色聊天流式LLM调用失败，已输出部分片段，不执行同步降级，recentMessageCount: {}, memoryEnabled: {}, ragEnabled: {}, costMs: {}",
                    request.memoryContext().recentMessages().size(), isMemoryEnabled(request),
                    isRagEnabled(request), System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    /**
     * 累积单个流式片段并发送给客户端；发送失败说明SSE连接已经不可用。
     */
    private void appendAndSendChunk(StringBuilder content,
                                    AtomicBoolean chunkEmitted,
                                    Predicate<String> chunkConsumer,
                                    String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        content.append(chunk);
        chunkEmitted.set(true);
        if (chunkConsumer != null && !chunkConsumer.test(chunk)) {
            throw new ChatStreamClientClosedException();
        }
    }

    /** 判断本次请求是否注入了长期记忆摘要。 */
    private boolean isMemoryEnabled(ChatAssistantReplyRequest request) {
        return !request.memoryContext().summaryContent().isBlank();
    }

    /** 判断本次请求是否注入了RAG召回上下文。 */
    private boolean isRagEnabled(ChatAssistantReplyRequest request) {
        return request.ragPrompt() != null && !request.ragPrompt().isBlank();
    }

    /**
     * 将长期摘要、最近原文和 RAG 上下文折叠成普通文本，兼容当前 ChatClient API。
     */
    private String formatConversation(ChatMemoryContext memoryContext, String ragPrompt) {
        StringBuilder builder = new StringBuilder();
        if (!memoryContext.summaryContent().isBlank()) {
            builder.append("【长期记忆】\n")
                    .append(memoryContext.summaryContent())
                    .append("\n\n");
        }
        builder.append("【最近对话】\n");
        for (var message : memoryContext.recentMessages()) {
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

    /**
     * 同步和流式回复共用的LLM请求上下文。
     */
    private record ChatAssistantReplyRequest(String systemPrompt,
                                             ChatMemoryContext memoryContext,
                                             String ragPrompt) {
    }

}
