package com.wuming.chat.service.reply.advisor;

/**
 * 定义角色聊天Advisor的执行顺序。
 *
 * <p>数值越小越早执行，Prompt装配必须在所有上下文采集完成后执行。</p>
 */
public final class RoleChatAdvisorOrders {
    public static final int ROLE_CONTEXT = 100;
    public static final int MEMORY_CONTEXT = 200;
    public static final int RETRIEVAL_CONTEXT = 300;
    public static final int PROMPT_ASSEMBLY = 400;

    private RoleChatAdvisorOrders() {
    }
}
