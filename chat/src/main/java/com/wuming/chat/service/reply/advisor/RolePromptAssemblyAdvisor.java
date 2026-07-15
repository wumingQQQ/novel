package com.wuming.chat.service.reply.advisor;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.chat.domain.enums.ChatRole;
import com.wuming.chat.domain.model.ChatHistoryMessage;
import com.wuming.chat.domain.model.ChatMemoryContext;
import com.wuming.chat.rag.role.RoleRetrievalSnapshot;
import com.wuming.chat.service.reply.RoleChatPromptBuilder;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextAccessor;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextKeys;
import com.wuming.chat.service.reply.advisor.context.RoleChatRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将已采集的角色、分层记忆和RAG上下文统一装配为模型Prompt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePromptAssemblyAdvisor implements BaseAdvisor {

    private final RoleChatPromptBuilder promptBuilder;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        RoleChatRequestContext chatRequest = require(
                request,
                ChatAdvisorContextKeys.REQUEST,
                RoleChatRequestContext.class
        );

        RoleRuntimeContextDto roleContext = require(
                request,
                ChatAdvisorContextKeys.ROLE_CONTEXT,
                RoleRuntimeContextDto.class
        );

        ChatMemoryContext memoryContext = require(
                request,
                ChatAdvisorContextKeys.MEMORY_CONTEXT,
                ChatMemoryContext.class
        );

        RoleRetrievalSnapshot retrieval = require(
                request,
                ChatAdvisorContextKeys.RETRIEVAL_CONTEXT,
                RoleRetrievalSnapshot.class
        );

        UserMessage currentUserMessage = request.prompt().getUserMessage();
        if (currentUserMessage.getText() == null || currentUserMessage.getText().isBlank()) {
            throw new IllegalStateException("角色聊天Prompt缺少本轮用户消息");
        }
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                promptBuilder.buildSystemPrompt(
                        roleContext,
                        memoryContext,
                        retrieval
                )
        ));

        appendRecentMessages(
                messages,
                memoryContext.recentMessages(),
                chatRequest.sessionId()
        );

        // 本轮消息必须位于历史消息之后，且只注入一次
        messages.add(currentUserMessage.copy());

        // 为了在替换消息列表时保留模型、temperature等调用选项，同时避免共享可变配置
        ChatOptions originalOptions = request.prompt().getOptions();
        Prompt assembledPrompt = new Prompt(
                messages,
                originalOptions == null ? null : originalOptions.copy()
        );

        log.debug(
                "角色聊天Prompt装配完成，sessionId: {}, messageCount: {}, recentMessageCount: {}",
                chatRequest.sessionId(),
                messages.size(),
                memoryContext.recentMessages().size()
        );

        return request.mutate()
                .prompt(assembledPrompt)
                .build();
    }

    /** 按照原始角色和顺序注入尚未压缩的历史消息。 */
    private void appendRecentMessages(List<Message> target, List<ChatHistoryMessage> history, Long sessionId) {
        for (ChatHistoryMessage message : history) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                log.warn("忽略无效聊天历史消息，sessionId: {}", sessionId);
                continue;
            }

            if (ChatRole.USER.matches(message.role())) {
                target.add(new UserMessage(message.content()));
            } else if (ChatRole.ASSISTANT.matches(message.role())) {
                target.add(new AssistantMessage(message.content()));
            } else {
                log.warn(
                        "忽略未知角色的聊天历史消息，sessionId: {}, role: {}",
                        sessionId,
                        message.role()
                );
            }
        }
    }

    private <T> T require(ChatClientRequest request, String key, Class<T> type) {
        return ChatAdvisorContextAccessor.require(request, key, type);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return RoleChatAdvisorOrders.PROMPT_ASSEMBLY;
    }
}
