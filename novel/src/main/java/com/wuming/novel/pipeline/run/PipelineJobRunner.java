package com.wuming.novel.pipeline.run;

import com.wuming.novel.pipeline.PipelineService;
import com.wuming.novel.sse.JobProgress;
import com.wuming.novel.sse.JobProgressService;
import com.wuming.novel.sse.TaskState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class PipelineJobRunner {
    private final PipelineService pipelineService;
    private final JobRunLock jobRunLock;
    private final JobProgressService jobProgressService;
    private final Executor pipelineExecutor;

    public PipelineJobRunner(
            PipelineService pipelineService,
            JobRunLock jobRunLock,
            JobProgressService jobProgressService,
            @Qualifier("pipelineExecutor") Executor pipelineExecutor
    ) {
        this.pipelineService = pipelineService;
        this.jobRunLock = jobRunLock;
        this.jobProgressService = jobProgressService;
        this.pipelineExecutor = pipelineExecutor;
    }

    /**
     * 提交一个后台流程任务；获取锁成功才会真正放入线程池执行。
     */
    public JobSubmitStatus submit(Long jobId) {
        return submitWithLock(jobId);
    }

    /**
     * 失败任务允许重新提交；运行中任务不重复提交，其他状态不重启。
     */
    public JobSubmitStatus redo(Long jobId) {
        JobProgress progress = jobProgressService.getProgress(jobId);
        if (progress == null) {
            return JobSubmitStatus.NOT_RESTARTABLE;
        }
        if (progress.getState() == TaskState.RUNNING) {
            return JobSubmitStatus.ALREADY_RUNNING;
        }
        if (progress.getState() != TaskState.FAILED) {
            return JobSubmitStatus.NOT_RESTARTABLE;
        }
        return submitWithLock(jobId);
    }

    private JobSubmitStatus submitWithLock(Long jobId) {
        String runId = jobRunLock.tryLock(jobId);
        if (runId == null) {
            return JobSubmitStatus.ALREADY_RUNNING;
        }

        try {
            CompletableFuture.runAsync(() -> run(jobId, runId), pipelineExecutor);
            return JobSubmitStatus.STARTED;
        } catch (RuntimeException e) {
            jobRunLock.release(jobId, runId);
            throw e;
        }
    }

    /**
     * 后台执行同步 pipeline，并确保无论成功失败都释放本次运行锁。
     */
    private void run(Long jobId, String runId) {
        try {
            pipelineService.handleNovel(jobId);
        } catch (RuntimeException e) {
            log.error("job: {} 异步流程执行失败", jobId, e);
        } finally {
            jobRunLock.release(jobId, runId);
        }
    }
}
