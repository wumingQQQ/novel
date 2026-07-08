package com.wuming.chat.config;

import com.wuming.chat.config.llm.LlmConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(LlmConfig llmConfig) {
        return ChatClient.builder(llmConfig.getDeepseek().chatModel(llmConfig.getTemperature())).build();
    }
}
