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
    private Redis redis = new Redis();
    private Retrieve retrieve = new Retrieve();

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

    @Data
    public static class Redis {
        // 定义全局唯一的索引名，后续 FT.SEARCH 查询使用该标识符
        private String index = "idx:rag:scene";
        // 只有 key 符合该前缀的 HASH 才会被纳入索引
        private String keyPrefix = "rag:scene:";
    }

    @Data
    public static class Retrieve{
        // 向量检索阶段的粗召回数量
        private Integer vectorTopK = 30;
        // 最后注入上下文的最大场景数量
        private Integer contextTopN = 5;
        // rerank后允许注入提示词的最低分数
        private double minScore = 0.35;
        // 注入提示词的原文长度上限
        private Integer maxContextChars = 3000;

        private Integer maxContextCharsPerScene = 700;
    }
}
