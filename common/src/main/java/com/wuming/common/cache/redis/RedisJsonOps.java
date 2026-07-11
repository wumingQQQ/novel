package com.wuming.common.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 使用StringRedisTemplate+ObjectMapper，避免序列化问题
 */
@RequiredArgsConstructor
public class RedisJsonOps {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> void set(String key, T value, Duration ttl){
        String json = write(value);
        if(ttl == null){
            redisTemplate.opsForValue().set(key, json);
            return;
        }
        redisTemplate.opsForValue().set(key, json, ttl);
    }

    public <T> T get(String key, Class<T> clazz){
        String json = redisTemplate.opsForValue().get(key);
        if(json == null || json.isBlank()){
            return null;
        }
        return read(json, clazz);
    }

    public Boolean delete(String key){
        return redisTemplate.delete(key);
    }

    public Boolean exists(String key){
        return redisTemplate.hasKey(key);
    }

    private String write(Object value){
        try{
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e){
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis json序列化失败");
        }
    }
    private <T> T read(String json, Class<T> clazz){
        try{
            return objectMapper.readValue(json, clazz);
        }
        catch (JsonProcessingException e){
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis json反序列化失败");
        }
    }
}
