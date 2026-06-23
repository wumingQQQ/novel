package com.wuming.novel.pipeline;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.sse.JobProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineService {
    private final IJobService jobService;
    private final List<PipelineStep> pipelineSteps;
    private final StageRetryExecutor stageRetryExecutor;
    private final JobProgressService jobProgressService;

    // TODO 将来各个阶段抛出异常则从原处恢复
    // TODO 针对前面几个与job无关的阶段可以检测是否已经完成，完成则跳过
    public boolean handleNovel(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }

        log.debug("job: {} 开始处理，当前阶段: {}", jobId, job.getStage());
        jobProgressService.initJob(jobId);
        jobProgressService.startJob(jobId);

        try {
            for (PipelineStep step : orderedSteps()) {
                if (step.stage().getCode() <= job.getStage().getCode()) {
                    jobProgressService.completeStage(jobId, step.stage(), step.name() + "已完成");
                    continue;
                }
                log.info("job: {} 开始{}阶段", jobId, step.name());
                jobProgressService.startStage(jobId, step.stage(), step.name());
                stageRetryExecutor.runWithRetry(jobId, step);
                jobService.advanceStage(jobId, step.stage());
                job.setStage(step.stage());
                jobProgressService.completeStage(jobId, step.stage(), step.name() + "完成");
            }
            jobService.advanceStage(jobId, JobStage.COMPLETE);
            jobProgressService.completeJob(jobId);
            return true;
        } catch (RuntimeException e) {
            jobProgressService.failJob(jobId, e.getMessage());
            throw e;
        }
    }

    private List<PipelineStep> orderedSteps() {
        return pipelineSteps.stream()
                .sorted(Comparator.comparingInt(step -> step.stage().getCode()))
                .toList();
    }

}
