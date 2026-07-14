package com.wuming.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;


@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Redis redis = new Redis();
    private Embedding embedding = new Embedding();
    private Reranker reranker = new Reranker();
    private QueryRewrite queryRewrite = new QueryRewrite();
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
        private String baseUrl;
        private String apiKey;
        private String model;
        private String path = "/v1/rerank";
        private int topN;
    }

    @Data
    public static class QueryRewrite {
        private boolean enabled = true;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 1;
        private int multiQueryMaxCount = 4;
        private String promptTemplate = """
                你是RAG检索查询改写器。请根据聊天上下文和当前用户查询，将其改写为适合向量检索的中文查询。

                要求：
                1. 只输出一条改写后的查询，不要解释。
                2. 保留角色名、作品名、关系、场景、情绪、行为等检索关键要素。
                3. 如果原查询已经清晰，只做轻微补全。
                4. 不要编造上下文中没有的信息。

                当前查询：
                {{query}}
                """;
        private String multiQueryPromptTemplate = """
                你是RAG多路召回查询生成器。请基于当前用户查询，生成{{multiQueryMaxCount}}条适合向量检索的中文查询。

                要求：
                1. 每行只输出一条查询，不要编号，不要解释。
                2. 各查询应从不同角度覆盖语义，例如角色名、关系、对话风格、相处方式、场景行为、情绪反应。
                3. 保留原查询中的关键实体与专有名词。
                4. 不要编造原查询中没有的角色或作品。

                当前查询：
                {{query}}
                """;
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
