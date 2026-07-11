package com.wuming.novel.pipeline;

import com.wuming.common.cache.redis.RedisListOps;
import com.wuming.novel.domain.enums.JobStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis阶段子项状态存储。
 *
 * <p>这里的状态只用于阶段执行期间的断点续跑，不作为业务最终状态。
 * 阶段整体完成并推进任务进度后，需要清理对应Redis记录。</p>
 */
@Service
@RequiredArgsConstructor
public class RedisStageFailureStore {
    private final RedisListOps redisListOps;

    public List<Long> consumeFailedLongItems(Long jobId, JobStage stage) {
        return consumeFailedItems(jobId, stage).stream()
                .map(Long::valueOf)
                .toList();
    }

    public void recordFailure(Long jobId, JobStage stage, Long itemId) {
        recordFailure(jobId, stage, itemId.toString());
    }

    public void recordSuccess(Long jobId, JobStage stage, Long itemId) {
        recordSuccess(jobId, stage, itemId.toString());
    }

    /**
     * 读取并清空失败子项。
     *
     * <p>调用方拿到失败项后会立即重试；如果重试仍失败，会再次写回失败列表。</p>
     */
    public List<String> consumeFailedItems(Long jobId, JobStage stage) {
        List<String> failedItems = redisListOps.range(failedKey(jobId, stage), 0, -1);
        if (failedItems == null || failedItems.isEmpty()) {
            return List.of();
        }
        redisListOps.delete(failedKey(jobId, stage));
        return failedItems.stream()
                .distinct()
                .toList();
    }

    public void recordFailure(Long jobId, JobStage stage, String itemId) {
        redisListOps.rightPush(failedKey(jobId, stage), itemId);
    }

    /**
     * 记录阶段内已成功的子项，用于阶段未推进时跳过重复处理。
     */
    public void recordSuccess(Long jobId, JobStage stage, String itemId) {
        redisListOps.rightPush(successKey(jobId, stage), itemId);
    }

    public List<Long> completedLongItems(Long jobId, JobStage stage) {
        return completedItems(jobId, stage).stream()
                .map(Long::valueOf)
                .toList();
    }

    public List<String> completedItems(Long jobId, JobStage stage) {
        List<String> completedItems = redisListOps.range(successKey(jobId, stage), 0, -1);
        if (completedItems == null || completedItems.isEmpty()) {
            return List.of();
        }
        return completedItems.stream()
                .distinct()
                .toList();
    }

    public boolean hasFailures(Long jobId, JobStage stage) {
        return failureCount(jobId, stage) > 0;
    }

    public long failureCount(Long jobId, JobStage stage) {
        Long size = redisListOps.size(failedKey(jobId, stage));
        return size == null ? 0 : size;
    }

    /**
     * 阶段整体完成后清理临时状态，避免后续重新执行任务时被旧记录影响。
     */
    public void clearStage(Long jobId, JobStage stage) {
        redisListOps.delete(failedKey(jobId, stage));
        redisListOps.delete(successKey(jobId, stage));
    }

    private String failedKey(Long jobId, JobStage stage) {
        return "job:" + jobId + ":" + stage.name() + ":failed";
    }

    private String successKey(Long jobId, JobStage stage) {
        return "job:" + jobId + ":" + stage.name() + ":success";
    }
}
