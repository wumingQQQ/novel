package com.wuming.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;


@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Redis redis = new Redis();
    private Embedding embedding = new Embedding();
    private Reranker reranker = new Reranker();
    private Map<String, Index> indexes = new LinkedHashMap<>();

    @Data
    public static class Redis{
        private String host;
        private int port;
        private String password;
        int database;
        int connectionTimeoutMs;
        int socketTimeoutMs;
    }
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

    @Data
    public static class Index{
        private String physicalIndexName;
        private String keyPrefix;
        private Map<String, MetadataFieldType> metadataFields = new LinkedHashMap<>();
    }

    public enum MetadataFieldType{
        NUMERIC,
        TEXT,
        TAG
    }
}
