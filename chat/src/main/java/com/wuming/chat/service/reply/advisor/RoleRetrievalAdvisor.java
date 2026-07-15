package com.wuming.chat.service.reply.advisor;

import com.wuming.chat.rag.role.RoleRetrievalSnapshot;
import com.wuming.chat.rag.role.RoleRuntimeRagService;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextAccessor;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextKeys;
import com.wuming.chat.service.reply.advisor.context.RoleChatRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 根据本轮用户输入召回角色反应规则和原作样本。
 *
 * <p>该Advisor只采集结构化数据，不修改最终Prompt。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleRetrievalAdvisor implements BaseAdvisor {

    private final RoleRuntimeRagService ragService;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        RoleChatRequestContext chatRequest = ChatAdvisorContextAccessor.require(
                request,
                ChatAdvisorContextKeys.REQUEST,
                RoleChatRequestContext.class
        );

        UserMessage userMessage = request.prompt().getUserMessage();
        if (userMessage.getText() == null || userMessage.getText().isBlank()) {
            throw new IllegalStateException("角色聊天Prompt缺少本轮用户消息");
        }

        RoleRetrievalSnapshot retrieval = ragService.retrieve(
                chatRequest.characterId(),
                userMessage.getText()
        );

        return request.mutate()
                .context(ChatAdvisorContextKeys.RETRIEVAL_CONTEXT, retrieval)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return RoleChatAdvisorOrders.RETRIEVAL_CONTEXT;
    }
}
