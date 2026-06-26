package com.wuming.chat.config.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LlmClientFactory {
    public static final String TASK_ROLE_CHAT = "role-chat";

    private final LlmConfig llmConfig;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    /**
     * 按任务类型获取对应温度配置的聊天客户端。
     */
    public ChatClient taskClient(String taskKey) {
        return client(null, taskKey);
    }

    /**
     * 根据模型供应商和任务类型创建或复用 ChatClient。
     */
    public ChatClient client(String providerName, String taskKey) {
        String resolvedName = llmConfig.resolveProviderName(providerName);
        Double temperature = llmConfig.resolveTemperature(taskKey);
        String cacheKey = resolvedName + ":" + temperature;
        return cache.computeIfAbsent(cacheKey, key -> {
            LlmProvider provider = llmConfig.getProvider(resolvedName);
            return ChatClient.builder(provider.chatModel(temperature)).build();
        });
    }
}
