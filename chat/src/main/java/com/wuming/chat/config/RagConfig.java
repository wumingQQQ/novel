package com.wuming.chat.config;

import com.wuming.chat.config.llm.RagProperties;
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
                .dimensions(embedding.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }

    @Bean
    public JedisPooled jedisPool(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();
        String password = redisProperties.getPassword();

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("redis connect password can't be empty");
        }

        return new JedisPooled(host, port, null, password);
    }

    @Bean
    public VectorStore vectorStore(
            JedisPooled jedisPool,
            EmbeddingModel embeddingModel,
            RagProperties props
    ) {
        RagProperties.Redis redis = props.getRedis();
        return RedisVectorStore.builder(jedisPool, embeddingModel)
                .indexName(redis.getIndex())
                .prefix(redis.getKeyPrefix())
                .metadataFields(
                        MetadataField.numeric("jobId"),
                        MetadataField.numeric("novelId"),
                        MetadataField.numeric("chapterId"),
                        MetadataField.numeric("sceneId"),
                        MetadataField.numeric("chapterSequence"),
                        MetadataField.numeric("sceneSequence"),
                        MetadataField.numeric("chunkIndex")
                )
                .initializeSchema(true)
                .build();
    }
}
