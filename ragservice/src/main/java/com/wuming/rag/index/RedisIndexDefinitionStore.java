package com.wuming.rag.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.rag.config.RagServiceProperties;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

@Service
public class RedisIndexDefinitionStore {
    private final JedisPooled jedisPooled;
    private final ObjectMapper objectMapper;
    private final RagServiceProperties properties;

    public RedisIndexDefinitionStore(
            JedisPooled jedisPooled,
            ObjectMapper objectMapper,
            RagServiceProperties properties
    ) {
        this.jedisPooled = jedisPooled;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void save(RagIndexDefinition definition) {
        try {
            jedisPooled.set(key(definition.getIndexName()), objectMapper.writeValueAsString(definition));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("索引定义序列化失败", e);
        }
    }

    public RagIndexDefinition getRequired(String indexName) {
        String value = jedisPooled.get(key(indexName));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("索引不存在: " + indexName);
        }
        try {
            return objectMapper.readValue(value, RagIndexDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("索引定义反序列化失败: " + indexName, e);
        }
    }

    public boolean exists(String indexName) {
        return jedisPooled.exists(key(indexName));
    }

    private String key(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName不能为空");
        }
        return properties.getRedis().getIndexDefinitionKeyPrefix() + indexName;
    }
}
