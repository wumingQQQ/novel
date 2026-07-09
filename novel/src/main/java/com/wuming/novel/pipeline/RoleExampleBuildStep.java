package com.wuming.novel.pipeline;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IRoleExampleService;
import com.wuming.novel.sse.JobProgressService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 角色样本构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleExampleBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IRoleExampleService roleExampleService;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

    @Override
    public JobStage stage() {
        return JobStage.ROLE_EXAMPLE_BUILD;
    }

    @Override
    public String name() {
        return "角色样本构建";
    }

    @Override
    public void execute(Long jobId) {
        Job job = requireJob(jobId);
        String targetName = requireText(job.getTargetName(), "targetName不能为空");
        roleExampleService.startExampleExtraction(job.getNovelId(), targetName);
        List<Long> passageIds = targetPassageIds(jobId, job, targetName);
        int completedCount = redisStageFailureStore.completedLongItems(jobId, stage()).size();
        jobProgressService.setStageItemCounts(jobId, stage(), completedCount + passageIds.size(), completedCount, 0);
        List<PassageExampleBuildResult> results = passageIds.stream()
                .map(passageId -> CompletableFuture
                        .supplyAsync(() -> roleExampleService.extractExamplesFromPassage(job.getNovelId(), targetName, passageId), llmExecutor)
                        .handle((result, throwable) -> finishOnePassage(jobId, job, targetName, passageId, result, throwable))
                )
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
        int savedCount = results.stream().mapToInt(PassageExampleBuildResult::savedCount).sum();
        int failedCount = (int) results.stream().filter(result -> !result.success()).count();
        Long characterId = results.stream()
                .map(PassageExampleBuildResult::characterId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (failedCount == 0) {
            roleExampleService.completeExampleExtraction(job.getNovelId(), targetName, savedCount);
        }
        if (savedCount <= 0 && failedCount == 0) {
            log.info("目标角色本轮未抽取到样本，jobId: {}, novelId: {}, characterId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), characterId, targetName);
        }
        log.info("角色样本构建执行完成，jobId: {}, novelId: {}, targetName: {}, requestCount: {}, successCount: {}, savedCount: {}",
                jobId, job.getNovelId(), targetName, passageIds.size(), passageIds.size() - failedCount, savedCount);
    }

    /**
     * 完成单个Passage样本构建子任务收尾，统一记录检查点和进度。
     */
    private PassageExampleBuildResult finishOnePassage(Long jobId,
                                                       Job job,
                                                       String targetName,
                                                       Long passageId,
                                                       IRoleExampleService.ExtractExamplesResult result,
                                                       Throwable throwable) {
        if (throwable == null) {
            redisStageFailureStore.recordSuccess(jobId, stage(), passageId);
            jobProgressService.recordItemSuccess(jobId, stage());
            return new PassageExampleBuildResult(true, result.characterId(), result.savedCount());
        }
        redisStageFailureStore.recordFailure(jobId, stage(), passageId);
        jobProgressService.recordItemFailure(jobId, stage());
        log.warn("Passage角色样本构建失败，已记录失败项，jobId: {}, novelId: {}, passageId: {}, targetName: {}",
                jobId, job.getNovelId(), passageId, targetName, throwable);
        return new PassageExampleBuildResult(false, null, 0);
    }

    /**
     * 获取本次需要抽取角色样本的Passage；存在失败项时只重试失败项，否则跳过已完成项。
     */
    private List<Long> targetPassageIds(Long jobId, Job job, String targetName) {
        List<Long> failedPassageIds = redisStageFailureStore.consumeFailedLongItems(jobId, stage());
        if (!failedPassageIds.isEmpty()) {
            log.info("重试角色样本构建失败Passage，jobId: {}, novelId: {}, failedCount: {}",
                    jobId, job.getNovelId(), failedPassageIds.size());
            return failedPassageIds;
        }

        List<Long> completedPassageIds = redisStageFailureStore.completedLongItems(jobId, stage());
        List<Long> candidatePassageIds = roleExampleService.candidatePassageIds(job.getNovelId(), targetName);
        if (completedPassageIds.isEmpty()) {
            return candidatePassageIds;
        }
        log.info("跳过已完成角色样本构建Passage，jobId: {}, novelId: {}, completedCount: {}",
                jobId, job.getNovelId(), completedPassageIds.size());
        return candidatePassageIds.stream()
                .filter(passageId -> !completedPassageIds.contains(passageId))
                .toList();
    }

    private Job requireJob(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        return job;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private record PassageExampleBuildResult(boolean success, Long characterId, int savedCount) {
    }
}
