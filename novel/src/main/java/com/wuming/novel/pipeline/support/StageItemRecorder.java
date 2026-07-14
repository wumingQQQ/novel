package com.wuming.novel.pipeline.support;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.sse.JobProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pipeline计数型阶段的子项状态记录器。
 *
 * <p>统一维护Redis断点记录与SSE进度计数，避免各Step重复编排。</p>
 */
@Component
@RequiredArgsConstructor
public class StageItemRecorder {
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;

    /**
     * 从redis中加载完成项数，并将总数与已完成数的任务进度恢复
     * @param currentItemCount 待处理项数，通常为之前的失败项或者未处理时的全量项
     */
    public void initLongItemCounts(Long jobId, JobStage stage, int currentItemCount) {
        int completedCount = redisStageFailureStore.completedLongItems(jobId, stage).size();
        jobProgressService.setStageItemCounts(jobId, stage, completedCount + currentItemCount, completedCount, 0);
    }

    /**
     * 从redis中加载完成项数，并将总数与已完成数的任务进度恢复
     * @param currentItemCount 待处理项数，通常为之前的失败项或者未处理时的全量项
     */
    public void initStringItemCounts(Long jobId, JobStage stage, int currentItemCount) {
        int completedCount = redisStageFailureStore.completedItems(jobId, stage).size();
        jobProgressService.setStageItemCounts(jobId, stage, completedCount + currentItemCount, completedCount, 0);
    }

    /**
     * 将某个成功项id写入redis并更新到任务进度
     */
    public void recordLongSuccess(Long jobId, JobStage stage, Long itemId) {
        redisStageFailureStore.recordSuccess(jobId, stage, itemId);
        jobProgressService.recordItemSuccess(jobId, stage);
    }

    /**
     * 将某个失败项id写入redis并更新到任务进度
     */
    public void recordLongFailure(Long jobId, JobStage stage, Long itemId) {
        redisStageFailureStore.recordFailure(jobId, stage, itemId);
        jobProgressService.recordItemFailure(jobId, stage);
    }

    /**
     * 将某个成功项id写入redis并更新到任务进度
     */
    public void recordStringSuccess(Long jobId, JobStage stage, String itemId) {
        redisStageFailureStore.recordSuccess(jobId, stage, itemId);
        jobProgressService.recordItemSuccess(jobId, stage);
    }

    /**
     * 将某个成功项id写入redis并更新到任务进度
     */
    public void recordStringFailure(Long jobId, JobStage stage, String itemId) {
        redisStageFailureStore.recordFailure(jobId, stage, itemId);
        jobProgressService.recordItemFailure(jobId, stage);
    }
}
