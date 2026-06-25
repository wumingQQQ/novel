package com.wuming.novel.config.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LlmClientFactory {
    public static final String TASK_SCENE_SPLIT = "scene-split";
    public static final String TASK_SCENE_POOL = "scene-pool";
    public static final String TASK_LAYER_SPLIT = "layer-split";
    public static final String TASK_EVIDENCE_EXTRACT = "evidence-extract";
    public static final String TASK_AGGREGATION = "aggregation";
    public static final String TASK_PROFILE_DETAIL_ENHANCE = "profile-detail-enhance";

    private final LlmConfig llmConfig;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    public ChatClient defaultClient() {
        return client(null, null);
    }

    public ChatClient client(String providerName) {
        return client(providerName, null);
    }

    public ChatClient taskClient(String taskKey) {
        return client(null, taskKey);
    }

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
