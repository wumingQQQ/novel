package com.wuming.novel.pipeline.run;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStatus;
import com.wuming.novel.pipeline.lock.JobRunLock;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.sse.JobProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptedJobRecovery implements ApplicationRunner {
    private static final String DEFAULT_FAILURE_REASON = "服务重启导致任务中断，请重试";

    private final IJobService jobService;
    private final JobRunLock jobRunLock;
    private final JobProgressStore jobProgressStore;

    @Value("${novel.pipeline.recover-interrupted-running-jobs:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("中断任务启动恢复未启用");
            return;
        }

        List<Job> runningJobs = jobService.list(new LambdaQueryWrapper<Job>()
                .eq(Job::getStatus, JobStatus.RUNNING));
        if (runningJobs.isEmpty()) {
            return;
        }

        log.warn("检测到服务重启后的悬挂运行任务，count: {}", runningJobs.size());
        for (Job job : runningJobs) {
            recoverOne(job);
        }
    }

    private void recoverOne(Job job) {
        Long jobId = job.getId();
        jobService.markFailed(jobId, DEFAULT_FAILURE_REASON);
        jobRunLock.forceRelease(jobId);
        jobProgressStore.delete(jobId);
        log.warn("悬挂运行任务已标记失败，jobId: {}, stage: {}", jobId, job.getStage());
    }
}
