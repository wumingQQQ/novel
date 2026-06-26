package com.wuming.chat.config.llm;

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
    private Double temperature = 0.6;
    private Map<String, Double> taskTemperature = new HashMap<>();
    private Map<String, LlmProvider> providers = new HashMap<>();

    /**
     * 解析实际使用的模型供应商名称，未指定时使用默认供应商。
     */
    public String resolveProviderName(String name) {
        if (name == null || name.isBlank()) {
            return defaultProvider;
        }
        if (!providers.containsKey(name)) {
            log.warn("模型 {} 不存在，切换到默认模型 {}", name, defaultProvider);
            return defaultProvider;
        }
        return name;
    }

    /**
     * 获取指定供应商配置，缺失时直接失败，避免创建不可用模型。
     */
    public LlmProvider getProvider(String name) {
        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("模型 " + name + " 未配置");
        }
        return provider;
    }

    /**
     * 解析任务级温度配置，任务未单独配置时回退到默认温度。
     */
    public Double resolveTemperature(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return temperature;
        }
        return taskTemperature.getOrDefault(taskKey, temperature);
    }
}
