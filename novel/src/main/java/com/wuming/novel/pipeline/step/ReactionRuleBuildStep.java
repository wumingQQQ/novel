package com.wuming.novel.pipeline.step;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.pipeline.PipelineStep;
import com.wuming.novel.pipeline.support.AsyncStageItemRunner;
import com.wuming.novel.pipeline.support.PipelineJobSupport;
import com.wuming.novel.pipeline.support.StageItemRecorder;
import com.wuming.novel.pipeline.support.StageItemSelector;
import com.wuming.novel.service.IRoleReactionRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 反应规则构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionRuleBuildStep implements PipelineStep {
    private final PipelineJobSupport pipelineJobSupport;
    private final StageItemSelector stageItemSelector;
    private final StageItemRecorder stageItemRecorder;
    private final AsyncStageItemRunner asyncStageItemRunner;
    private final IRoleReactionRuleService roleReactionRuleService;
    private final Executor llmExecutor;

    @Override
    public JobStage stage() {
        return JobStage.REACTION_RULE_BUILD;
    }

    @Override
    public String name() {
        return "反应规则构建";
    }

    @Override
    public void execute(Long jobId) {
        Job job = pipelineJobSupport.requireJob(jobId);
        String targetName = pipelineJobSupport.requireTargetName(job);
        Long characterId = pipelineJobSupport.targetCharacterId(job, targetName);
        if (characterId == null) {
            log.info("目标角色不存在，跳过反应规则构建，jobId: {}, novelId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), targetName);
            return;
        }

        List<String> situationKeys = targetSituationKeys(jobId, characterId);
        stageItemRecorder.initStringItemCounts(jobId, stage(), situationKeys.size());
        List<SituationRuleBuildResult> results = asyncStageItemRunner.supply(
                situationKeys,
                llmExecutor,
                situationKey -> roleReactionRuleService.buildRule(characterId, situationKey),
                (situationKey, savedCount, throwable) ->
                        finishOneSituation(jobId, job, characterId, situationKey, savedCount, throwable)
        );
        int savedCount = results.stream().mapToInt(SituationRuleBuildResult::savedCount).sum();
        int failedCount = (int) results.stream().filter(result -> !result.success()).count();
        if (failedCount == 0) {
            roleReactionRuleService.completeRuleBuild(characterId);
        }
        if (savedCount <= 0 && failedCount == 0) {
            log.info("目标角色未构建出反应规则，jobId: {}, novelId: {}, characterId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), characterId, targetName);
        }
        log.info("反应规则构建执行完成，jobId: {}, novelId: {}, characterId: {}, requestCount: {}, successCount: {}, savedCount: {}",
                jobId, job.getNovelId(), characterId, situationKeys.size(), situationKeys.size() - failedCount, savedCount);
    }

    /**
     * 完成单个情境规则构建子任务收尾，统一记录检查点和进度。
     */
    private SituationRuleBuildResult finishOneSituation(Long jobId,
                                                        Job job,
                                                        Long characterId,
                                                        String situationKey,
                                                        Integer savedCount,
                                                        Throwable throwable) {
        if (throwable == null) {
            stageItemRecorder.recordStringSuccess(jobId, stage(), situationKey);
            return new SituationRuleBuildResult(true, savedCount);
        }
        stageItemRecorder.recordStringFailure(jobId, stage(), situationKey);
        Throwable cause = pipelineJobSupport.rootCause(throwable);
        log.warn("情境反应规则构建失败，已记录失败项，jobId: {}, novelId: {}, characterId: {}, situationKey: {}, errorType: {}, errorMessage: {}",
                jobId, job.getNovelId(), characterId, situationKey,
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("情境反应规则构建失败堆栈，jobId: {}, novelId: {}, characterId: {}, situationKey: {}",
                jobId, job.getNovelId(), characterId, situationKey, throwable);
        return new SituationRuleBuildResult(false, 0);
    }

    /**
     * 获取本次需要构建反应规则的情境；存在失败项时只重试失败项，否则跳过已完成项。
     */
    private List<String> targetSituationKeys(Long jobId, Long characterId) {
        return stageItemSelector.selectStringItems(
                jobId,
                stage(),
                () -> roleReactionRuleService.situationKeys(characterId)
        );
    }

    private record SituationRuleBuildResult(boolean success, int savedCount) {
    }
}
