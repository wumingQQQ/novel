package com.wuming.rag.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import redis.clients.jedis.JedisPooled;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(RagProperties properties) {
        RagProperties.Embedding embedding = properties.getEmbedding();
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
            return new JedisPooled(redisProperties.getHost(), redisProperties.getPort());
        }
        return new JedisPooled(
                redisProperties.getHost(),
                redisProperties.getPort(),
                null,
                password
        );
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
