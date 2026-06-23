package com.wuming.novel.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StageRetryExecutor {
    private final RedisStageFailureStore redisStageFailureStore;

    @Value("${novel.pipeline.max-retry:3}")
    private int maxRetry;

    public void runWithRetry(Long jobId, PipelineStep step) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            log.debug("job: {} stage: {} 开始执行，第{}次尝试", jobId, step.stage(), attempt);
            try {
                step.execute(jobId);
                lastException = null;
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("job: {} stage: {} 第{}次执行异常", jobId, step.stage(), attempt, e);
            }

            long failureCount = redisStageFailureStore.failureCount(jobId, step.stage());
            if (lastException == null && failureCount == 0) {
                return;
            }

            if (failureCount > 0) {
                log.warn("job: {} stage: {} 第{}次执行后仍有失败项，失败数: {}",
                        jobId,
                        step.stage(),
                        attempt,
                        failureCount);
            } else {
                log.warn("job: {} stage: {} 第{}次执行异常且没有记录失败项，等待重试",
                        jobId,
                        step.stage(),
                        attempt);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("job: " + jobId + " stage: " + step.stage() + " 超过最大重试次数");
    }
}
