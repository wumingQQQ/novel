package com.wuming.chat.service.reply.advisor;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.chat.integration.rpc.role.RoleRuntimeContextService;
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
 * 加载本轮聊天使用的公共角色上下文或个人角色版本上下文。
 *
 * <p>该Advisor只采集结构化数据，不修改最终Prompt。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleContextAdvisor implements BaseAdvisor {

    private final RoleRuntimeContextService roleRuntimeContextService;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        RoleChatRequestContext chatRequest = ChatAdvisorContextAccessor.require(
                request,
                ChatAdvisorContextKeys.REQUEST,
                RoleChatRequestContext.class
        );

        long start = System.currentTimeMillis();
        RoleRuntimeContextDto roleContext = roleRuntimeContextService.getRuntimeContext(
                chatRequest.userId(),
                chatRequest.characterId(),
                chatRequest.userRoleVersionId()
        );
        log.debug(
                "角色聊天运行时上下文加载完成，sessionId: {}, characterId: {}, versionId: {}, costMs: {}",
                chatRequest.sessionId(),
                chatRequest.characterId(),
                chatRequest.userRoleVersionId(),
                System.currentTimeMillis() - start
        );
        return request.mutate()
                .context(ChatAdvisorContextKeys.ROLE_CONTEXT, roleContext)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return RoleChatAdvisorOrders.ROLE_CONTEXT;
    }
}
