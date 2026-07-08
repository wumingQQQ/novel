package com.wuming.chat.infrastructure.cache;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.common.redis.core.RedisJsonOps;
import com.wuming.common.redis.core.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RpcResultCacheService {
    private static final String USER_KEY_PREFIX = "chat:rpc:user";
    private static final String ROLE_RUNTIME_KEY_PREFIX = "chat:rpc:role-runtime";

    private final RedisJsonOps redisJsonOps;

    @Value("${chat.rpc-cache-ttl:2h}")
    private Duration rpcCacheTtl;

    /**
     * 读取用户远程校验结果缓存，缓存不存在或解析失败时返回null。
     */
    public UserResultDto getUserResult(Long userId) {
        return readValue(userKey(userId), UserResultDto.class,
                "用户远程校验结果", "userId", userId);
    }

    /**
     * 写入用户远程校验结果缓存，成功和业务失败结果都允许缓存。
     */
    public void putUserResult(Long userId, UserResultDto result) {
        writeValue(userKey(userId), result, "用户远程校验结果", "userId", userId);
    }

    /**
     * 读取角色运行时上下文缓存，缓存不存在或解析失败时返回null。
     */
    public RoleRuntimeContextDto getRoleRuntimeContext(Long characterId) {
        return readValue(roleRuntimeKey(characterId), RoleRuntimeContextDto.class,
                "角色运行时上下文", "characterId", characterId);
    }

    /**
     * 写入角色运行时上下文缓存，只缓存远程查询成功的结果。
     */
    public void putRoleRuntimeContext(Long characterId, RoleRuntimeContextDto context) {
        writeValue(roleRuntimeKey(characterId), context, "角色运行时上下文", "characterId", characterId);
    }

    /**
     * 从Redis读取JSON并反序列化为指定类型。
     */
    private <T> T readValue(String key, Class<T> type, String cacheName,
                            String idName, Long idValue) {
        try {
            T value = redisJsonOps.get(key, type);
            if (value == null) {
                log.debug("{}缓存未命中，{}: {}", cacheName, idName, idValue);
                return null;
            }
            log.debug("{}缓存命中，{}: {}", cacheName, idName, idValue);
            return value;
        } catch (Exception e) {
            log.warn("{}读取Redis缓存失败，{}: {}", cacheName, idName, idValue, e);
            return null;
        }
    }

    /**
     * 将对象序列化为JSON后写入Redis，并设置统一TTL。
     */
    private void writeValue(String key, Object value, String cacheName,
                            String idName, Long idValue) {
        if (value == null) {
            return;
        }
        try {
            redisJsonOps.set(key, value, rpcCacheTtl);
            log.debug("{}缓存写入完成，{}: {}", cacheName, idName, idValue);
        } catch (Exception e) {
            log.warn("{}写入Redis缓存失败，{}: {}", cacheName, idName, idValue, e);
        }
    }

    /**
     * 构造用户远程校验结果缓存key。
     */
    private String userKey(Long userId) {
        return RedisKey.join(USER_KEY_PREFIX, String.valueOf(userId));
    }

    /**
     * 构造角色运行时上下文缓存key。
     */
    private String roleRuntimeKey(Long characterId) {
        return RedisKey.join(ROLE_RUNTIME_KEY_PREFIX, String.valueOf(characterId));
    }
}

