package com.wuming.novel.schedule;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.service.impl.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFailedJobCleaner {
    private static final String FAILED_KEY_PATTERN = "job:*:*:failed";
    private static final int SCAN_COUNT = 100;
    private static final long EXPIRE_MONTHS = 3L;

    private final JobService jobService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 定时清理满足条件的失败任务
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void clean() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(FAILED_KEY_PATTERN)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            Map<Long, Job> jobCache = new HashMap<>();
            List<String> keysToDelete = new ArrayList<>();

            while (cursor.hasNext()) {
                String key = cursor.next();
                Long jobId = parseJobId(key);
                if (jobId == null) {
                    continue;
                }

                Job job = jobCache.computeIfAbsent(jobId, jobService::getById);
                if (job == null || isLongTerm(job.getUpdateTime())) {
                    keysToDelete.add(key);
                }
            }

            if (!keysToDelete.isEmpty()) {
                stringRedisTemplate.unlink(keysToDelete);
                log.debug("redis清理过期失败项key数量: {}", keysToDelete.size());
            }
        } catch (Exception e) {
            log.error("redis清理垃圾job时出现异常", e);
        }
    }

    /**
     * 将redis的key转为jobId
     * @param key redis key
     * @return jobId
     */
    private Long parseJobId(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4 || !"job".equals(parts[0]) || !"failed".equals(parts[3])) {
            log.warn("redis失败项key格式异常，跳过清理: {}", key);
            return null;
        }

        try {
            return Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("redis失败项key中的jobId格式异常，跳过清理: {}", key);
            return null;
        }
    }

    private boolean isLongTerm(LocalDateTime time) {
        return time == null || LocalDateTime.now().isAfter(time.plusMonths(EXPIRE_MONTHS));
    }
}
