package com.wuming.chat.infrastructure.cache;

import com.wuming.common.redis.core.RedisKey;
import com.wuming.common.redis.core.RedisStringOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePromptCacheService {
    private static final String KEY_PREFIX = "chat:profile-prompt";

    private final RedisStringOps redisStringOps;

    @Value("${chat.profile-prompt-cache-ttl:3d}")
    private Duration profilePromptCacheTtl;

    /**
     * 读取指定任务的角色系统提示词缓存。
     */
    public String get(Long jobId) {
        try {
            return redisStringOps.get(key(jobId));
        } catch (Exception e) {
            log.warn("job:{} 角色提示词读取Redis缓存失败", jobId, e);
            return null;
        }
    }

    /**
     * 缓存指定任务的角色系统提示词，减少每轮聊天的画像表查询。
     */
    public void put(Long jobId, String systemPrompt) {
        try {
            redisStringOps.set(key(jobId), systemPrompt, profilePromptCacheTtl);
        } catch (Exception e) {
            log.warn("job:{} 角色提示词写入Redis缓存失败", jobId, e);
        }
    }

    private String key(Long jobId) {
        return RedisKey.join(KEY_PREFIX, String.valueOf(jobId));
    }
}

