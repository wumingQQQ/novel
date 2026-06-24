package com.wuming.novel.config.llm;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private String defaultProvider = "deepseek";
    private Double temperature = 0.0;
    private Map<String, Double> taskTemperature = new HashMap<>();
    Map<String, LlmProvider> providers;

    public String resolveProviderName(String name) {
        if (name == null || name.isBlank()) {
            return defaultProvider;
        }
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("未找到可用模型配置，请配置 llm.providers");
        }

        if (!providers.containsKey(name)) {
            log.warn("模型 {} 不存在，切换到默认模型 {}", name, defaultProvider);
            return defaultProvider;
        }
        return name;
    }

    public LlmProvider getProvider(String name) {

        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("模型 " + name + " 未配置");
        }

        return provider;
    }

    public Double resolveTemperature(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return temperature;
        }
        return taskTemperature.getOrDefault(taskKey, temperature);
    }
}
