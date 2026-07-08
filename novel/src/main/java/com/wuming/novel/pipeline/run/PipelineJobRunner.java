package com.wuming.novel.pipeline.run;

import com.wuming.novel.infrastructure.observability.TraceContext;
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
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            return submitWithLock(jobId);
        }
    }

    /**
     * 失败任务允许重新提交；运行中任务不重复提交，其他状态不重启。
     */
    public JobSubmitStatus redo(Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            JobProgress progress = jobProgressService.getProgress(jobId);
            if (progress == null) {
                log.info("任务不允许重跑，reason: progressNotFound");
                return JobSubmitStatus.NOT_RESTARTABLE;
            }
            if (progress.getState() == TaskState.RUNNING) {
                log.info("任务不重复提交，reason: alreadyRunning");
                return JobSubmitStatus.ALREADY_RUNNING;
            }
            if (progress.getState() != TaskState.FAILED) {
                log.info("任务不允许重跑，state: {}", progress.getState());
                return JobSubmitStatus.NOT_RESTARTABLE;
            }
            return submitWithLock(jobId);
        }
    }

    /**
     * 尝试获取任务运行锁，并把当前traceId传递给后台线程。
     */
    private JobSubmitStatus submitWithLock(Long jobId) {
        String runId = jobRunLock.tryLock(jobId);
        if (runId == null) {
            log.info("任务运行锁获取失败，submitStatus: ALREADY_RUNNING");
            return JobSubmitStatus.ALREADY_RUNNING;
        }

        String traceId = TraceContext.ensureTraceId();
        try {
            CompletableFuture.runAsync(() -> run(jobId, runId, traceId), pipelineExecutor);
            log.info("任务已提交到后台线程池，runId: {}", runId);
            return JobSubmitStatus.STARTED;
        } catch (RuntimeException e) {
            jobRunLock.release(jobId, runId);
            log.warn("任务提交后台线程池失败，runId: {}", runId, e);
            throw e;
        }
    }

    /**
     * 后台执行同步 pipeline，并确保无论成功失败都释放本次运行锁。
     */
    private void run(Long jobId, String runId, String traceId) {
        TraceContext.useTraceId(traceId);
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            long start = System.currentTimeMillis();
            log.info("后台任务开始执行，runId: {}", runId);
            try {
                pipelineService.handleNovel(jobId);
                log.info("后台任务执行完成，runId: {}, costMs: {}",
                        runId, System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                log.error("后台任务执行失败，runId: {}, costMs: {}",
                        runId, System.currentTimeMillis() - start, e);
            } finally {
                jobRunLock.release(jobId, runId);
                log.debug("任务运行锁已释放，runId: {}", runId);
            }
        } finally {
            TraceContext.clear();
        }
    }
}
