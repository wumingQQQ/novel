package com.wuming.rag.config;

import com.wuming.api.rag.enums.MetadataFieldType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rag")
public class RagServiceProperties {

    private Embedding embedding = new Embedding();
    private Reranker reranker = new Reranker();
    private Retrieve retrieve = new Retrieve();
    private Redis redis = new Redis();

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions = 1024;
    }

    @Data
    public static class Reranker {
        private boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private String model;
        private String path = "/v1/rerank";
        private Integer topN = 10;
    }

    @Data
    public static class Retrieve {
        private Integer defaultTopK = 5;
        private double minScore = 0.0;
    }

    @Data
    public static class Redis {
        private String indexDefinitionKeyPrefix = "rag:index-definition:";
        private Map<String, MetadataFieldType> reservedVectorFields = new LinkedHashMap<>();
    }
}
