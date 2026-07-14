package com.wuming.novel.pipeline;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.IRoleCharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pipeline阶段通用任务上下文工具。
 *
 * <p>只承载跨Step重复的基础查询和校验，不放具体阶段业务逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class PipelineJobSupport {
    private final IJobService jobService;
    private final IRoleCharacterService roleCharacterService;

    public Job requireJob(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        return job;
    }

    public String requireTargetName(Job job) {
        return requireText(job.getTargetName(), "targetName不能为空");
    }

    public String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public Long targetCharacterId(Job job, String targetName) {
        return roleCharacterService.lambdaQuery()
                .eq(RoleCharacter::getNovelId, job.getNovelId())
                .eq(RoleCharacter::getCharacterName, targetName)
                .oneOpt()
                .map(RoleCharacter::getId)
                .orElse(null);
    }

    public Throwable rootCause(Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }
}
