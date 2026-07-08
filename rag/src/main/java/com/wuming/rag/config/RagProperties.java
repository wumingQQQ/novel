package com.wuming.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Embedding embedding = new Embedding();
    private Reranker reranker = new Reranker();

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions = 1024;
    }

    @Data
    public static class Reranker {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private String model;
        private String path = "/v1/rerank";
        private int topN;
    }

}
