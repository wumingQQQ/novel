package com.wuming.novel.service.support;

import com.wuming.novel.domain.enums.NovelPreprocessStage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis checkpoint state shared by all preprocess jobs for one novel stage.
 */
@Service
public class NovelPreprocessCheckpointStore {
    private static final DefaultRedisScript<Long> TRY_START_ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            local attempts = tonumber(redis.call('GET', KEYS[1]) or '0')
            if attempts >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('INCR', KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RECORD_SUCCESS_SCRIPT = new DefaultRedisScript<>("""
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LREM', KEYS[2], 0, ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public NovelPreprocessCheckpointStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 记录章节成功，并原子移除同章节的失败检查点。 */
    public void recordSuccess(long novelId, NovelPreprocessStage stage, long chapterId) {
        recordSuccess(novelId, stage, Long.toString(chapterId));
    }

    /** 使用字符串章节标识记录成功，供 Redis 列表写入使用。 */
    public void recordSuccess(long novelId, NovelPreprocessStage stage, String chapterId) {
        redisTemplate.execute(
                RECORD_SUCCESS_SCRIPT,
                List.of(successKey(novelId, stage), failedKey(novelId, stage)),
                chapterId
        );
    }

    /** 记录章节失败，使后续取得小说锁的任务能够接管重试。 */
    public void recordFailure(long novelId, NovelPreprocessStage stage, long chapterId) {
        recordFailure(novelId, stage, Long.toString(chapterId));
    }

    /** 使用字符串章节标识记录失败，并去除同一章节的重复失败项。 */
    public void recordFailure(long novelId, NovelPreprocessStage stage, String chapterId) {
        // 底层使用lrem从失败项列表中移除chapterId
        redisTemplate.opsForList().remove(failedKey(novelId, stage), 0, chapterId);
        redisTemplate.opsForList().rightPush(failedKey(novelId, stage), chapterId);
    }

    /**
     * 选择当前持锁任务需要处理的章节。
     * 有失败项时仅重试失败章节；成功记录始终优先，避免重建已有产物。
     */
    public List<Long> selectItems(long novelId, NovelPreprocessStage stage, List<Long> candidateIds) {
        List<String> failedIds = items(failedKey(novelId, stage));
        Set<Long> succeeded = items(successKey(novelId, stage)).stream()
                .map(Long::valueOf)
                .collect(java.util.stream.Collectors.toSet());
        if (!failedIds.isEmpty()) {
            Set<Long> candidates = new HashSet<>(candidateIds);
            return failedIds.stream()
                    .map(Long::valueOf)
                    .filter(candidates::contains)
                    .filter(failedId -> !succeeded.contains(failedId))
                    .distinct()
                    .toList();
        }

        return candidateIds.stream()
                .filter(candidateId -> !succeeded.contains(candidateId))
                .toList();
    }

    /** 查询共享阶段的成功数、失败数与已开始尝试次数。 */
    public NovelPreprocessProgress progress(long novelId, NovelPreprocessStage stage) {
        long successCount = items(successKey(novelId, stage)).stream().distinct().count();
        long failureCount = items(failedKey(novelId, stage)).stream().distinct().count();
        long attemptCount = attemptCount(novelId, stage);
        return new NovelPreprocessProgress(successCount, failureCount, attemptCount);
    }

    /** 原子检查共享重试预算并在允许时开始一次新的尝试。 */
    public boolean tryStartAttempt(long novelId, NovelPreprocessStage stage, long maxAttempts) {
        Long started = redisTemplate.execute(
                TRY_START_ATTEMPT_SCRIPT,
                List.of(attemptsKey(novelId, stage)),
                Long.toString(maxAttempts)
        );
        return Long.valueOf(1L).equals(started);
    }

    /** 公共阶段成功后清除全部临时 Redis 检查点。 */
    public void clear(long novelId, NovelPreprocessStage stage) {
        redisTemplate.delete(successKey(novelId, stage));
        redisTemplate.delete(failedKey(novelId, stage));
        redisTemplate.delete(attemptsKey(novelId, stage));
    }

    /** 读取列表值，并将不存在的 Redis key 视为空列表。 */
    private List<String> items(String key) {
        List<String> values = redisTemplate.opsForList().range(key, 0, -1);
        return values == null ? List.of() : values;
    }

    /** 读取已消耗的共享重试次数。 */
    private long attemptCount(long novelId, NovelPreprocessStage stage) {
        String value = redisTemplate.opsForValue().get(attemptsKey(novelId, stage));
        return value == null ? 0 : Long.parseLong(value);
    }

    /** 生成成功章节检查点的 Redis key。 */
    private String successKey(long novelId, NovelPreprocessStage stage) {
        return key(novelId, stage, "success");
    }

    /** 生成失败章节检查点的 Redis key。 */
    private String failedKey(long novelId, NovelPreprocessStage stage) {
        return key(novelId, stage, "failed");
    }

    /** 生成共享尝试次数的 Redis key。 */
    private String attemptsKey(long novelId, NovelPreprocessStage stage) {
        return key(novelId, stage, "attempts");
    }

    /** 按小说、公共阶段和状态后缀生成隔离的 Redis key。 */
    private String key(long novelId, NovelPreprocessStage stage, String suffix) {
        return "novel:" + novelId + ":" + stage.name() + ":" + suffix;
    }
}
