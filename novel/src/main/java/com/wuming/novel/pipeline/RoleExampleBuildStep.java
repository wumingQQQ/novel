package com.wuming.novel.pipeline;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IRoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 角色样本构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleExampleBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IRoleExampleService roleExampleService;

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
        IRoleExampleService.ExtractExamplesResult result =
                roleExampleService.extractExamples(job.getNovelId(), targetName);
        if (result.savedCount() <= 0) {
            log.info("目标角色未抽取到样本，jobId: {}, novelId: {}, characterId: {}, targetName: {}",
                    job.getId(), job.getNovelId(), result.characterId(), targetName);
        }
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
