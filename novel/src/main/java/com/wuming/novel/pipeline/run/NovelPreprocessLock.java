package com.wuming.novel.pipeline.run;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 同一小说公共预处理产物的分布式互斥锁。 */
@Service
@RequiredArgsConstructor
public class NovelPreprocessLock {
    private static final Duration LOCK_TTL = Duration.ofHours(6);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /** 尝试取得小说级锁，返回本次持有令牌；已有生产者时返回null。 */
    public String tryLock(Long novelId) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(novelId), token, LOCK_TTL);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    /** 仅持有相同令牌时释放锁，避免误删后续生产者的锁。 */
    public void release(Long novelId, String token) {
        stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey(novelId)), token);
    }

    private String lockKey(Long novelId) {
        return "novel:" + novelId + ":preprocess:running";
    }
}
