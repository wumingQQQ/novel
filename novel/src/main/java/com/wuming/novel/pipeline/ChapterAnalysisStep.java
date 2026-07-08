package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.support.ChapterAnalysisService;
import com.wuming.novel.sse.JobProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 章节分析流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterAnalysisStep implements PipelineStep {
    private final IJobService jobService;
    private final IChapterService chapterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

    @Override
    public JobStage stage() {
        return JobStage.CHAPTER_ANALYSIS;
    }

    @Override
    public String name() {
        return "章节分析";
    }

    @Override
    public void execute(Long jobId) {
        Job job = requireJob(jobId);
        List<Chapter> chapters = targetChapters(jobId, job);
        jobProgressService.setStageTotalItems(jobId, stage(), chapters.size());
        int successCount = chapters.stream()
                .map(chapter -> CompletableFuture
                        .runAsync(() -> chapterAnalysisService.analyzeChapter(chapter), llmExecutor)
                        .handle((ignored, throwable) -> finishOneChapter(jobId, job, chapter, throwable)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .mapToInt(success -> success ? 1 : 0)
                .sum();
        log.info("章节分析执行完成，jobId: {}, novelId: {}, requestCount: {}, successCount: {}",
                jobId, job.getNovelId(), chapters.size(), successCount);
    }

    /**
     * 完成单章分析子任务收尾，统一记录检查点和进度。
     */
    private boolean finishOneChapter(Long jobId, Job job, Chapter chapter, Throwable throwable) {
        if (throwable == null) {
            redisStageFailureStore.recordSuccess(jobId, stage(), chapter.getId());
            jobProgressService.recordItemSuccess(jobId, stage());
            return true;
        }
        redisStageFailureStore.recordFailure(jobId, stage(), chapter.getId());
        jobProgressService.recordItemFailure(jobId, stage());
        log.warn("章节分析失败，已记录失败项，jobId: {}, novelId: {}, chapterId: {}",
                jobId, job.getNovelId(), chapter.getId(), throwable);
        return false;
    }

    /**
     * 获取本次需要分析的章节；存在失败项时只重试失败章节，否则跳过已完成章节。
     */
    private List<Chapter> targetChapters(Long jobId, Job job) {
        List<Long> failedChapterIds = redisStageFailureStore.consumeFailedLongItems(jobId, stage());
        if (!failedChapterIds.isEmpty()) {
            log.info("重试章节分析失败项，jobId: {}, novelId: {}, failedCount: {}",
                    jobId, job.getNovelId(), failedChapterIds.size());
            return chapterService.list(new LambdaQueryWrapper<Chapter>()
                    .eq(Chapter::getNovelId, job.getNovelId())
                    .in(Chapter::getId, failedChapterIds)
                    .orderByAsc(Chapter::getSequence));
        }

        List<Long> completedChapterIds = redisStageFailureStore.completedLongItems(jobId, stage());
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, job.getNovelId())
                .orderByAsc(Chapter::getSequence);
        if (!completedChapterIds.isEmpty()) {
            queryWrapper.notIn(Chapter::getId, completedChapterIds);
            log.info("跳过已完成章节分析项，jobId: {}, novelId: {}, completedCount: {}",
                    jobId, job.getNovelId(), completedChapterIds.size());
        }
        return chapterService.list(queryWrapper);
    }

    private Job requireJob(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        return job;
    }
}
