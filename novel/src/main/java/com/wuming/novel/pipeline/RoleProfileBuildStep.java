package com.wuming.novel.pipeline;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.domain.enums.JobStage;
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
    private final PipelineJobSupport pipelineJobSupport;
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
        Job job = pipelineJobSupport.requireJob(jobId);
        String targetName = pipelineJobSupport.requireTargetName(job);
        Long characterId = pipelineJobSupport.targetCharacterId(job, targetName);
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
        boolean profileBuilt = roleProfileService.buildProfile(characterId);
        log.info("角色画像构建执行完成，jobId: {}, novelId: {}, characterId: {}, targetName: {}, profileBuilt: {}",
                job.getId(), job.getNovelId(), characterId, targetName, profileBuilt);
    }
}
