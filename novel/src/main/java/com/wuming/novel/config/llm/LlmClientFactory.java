package com.wuming.novel.config.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LlmClientFactory {

    private final LlmConfig llmConfig;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    public ChatClient defaultClient() {
        return client(null);
    }

    public ChatClient client(String providerName) {
        String resolvedName = llmConfig.resolveProviderName(providerName);
        return cache.computeIfAbsent(resolvedName, name -> {
            LlmProvider provider = llmConfig.getProvider(name);
            return ChatClient.builder(provider.chatModel()).build();
        });
    }
}
