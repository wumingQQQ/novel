package com.wuming.novel.config.llm;


import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Setter
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private String default_provider = "deepseek";
    Map<String, LlmProvider> providers;

    public String resolveProviderName(String name) {
        if (name == null || name.isBlank()) {
            return default_provider;
        }
        if (!providers.containsKey(name)) {
            log.warn("模型 {} 不存在，切换到默认模型 {}", name, default_provider);
            return default_provider;
        }
        return name;
    }

    public LlmProvider getProvider(String name) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("未找到可用模型配置，请配置 llm.providers");
        }

        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("模型 " + name + " 未配置");
        }

        return provider;
    }
}
