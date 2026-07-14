package com.wuming.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private Double temperature = 0.0;
    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class DeepSeek {
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}
