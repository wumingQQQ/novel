package com.wuming.chat.config;

import com.wuming.chat.config.llm.LlmConfig;
import com.wuming.chat.service.reply.advisor.LayeredMemoryAdvisor;
import com.wuming.chat.service.reply.advisor.RoleContextAdvisor;
import com.wuming.chat.service.reply.advisor.RolePromptAssemblyAdvisor;
import com.wuming.chat.service.reply.advisor.RoleRetrievalAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    /**
     * 角色聊天主客户端，按顺序执行角色、分层记忆、RAG和Prompt装配Advisor。
     */
    @Bean
    public ChatClient roleChatClient(
            LlmConfig llmConfig,
            RoleContextAdvisor roleContextAdvisor,
            LayeredMemoryAdvisor layeredMemoryAdvisor,
            RoleRetrievalAdvisor roleRetrievalAdvisor,
            RolePromptAssemblyAdvisor rolePromptAssemblyAdvisor
    ) {
        return ChatClient.builder(llmConfig.getDeepseek().chatModel(llmConfig.getTemperature()))
                .defaultAdvisors(
                        roleContextAdvisor,
                        layeredMemoryAdvisor,
                        roleRetrievalAdvisor,
                        rolePromptAssemblyAdvisor
                )
                .build();
    }

    /**
     * 摘要专用客户端，不注入任何 Advisor，避免记忆和 RAG 上下文污染摘要结果。
     */
    @Bean
    public ChatClient summarizerChatClient(LlmConfig llmConfig) {
        return ChatClient.builder(llmConfig.getDeepseek().chatModel(llmConfig.getTemperature())).build();
    }
}
