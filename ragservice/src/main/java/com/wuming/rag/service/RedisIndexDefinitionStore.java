package com.wuming.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.rag.config.RagProperties;
import com.wuming.rag.model.RagIndexDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisIndexDefinitionStore {
    private final StringRedisTemplate redisTemplate;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    boolean exists(String indexName){
        return redisTemplate.hasKey(key(indexName));
    }

    /**
     * 保存索引库定义
     * @param definition
     * @throws JsonProcessingException
     */
    void save(RagIndexDefinition definition) throws JsonProcessingException {
        String indexName = definition.getIndexName();
        if(exists(indexName)){
            return;
        }
        String json = objectMapper.writeValueAsString(definition);
        redisTemplate.opsForValue().set(key(indexName), json);
    }

    /**
     * 读取已有索引库的定义
     * @param indexName
     * @return
     * @throws JsonProcessingException
     */
    RagIndexDefinition getRequired(String indexName) throws JsonProcessingException {
        String json = redisTemplate.opsForValue().get(key(indexName));
        return objectMapper.readValue(json, RagIndexDefinition.class);
    }

    String key(String indexName){
        return props.getDefinitionPrefix() + indexName;
    }
}
