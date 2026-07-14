package com.wuming.novel.pipeline.step;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.pipeline.PipelineStep;
import com.wuming.novel.pipeline.support.AsyncStageItemRunner;
import com.wuming.novel.pipeline.support.PipelineJobSupport;
import com.wuming.novel.pipeline.support.StageItemRecorder;
import com.wuming.novel.pipeline.support.StageItemSelector;
import com.wuming.novel.service.IRoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 角色样本构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleExampleBuildStep implements PipelineStep {
    private final PipelineJobSupport pipelineJobSupport;
    private final StageItemSelector stageItemSelector;
    private final StageItemRecorder stageItemRecorder;
    private final AsyncStageItemRunner asyncStageItemRunner;
    private final IRoleExampleService roleExampleService;
    private final Executor llmExecutor;

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
        Job job = pipelineJobSupport.requireJob(jobId);
        String targetName = pipelineJobSupport.requireTargetName(job);
        roleExampleService.startExampleExtraction(job.getNovelId(), targetName);
        List<Long> passageIds = targetPassageIds(jobId, job, targetName);
        stageItemRecorder.initLongItemCounts(jobId, stage(), passageIds.size());
        List<PassageExampleBuildResult> results = asyncStageItemRunner.supply(
                passageIds,
                llmExecutor,
                passageId -> roleExampleService.extractExamplesFromPassage(job.getNovelId(), targetName, passageId),
                (passageId, result, throwable) -> finishOnePassage(jobId, job, targetName, passageId, result, throwable)
        );
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
            stageItemRecorder.recordLongSuccess(jobId, stage(), passageId);
            return new PassageExampleBuildResult(true, result.characterId(), result.savedCount());
        }
        stageItemRecorder.recordLongFailure(jobId, stage(), passageId);
        Throwable cause = pipelineJobSupport.rootCause(throwable);
        log.warn("Passage角色样本构建失败，已记录失败项，jobId: {}, novelId: {}, passageId: {}, targetName: {}, errorType: {}, errorMessage: {}",
                jobId, job.getNovelId(), passageId, targetName,
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("Passage角色样本构建失败堆栈，jobId: {}, novelId: {}, passageId: {}, targetName: {}",
                jobId, job.getNovelId(), passageId, targetName, throwable);
        return new PassageExampleBuildResult(false, null, 0);
    }

    /**
     * 获取本次需要抽取角色样本的Passage；存在失败项时只重试失败项，否则跳过已完成项。
     */
    private List<Long> targetPassageIds(Long jobId, Job job, String targetName) {
        return stageItemSelector.selectLongBackedItems(
                jobId, stage(), failedPassageIds -> failedPassageIds,
                () -> roleExampleService.candidatePassageIds(job.getNovelId(), targetName),
                passageId -> passageId
        );
    }

    private record PassageExampleBuildResult(boolean success, Long characterId, int savedCount) {
    }
}
