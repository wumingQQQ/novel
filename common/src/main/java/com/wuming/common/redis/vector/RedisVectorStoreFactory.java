package com.wuming.common.redis.vector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.JedisPooled;

import java.util.List;

/**
 * 创建基于 Redis Stack 的 Spring AI 向量存储实例。
 *
 * <p>common 模块只沉淀向量存储的基础设施创建逻辑，不封装业务文档模型。
 * chat、novel 等业务模块仍然直接使用 Spring AI 的 Document、SearchRequest
 * 和 VectorStore，避免引入价值不高的中间 DTO。</p>
 */
public class RedisVectorStoreFactory {

    /**
     * 根据业务模块传入的索引、key 前缀和元数据字段创建向量存储。
     *
     * @param jedisPooled Redis 连接客户端
     * @param embeddingModel 文本向量化模型
     * @param indexName Redis Search 索引名称
     * @param keyPrefix Redis 文档 key 前缀
     * @param metadataFields 需要建立过滤索引的元数据字段
     * @return 可直接用于写入和检索的向量存储
     */
    public VectorStore create(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel,
            String indexName,
            String keyPrefix,
            List<RedisVectorStore.MetadataField> metadataFields
    ) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(keyPrefix)
                .metadataFields(metadataFields)
                .initializeSchema(true)
                .build();
    }
}
