package com.wuming.chat.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.api.user.dto.UserResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RpcResultCacheService {
    private static final String USER_KEY_PREFIX = "chat:rpc:user:";
    private static final String PROFILE_KEY_PREFIX = "chat:rpc:profile:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.rpc-cache-ttl:2h}")
    private Duration rpcCacheTtl;

    /**
     * 读取用户远程校验结果缓存，缓存不存在或解析失败时返回null。
     */
    public UserResultDto getUserResult(Long userId) {
        return readValue(userKey(userId), UserResultDto.class, "用户远程校验结果", userId);
    }

    /**
     * 写入用户远程校验结果缓存，成功和业务失败结果都允许缓存。
     */
    public void putUserResult(Long userId, UserResultDto result) {
        writeValue(userKey(userId), result, "用户远程校验结果", userId);
    }

    /**
     * 读取角色画像上下文缓存，缓存不存在或解析失败时返回null。
     */
    public RoleContextDto getRoleContext(Long jobId) {
        return readValue(profileKey(jobId), RoleContextDto.class, "角色画像上下文", jobId);
    }

    /**
     * 写入角色画像上下文缓存，只由调用方在远程查询成功后调用。
     */
    public void putRoleContext(Long jobId, RoleContextDto context) {
        writeValue(profileKey(jobId), context, "角色画像上下文", jobId);
    }

    /**
     * 从Redis读取JSON并反序列化为指定类型。
     */
    private <T> T readValue(String key, Class<T> type, String cacheName, Long businessId) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                log.debug("{}缓存未命中，businessId: {}", cacheName, businessId);
                return null;
            }
            log.debug("{}缓存命中，businessId: {}", cacheName, businessId);
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            log.warn("{}缓存解析失败，businessId: {}", cacheName, businessId, e);
            return null;
        } catch (Exception e) {
            log.warn("{}读取Redis缓存失败，businessId: {}", cacheName, businessId, e);
            return null;
        }
    }

    /**
     * 将对象序列化为JSON后写入Redis，并设置统一TTL。
     */
    private void writeValue(String key, Object value, String cacheName, Long businessId) {
        if (value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    rpcCacheTtl
            );
            log.debug("{}缓存写入完成，businessId: {}", cacheName, businessId);
        } catch (Exception e) {
            log.warn("{}写入Redis缓存失败，businessId: {}", cacheName, businessId, e);
        }
    }

    /**
     * 构造用户远程校验结果缓存key。
     */
    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    /**
     * 构造角色画像上下文缓存key。
     */
    private String profileKey(Long jobId) {
        return PROFILE_KEY_PREFIX + jobId;
    }
}
