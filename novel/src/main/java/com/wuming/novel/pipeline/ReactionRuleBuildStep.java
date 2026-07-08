package com.wuming.novel.pipeline;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleReactionRuleService;
import com.wuming.novel.sse.JobProgressService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 反应规则构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionRuleBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IRoleCharacterService roleCharacterService;
    private final IRoleReactionRuleService roleReactionRuleService;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

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
        Job job = requireJob(jobId);
        String targetName = requireText(job.getTargetName(), "targetName不能为空");
        Long characterId = targetCharacterId(job, targetName);
        if (characterId == null) {
            log.info("目标角色不存在，跳过反应规则构建，jobId: {}, novelId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), targetName);
            return;
        }

        List<String> situationKeys = targetSituationKeys(jobId, characterId);
        jobProgressService.setStageTotalItems(jobId, stage(), situationKeys.size());
        List<SituationRuleBuildResult> results = situationKeys.stream()
                .map(situationKey -> CompletableFuture
                        .supplyAsync(
                                () -> roleReactionRuleService.buildRule(characterId, situationKey),
                                llmExecutor)
                        .handle((savedCount, throwable) ->
                                finishOneSituation(jobId, job, characterId, situationKey, savedCount, throwable)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
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
            redisStageFailureStore.recordSuccess(jobId, stage(), situationKey);
            jobProgressService.recordItemSuccess(jobId, stage());
            return new SituationRuleBuildResult(true, savedCount);
        }
        redisStageFailureStore.recordFailure(jobId, stage(), situationKey);
        jobProgressService.recordItemFailure(jobId, stage());
        log.warn("情境反应规则构建失败，已记录失败项，jobId: {}, novelId: {}, characterId: {}, situationKey: {}",
                jobId, job.getNovelId(), characterId, situationKey, throwable);
        return new SituationRuleBuildResult(false, 0);
    }

    /**
     * 获取本次需要构建反应规则的情境；存在失败项时只重试失败项，否则跳过已完成项。
     */
    private List<String> targetSituationKeys(Long jobId, Long characterId) {
        List<String> failedSituationKeys = redisStageFailureStore.consumeFailedItems(jobId, stage());
        if (!failedSituationKeys.isEmpty()) {
            log.info("重试反应规则构建失败情境，jobId: {}, characterId: {}, failedCount: {}",
                    jobId, characterId, failedSituationKeys.size());
            return failedSituationKeys;
        }

        List<String> completedSituationKeys = redisStageFailureStore.completedItems(jobId, stage());
        List<String> situationKeys = roleReactionRuleService.situationKeys(characterId);
        if (completedSituationKeys.isEmpty()) {
            return situationKeys;
        }
        log.info("跳过已完成反应规则构建情境，jobId: {}, characterId: {}, completedCount: {}",
                jobId, characterId, completedSituationKeys.size());
        return situationKeys.stream()
                .filter(situationKey -> !completedSituationKeys.contains(situationKey))
                .toList();
    }

    private Long targetCharacterId(Job job, String targetName) {
        return roleCharacterService.lambdaQuery()
                .eq(RoleCharacter::getNovelId, job.getNovelId())
                .eq(RoleCharacter::getCharacterName, targetName)
                .oneOpt()
                .map(RoleCharacter::getId)
                .orElse(null);
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

    private record SituationRuleBuildResult(boolean success, int savedCount) {
    }
}
