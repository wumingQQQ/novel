package com.wuming.novel.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProgressStore {
    private static final Duration PROGRESS_TTL = Duration.ofDays(3);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 将任务进度序列化为 JSON 写入 Redis；失败只记录日志，不阻断主流程。
     */
    public void save(JobProgress progress) {
        try {
            stringRedisTemplate.opsForValue().set(
                    progressKey(progress.getJobId()),
                    objectMapper.writeValueAsString(progress),
                    PROGRESS_TTL
            );
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("任务进度保存失败，jobId: {}, errorType: {}, errorMessage: {}",
                    progress.getJobId(), e.getClass().getSimpleName(), e.getMessage());
            log.debug("任务进度保存失败堆栈，jobId: {}", progress.getJobId(), e);
        }
    }

    /**
     * 从 Redis 读取任务进度；Redis 或 JSON 异常时返回 null 交给本地流程兜底。
     */
    public JobProgress get(Long jobId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(progressKey(jobId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, JobProgress.class);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("任务进度读取失败，jobId: {}, errorType: {}, errorMessage: {}",
                    jobId, e.getClass().getSimpleName(), e.getMessage());
            log.debug("任务进度读取失败堆栈，jobId: {}", jobId, e);
            return null;
        }
    }

    /**
     * 删除 Redis 中的任务进度缓存；删除失败不影响业务流程。
     */
    public void delete(Long jobId) {
        try {
            stringRedisTemplate.delete(progressKey(jobId));
        } catch (RuntimeException e) {
            log.warn("任务进度删除失败，jobId: {}, errorType: {}, errorMessage: {}",
                    jobId, e.getClass().getSimpleName(), e.getMessage());
            log.debug("任务进度删除失败堆栈，jobId: {}", jobId, e);
        }
    }

    private String progressKey(Long jobId) {
        return "job:" + jobId + ":progress";
    }
}
