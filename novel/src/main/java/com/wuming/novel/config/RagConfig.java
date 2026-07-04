package com.wuming.novel.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import java.util.List;

/**
 * RAG基础设施配置。
 */
@Configuration
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(RagProperties ragProperties) {
        RagProperties.Embedding embedding = ragProperties.getEmbedding();

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(embedding.getApiKey())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embedding.getModel())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }

    @Bean
    public JedisPooled jedisPooled(RedisProperties redisProperties) {
        String password = redisProperties.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("redis connect password can't be empty");
        }
        return new JedisPooled(
                redisProperties.getHost(),
                redisProperties.getPort(),
                null,
                password
        );
    }

    @Bean
    public VectorStore vectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel,
            RagProperties ragProperties
    ) {
        RagProperties.Redis redis = ragProperties.getRedis();
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(redis.getIndex())
                .prefix(redis.getKeyPrefix())
                .metadataFields(metadataFields())
                .initializeSchema(true)
                .build();
    }

    private List<MetadataField> metadataFields() {
        return List.of(
                MetadataField.numeric("novelId"),
                MetadataField.numeric("chapterId"),
                MetadataField.numeric("passageId"),
                MetadataField.numeric("characterId"),
                MetadataField.numeric("exampleId"),
                MetadataField.text("sampleType")
        );
    }
}
