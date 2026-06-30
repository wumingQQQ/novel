package com.wuming.novel.pipeline;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.jobdone.JobFinishEvent;
import com.wuming.novel.infrastructure.observability.TraceContext;
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
     * 按阶段顺序执行小说画像构建流程，并在最终发布任务完成或失败事件。
     */
    public boolean handleNovel(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("该job不存在，请创建后重试");
        }

        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(job.getUserId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(job.getNovelId())) {
            long start = System.currentTimeMillis();
            log.info("任务流程开始，currentStage: {}", job.getStage());
            jobProgressService.initJob(jobId);
            jobProgressService.startJob(jobId);

            String failReason = null;
            try {
                for (PipelineStep step : orderedSteps()) {
                    runStepIfNeeded(jobId, job, step);
                }
                jobService.advanceStage(jobId, JobStage.COMPLETE);
                jobProgressService.completeJob(jobId);
                log.info("任务流程完成，costMs: {}", System.currentTimeMillis() - start);
                return true;
            } catch (RuntimeException e) {
                failReason = e.getMessage();
                jobProgressService.failJob(jobId, e.getMessage());
                log.warn("任务流程失败，costMs: {}", System.currentTimeMillis() - start, e);
                throw e;
            } finally {
                publishJobFinishEvent(jobId, failReason);
            }
        }
    }

    /**
     * 返回按阶段编码排序后的流程步骤。
     */
    private List<PipelineStep> orderedSteps() {
        return pipelineSteps.stream()
                .sorted(Comparator.comparingInt(step -> step.stage().getCode()))
                .toList();
    }

    /**
     * 跳过已经完成的阶段，执行未完成阶段并推进任务状态。
     */
    private void runStepIfNeeded(Long jobId, Job job, PipelineStep step) {
        try (TraceContext.MdcScope ignoredStage = TraceContext.putStage(step.stage())) {
            if (step.stage().getCode() <= job.getStage().getCode()) {
                jobProgressService.completeStage(jobId, step.stage(), step.name() + "已完成");
                log.debug("跳过已完成阶段，stageName: {}", step.name());
                return;
            }

            long start = System.currentTimeMillis();
            log.info("任务阶段开始，stageName: {}", step.name());
            jobProgressService.startStage(jobId, step.stage(), step.name());
            stageRetryExecutor.runWithRetry(jobId, step);
            jobService.advanceStage(jobId, step.stage());
            job.setStage(step.stage());
            jobProgressService.completeStage(jobId, step.stage(), step.name() + "完成");
            log.info("任务阶段完成，stageName: {}, costMs: {}",
                    step.name(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 发布任务失败或完成事件，便于下游发邮件或用户通知。
     *
     * @param jobId 任务id
     * @param failReason 失败原因，成功时为空
     */
    private void publishJobFinishEvent(Long jobId, String failReason) {
        Job job = jobService.getById(jobId);
        JobFinishEvent event = new JobFinishEvent();
        event.setJobId(jobId);
        event.setUserId(job.getUserId());
        event.setNovelId(job.getNovelId());
        JobFinishEvent.JobFinishStatus status = job.getStage() == JobStage.COMPLETE
                ? JobFinishEvent.JobFinishStatus.SUCCESS
                : JobFinishEvent.JobFinishStatus.FAILED;
        event.setStatus(status);
        event.setFailReason(failReason);
        try {
            eventPublisher.publish(event);
        } catch (RuntimeException e) {
            log.warn("job完成事件发布失败，status: {}", status, e);
        }
    }
}
