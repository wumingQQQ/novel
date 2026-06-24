package com.wuming.novel.config;

import com.wuming.novel.config.llm.LlmClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    private final LlmClientFactory clientFactory;
    public ChatClientConfig(LlmClientFactory clientFactory){
        this.clientFactory = clientFactory;
    }

    @Bean
    public ChatClient chatClient(){
        return clientFactory.defaultClient();
    }
}
