package com.wuming.novel.config.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmClientFactory {
    private final LlmConfig llmConfig;
    private volatile ChatClient defaultClient;

    public ChatClient defaultClient() {
        ChatClient client = defaultClient;
        if (client == null) {
            synchronized (this) {
                client = defaultClient;
                if (client == null) {
                    client = ChatClient.builder(llmConfig.getDeepseek().chatModel(llmConfig.getTemperature())).build();
                    defaultClient = client;
                }
            }
        }
        return client;
    }
}
