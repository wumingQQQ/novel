package com.wuming.rag.config;

import com.wuming.rag.service.RagVectorStoreRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
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
    public VectorStore novel_passage(JedisPooled jedisPool, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPool, embeddingModel)
                .indexName("idx:novel_passage")
                .prefix("rag:novel_passage:")
                .initializeSchema(true)
                .embeddingFieldName("vector")
                .metadataFields(
                        MetadataField.numeric("novel_id"),
                        MetadataField.numeric("chapter_id"),
                        MetadataField.numeric("passage_id"),
                        MetadataField.numeric("passage_sequence")
                )
                .build();
    }

    @Bean
    public VectorStore role_example(JedisPooled jedisPool, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPool, embeddingModel)
                .indexName("idx:role_example")
                .prefix("rag:role_example:")
                .initializeSchema(true)
                .embeddingFieldName("vector")
                .metadataFields(
                        MetadataField.numeric("character_id"),
                        MetadataField.numeric("passage_sequence"),
                        MetadataField.text("character_name")
                )
                .build();
    }

    @Bean
    public VectorStore reaction_rule(JedisPooled jedisPool, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPool, embeddingModel)
                .indexName("idx:reaction_rule")
                .prefix("rag:reaction_rule:")
                .initializeSchema(true)
                .embeddingFieldName("vector")
                .metadataFields(
                        MetadataField.numeric("character_id"),
                        MetadataField.text("character_name")
                )
                .build();
    }

    @Bean
    public RagVectorStoreRegistry registry(
            VectorStore novel_passage,
            VectorStore role_example,
            VectorStore reaction_rule
    ){
        RagVectorStoreRegistry registry = new RagVectorStoreRegistry();
        registry.registerVectorStore("novel_passage", novel_passage);
        registry.registerVectorStore("role_example", role_example);
        registry.registerVectorStore("reaction_rule", reaction_rule);

        return registry;
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
