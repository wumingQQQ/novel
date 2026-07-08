package com.wuming.common.redis.core;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class RedisListOps {
    private final StringRedisTemplate redisTemplate;

    public void rightPush(String key, String value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表写入失败");
        }
    }

    public List<String> range(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表读取失败");
        }
    }

    public void trim(String key, long start, long end) {
        try {
            redisTemplate.opsForList().trim(key, start, end);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表裁剪失败");
        }
    }

    public Long size(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表长度读取失败");
        }
    }

    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表删除失败");
        }
    }

    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CACHE_OPERATION_FAILED, "Redis列表过期时间设置失败");
        }
    }
}
