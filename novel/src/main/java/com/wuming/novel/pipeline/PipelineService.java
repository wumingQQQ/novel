package com.wuming.novel.pipeline;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.message.EventPublisher;
import com.wuming.novel.message.jobdone.JobFinishEvent;
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
    private final EventPublisher<JobFinishEvent> eventPublisher;


    /**
     * 任务正式开始
     */
    public boolean handleNovel(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }

        log.debug("job: {} 开始处理，当前阶段: {}", jobId, job.getStage());
        jobProgressService.initJob(jobId);
        jobProgressService.startJob(jobId);

        String failReason = null;
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
            failReason = e.getMessage();
            jobProgressService.failJob(jobId, e.getMessage());
            throw e;
        }
        finally {
            publishJobFinishEvent(jobId, failReason);
        }
    }

    private List<PipelineStep> orderedSteps() {
        return pipelineSteps.stream()
                .sorted(Comparator.comparingInt(step -> step.stage().getCode()))
                .toList();
    }

    /**
     * 发布任务失败或完成的事件，便于下游发邮件通知用户
     * @param failReason 失败原因
     */
    private void publishJobFinishEvent(Long jobId, String failReason) {
        Job job =  jobService.getById(jobId);
        JobFinishEvent event = new JobFinishEvent();
        event.setJobId(jobId);
        event.setUserId(job.getUserId());
        event.setNovelId(job.getNovelId());
        JobFinishEvent.JobFinishStatus status = job.getStage()==JobStage.COMPLETE
                ? JobFinishEvent.JobFinishStatus.SUCCESS
                : JobFinishEvent.JobFinishStatus.FAILED;
        event.setStatus(status);
        event.setFailReason(failReason);
        eventPublisher.publish(event);
    }

}
