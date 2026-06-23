package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisStageFailureStore {
    private final StringRedisTemplate stringRedisTemplate;

    public List<Long> consumeFailedLongItems(Long jobId, JobStage stage) {
        return consumeFailedItems(jobId, stage).stream()
                .map(Long::valueOf)
                .toList();
    }

    public void recordFailure(Long jobId, JobStage stage, Long itemId) {
        recordFailure(jobId, stage, itemId.toString());
    }

    public List<String> consumeFailedItems(Long jobId, JobStage stage) {
        List<String> failedItems = stringRedisTemplate.opsForList().range(failedKey(jobId, stage), 0, -1);
        if (failedItems == null || failedItems.isEmpty()) {
            return List.of();
        }
        stringRedisTemplate.delete(failedKey(jobId, stage));
        return failedItems.stream()
                .distinct()
                .toList();
    }

    public void recordFailure(Long jobId, JobStage stage, String itemId) {
        stringRedisTemplate.opsForList().rightPush(failedKey(jobId, stage), itemId);
    }

    public boolean hasFailures(Long jobId, JobStage stage) {
        return failureCount(jobId, stage) > 0;
    }

    public long failureCount(Long jobId, JobStage stage) {
        Long size = stringRedisTemplate.opsForList().size(failedKey(jobId, stage));
        return size == null ? 0 : size;
    }

    private String failedKey(Long jobId, JobStage stage) {
        return "job:" + jobId + ":" + stage.name() + ":failed";
    }
}
