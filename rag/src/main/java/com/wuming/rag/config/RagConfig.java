package com.wuming.rag.config;


import com.wuming.rag.service.EmbeddingStoreRegistry;
import com.wuming.rag.service.DefaultRagQueryTransformer;
import com.wuming.rag.service.LlmRagQueryTransformer;
import com.wuming.rag.service.RagQueryTransformer;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.*;
import redis.clients.jedis.providers.PooledConnectionProvider;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({RagProperties.class, LlmProperties.class})
@Slf4j
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(RagProperties properties) {
        RagProperties.Embedding embedding = properties.getEmbedding();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(embedding.getApiKey())
                .modelName(embedding.getModel())
                .dimensions(embedding.getDimensions())
                .build();
    }

    @Bean
    public RagQueryTransformer queryTransformer(RagProperties ragProperties, LlmProperties llmProperties) {
        RagProperties.QueryRewrite queryRewrite = ragProperties.getQueryRewrite();
        if (queryRewrite == null || !queryRewrite.isEnabled()) {
            log.info("RAG查询重写未启用，使用默认查询转换器");
            return new DefaultRagQueryTransformer();
        }

        LlmProperties.DeepSeek deepSeek = llmProperties.getDeepseek();
        if (deepSeek == null
                || !hasText(deepSeek.getBaseUrl())
                || !hasText(deepSeek.getApiKey())
                || !hasText(deepSeek.getModel())) {
            log.warn("RAG查询重写配置不完整，使用默认查询转换器，请检查 llm.deepseek.base-url、api-key、model");
            return new DefaultRagQueryTransformer();
        }

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(deepSeek.getBaseUrl())
                .apiKey(deepSeek.getApiKey())
                .modelName(deepSeek.getModel())
                .temperature(llmProperties.getTemperature())
                .timeout(queryRewrite.getTimeout())
                .maxRetries(queryRewrite.getMaxRetries())
                .build();
        log.info("RAG查询重写已启用，model: {}, timeout: {}, maxRetries: {}",
                deepSeek.getModel(), queryRewrite.getTimeout(), queryRewrite.getMaxRetries());
        return new LlmRagQueryTransformer(chatModel, queryRewrite);
    }

    @Bean(destroyMethod = "close")
    public PooledConnectionProvider jedisPooled(RagProperties props,
                                                @Value("${rag.redis.pool.max-total:32}") int maxTotal,
                                                @Value("${rag.redis.pool.max-idle:16}") int maxIdle,
                                                @Value("${rag.redis.pool.min-idle:2}") int minIdle) {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);

        RagProperties.Redis redis = props.getRedis();

        String password = redis.getPassword();
        if (password == null || password.isBlank()) {
            password = null;
        }

        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(redis.getConnectionTimeoutMs())
                .socketTimeoutMillis(redis.getSocketTimeoutMs())
                .password(password)
                .database(redis.getDatabase())   // rag单独使用数据库
                .build();

        log.info("RAG Redis连接池初始化，host: {}, port: {}, database: {}, maxTotal: {}, maxIdle: {}, minIdle: {}",
                redis.getHost(), redis.getPort(), redis.getDatabase(), maxTotal, maxIdle, minIdle);

        return new PooledConnectionProvider(
                new HostAndPort(redis.getHost(), redis.getPort()),
                clientConfig,
                poolConfig
        );
    }

    @Bean(destroyMethod = "close")
    public UnifiedJedis unifiedJedis(PooledConnectionProvider redisConnectionProvider) {
        return new UnifiedJedis(redisConnectionProvider);
    }

    /**
     * 将已存在的Embedding store注册
     */
    @Bean
    public EmbeddingStoreRegistry registry(RagProperties props, UnifiedJedis unifiedJedis) {
        EmbeddingStoreRegistry registry = new EmbeddingStoreRegistry();

        props.getIndexes().forEach((indexName, index) -> {
            EmbeddingStore<TextSegment> store = RedisEmbeddingStore.builder()
                    .unifiedJedis(unifiedJedis)
                    .dimension(props.getEmbedding().getDimensions())
                    .indexName(index.getPhysicalIndexName())
                    .prefix(index.getKeyPrefix())
                    .metadataConfig(toRedisMetadataConfig(index.getMetadataFields()))
                    .build();

            registry.register(indexName, store);
            log.info("RAG向量索引注册完成，indexName: {}, physicalIndexName: {}, keyPrefix: {}, metadataFieldCount: {}",
                    indexName, index.getPhysicalIndexName(), index.getKeyPrefix(),
                    index.getMetadataFields() == null ? 0 : index.getMetadataFields().size());
        });

        return registry;
    }

    private Map<String, SchemaField> toRedisMetadataConfig(
            Map<String, RagProperties.MetadataFieldType> metadataFields
    ) {
        if (metadataFields == null || metadataFields.isEmpty()) {
            return Map.of();
        }

        Map<String, SchemaField> result = new LinkedHashMap<>();
        metadataFields.forEach((name, type) -> {
            SchemaField field = switch (type) {
                case NUMERIC -> NumericField.of("$." + name).as(name);
                case TAG -> TagField.of("$." + name).as(name);
                case TEXT -> TextField.of("$." + name).as(name).weight(1.0);
            };
            result.put(name, field);
        });
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
