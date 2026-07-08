package com.wuming.novel.pipeline;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleProfileService;
import com.wuming.novel.service.IRoleReactionRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 角色画像构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleProfileBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IRoleCharacterService roleCharacterService;
    private final IRoleReactionRuleService roleReactionRuleService;
    private final IRoleProfileService roleProfileService;

    @Override
    public JobStage stage() {
        return JobStage.ROLE_PROFILE_BUILD;
    }

    @Override
    public String name() {
        return "角色画像构建";
    }

    @Override
    public void execute(Long jobId) {
        Job job = requireJob(jobId);
        String targetName = requireText(job.getTargetName(), "targetName不能为空");
        Long characterId = targetCharacterId(job, targetName);
        if (characterId == null) {
            log.info("目标角色不存在，跳过角色画像构建，jobId: {}, novelId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), targetName);
            return;
        }
        long ruleCount = roleReactionRuleService.lambdaQuery()
                .eq(RoleReactionRule::getCharacterId, characterId)
                .count();
        if (ruleCount <= 0) {
            log.info("目标角色没有反应规则，跳过角色画像构建，jobId: {}, novelId: {}, characterId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), characterId, targetName);
            return;
        }
        roleProfileService.buildProfile(characterId);
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
}
