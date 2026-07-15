package com.wuming.chat.service.reply.advisor;

import com.wuming.chat.domain.model.ChatMemoryContext;
import com.wuming.chat.service.memory.ChatMemoryService;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextAccessor;
import com.wuming.chat.service.reply.advisor.context.ChatAdvisorContextKeys;
import com.wuming.chat.service.reply.advisor.context.RoleChatRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

/**
 * 加载本轮聊天可见的长期摘要和最近原始消息。
 *
 * <p>该Advisor只采集结构化数据，不修改最终Prompt。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayeredMemoryAdvisor implements BaseAdvisor {

    private final ChatMemoryService chatMemoryService;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        RoleChatRequestContext chatRequest = ChatAdvisorContextAccessor.require(
                request,
                ChatAdvisorContextKeys.REQUEST,
                RoleChatRequestContext.class
        );

        long start = System.currentTimeMillis();
        ChatMemoryContext memoryContext = chatMemoryService.prepareContext(
                chatRequest.sessionId(),
                chatRequest.currentUserMessageId()
        );
        log.debug(
                "角色聊天分层记忆加载完成，sessionId: {}, hasSummary: {}, recentMessageCount: {}, costMs: {}",
                chatRequest.sessionId(),
                !memoryContext.summaryContent().isBlank(),
                memoryContext.recentMessages().size(),
                System.currentTimeMillis() - start
        );
        return request.mutate()
                .context(ChatAdvisorContextKeys.MEMORY_CONTEXT, memoryContext)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return RoleChatAdvisorOrders.MEMORY_CONTEXT;
    }
}
