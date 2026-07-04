package com.wuming.novel.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG相关配置。
 *
 * 该配置结构与chat模块保持一致，novel模块当前主要使用embedding与Redis
 * VectorStore索引角色原作样本。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * 向量模型配置
     */
    private Embedding embedding = new Embedding();

    /**
     * 重排序模型配置，预留给后续召回质量优化
     */
    private Reranker reranker = new Reranker();

    /**
     * Redis VectorStore配置
     */
    private Redis redis = new Redis();

    /**
     * 召回配置，预留给后续角色样本检索
     */
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
        /**
         * Redis Search索引名
         */
        private String index = "idx:rag:role-example";

        /**
         * 纳入索引的Redis key前缀
         */
        private String keyPrefix = "rag:role-example:";
    }

    @Data
    public static class Retrieve {
        /**
         * 向量检索阶段的粗召回数量
         */
        private Integer vectorTopK = 30;

        /**
         * 最终注入prompt的样本数量
         */
        private Integer contextTopN = 3;

        /**
         * rerank后允许注入提示词的最低分数
         */
        private double minScore = 0.35;

        /**
         * 注入提示词的总字符上限
         */
        private Integer maxContextChars = 3000;

        /**
         * 单条样本注入提示词的字符上限
         */
        private Integer maxContextCharsPerScene = 700;
    }
}
