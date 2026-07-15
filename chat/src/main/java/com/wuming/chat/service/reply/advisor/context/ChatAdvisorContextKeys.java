package com.wuming.chat.service.reply.advisor.context;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.chat.domain.model.ChatMemoryContext;
import com.wuming.chat.rag.role.RoleRetrievalSnapshot;

/**
 * Spring AI Advisor链中传递聊天上下文的参数名。
 */
public final class ChatAdvisorContextKeys {

    /** 单次角色聊天请求的业务上下文。 */
    public static final String REQUEST = RoleChatRequestContext.class.getName();

    /** 当前角色的运行时上下文。 */
    public static final String ROLE_CONTEXT = RoleRuntimeContextDto.class.getName();

    /** 当前会话的分层记忆快照。 */
    public static final String MEMORY_CONTEXT = ChatMemoryContext.class.getName();

    /** 当前输入召回到的角色参考材料。 */
    public static final String RETRIEVAL_CONTEXT = RoleRetrievalSnapshot.class.getName();

    private ChatAdvisorContextKeys() {
    }
}
