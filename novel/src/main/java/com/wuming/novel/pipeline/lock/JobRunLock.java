package com.wuming.novel.pipeline.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobRunLock {
    private static final Duration LOCK_TTL = Duration.ofHours(2);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class
            );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取 job 运行锁，返回本次运行的 runId；返回 null 表示已有流程在运行。
     */
    public String tryLock(Long jobId) {
        String runId = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey(jobId), runId, LOCK_TTL);
        return Boolean.TRUE.equals(locked) ? runId : null;
    }

    /**
     * 只释放当前 runId 持有的锁，避免误删后续新运行的锁。
     */
    public void release(Long jobId, String runId) {
        stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey(jobId)), runId);
    }

    /**
     * 服务重启恢复时清理悬挂运行锁；此时原后台线程已经不存在，不需要校验runId。
     */
    public void forceRelease(Long jobId) {
        stringRedisTemplate.delete(lockKey(jobId));
    }

    private String lockKey(Long jobId) {
        return "job:" + jobId + ":running";
    }
}
