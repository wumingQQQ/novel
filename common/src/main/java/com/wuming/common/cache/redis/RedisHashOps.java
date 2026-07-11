package com.wuming.common.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisHashOps {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 设置或更新key的某个字段
     * @param key 键
     * @param field 字段
     * @param value 值
     */
    public <T> void put(String key, String field, T value){
        redisTemplate.opsForHash().put(key, field, write(value));
    }

    public <T> T get(String key, String field, Class<T> clazz){
        Object json = redisTemplate.opsForHash().get(key, field);
        if(json == null){
            return null;
        }
        return read(json.toString(), clazz);
    }

    /**
     * 删除某个key的某些字段
     * @param fields 需要删除的字段数组
     * @return 删除的数量
     */
    public Long delete(String key, String...fields){
        return redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis json序列化失败");
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis json反序列化失败");
        }
    }

}
