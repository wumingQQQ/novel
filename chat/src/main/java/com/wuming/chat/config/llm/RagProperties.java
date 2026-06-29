package com.wuming.chat.config.llm;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Embedding embedding;
    private Reranker reranker;

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions;
    }

    @Data
    public static class Reranker {
        private String baseUrl;
        private String apiKey;
        private String model;
        private String path;
        private Integer topN;
    }
}
