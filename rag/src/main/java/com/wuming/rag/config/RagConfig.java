package com.wuming.rag.config;


import com.wuming.rag.service.EmbeddingStoreRegistry;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
@EnableConfigurationProperties(RagProperties.class)
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

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
