package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import com.wuming.novel.sse.JobProgressService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Passage构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassageBuildStep implements PipelineStep {
    private final IJobService jobService;
    private final IChapterService chapterService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;
    private final NovelPreprocessCoordinator preprocessCoordinator;
    private final RedisStageFailureStore redisStageFailureStore;
    private final JobProgressService jobProgressService;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

    @Override
    public JobStage stage() {
        return JobStage.PASSAGE_BUILD;
    }

    @Override
    public String name() {
        return "Passage构建";
    }

    @Override
    public void execute(Long jobId) {
        Job job = requireJob(jobId);
        preprocessCoordinator.execute(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD,
                () -> buildPassages(jobId, job));
    }

    /** 仅由取得小说预处理锁的 job 构建并索引 Passage。 */
    private void buildPassages(Long jobId, Job job) {
        List<Chapter> chapters = targetChapters(jobId, job);
        int completedCount = redisStageFailureStore.completedLongItems(jobId, stage()).size();
        jobProgressService.setStageItemCounts(jobId, stage(), completedCount + chapters.size(), completedCount, 0);

        // Passage替换会同时改写多个二级索引，按章节串行执行以避免同一小说内的事务死锁。
        List<ChapterPassages> persistedChapters = persistPassages(jobId, job, chapters);
        int recognitionSuccessCount = persistedChapters.stream()
                .map(chapterPassages -> CompletableFuture
                        .runAsync(() -> recognizeCharacters(chapterPassages.passages()), llmExecutor)
                        .handle((ignored, throwable) -> finishOneChapter(
                                jobId, job, chapterPassages.chapter(), throwable)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .mapToInt(success -> success ? 1 : 0)
                .sum();
        int successCount = recognitionSuccessCount;
        log.info("小说Passage构建执行完成，jobId: {}, novelId: {}, requestCount: {}, successCount: {}",
                job.getId(), job.getNovelId(), chapters.size(), successCount);
        if (successCount != chapters.size()) {
            throw new IllegalStateException("Passage构建存在失败项，successCount: " + successCount
                    + ", requestCount: " + chapters.size());
        }
    }

    /**
     * 逐章写入 Passage，避免并发删除和批量插入竞争同一小说的二级索引锁。
     */
    private List<ChapterPassages> persistPassages(Long jobId, Job job, List<Chapter> chapters) {
        List<ChapterPassages> persistedChapters = new ArrayList<>(chapters.size());
        for (Chapter chapter : chapters) {
            try {
                persistedChapters.add(new ChapterPassages(chapter,
                        novelPassageService.splitPassage(job.getId(), chapter.getId())));
            } catch (RuntimeException e) {
                finishOneChapter(jobId, job, chapter, e);
            }
        }
        return persistedChapters;
    }

    /**
     * Passage全部落库后再进行人物识别，使外部 LLM 调用仍可并发，而数据库写入保持串行。
     */
    private void recognizeCharacters(List<NovelPassage> passages) {
        passageCharacterService.recognizeAndSave(passages);
    }

    /**
     * 完成单章Passage构建子任务收尾，统一记录检查点和进度。
     */
    private boolean finishOneChapter(Long jobId, Job job, Chapter chapter, Throwable throwable) {
        if (throwable == null) {
            redisStageFailureStore.recordSuccess(jobId, stage(), chapter.getId());
            jobProgressService.recordItemSuccess(jobId, stage());
            return true;
        }
        redisStageFailureStore.recordFailure(jobId, stage(), chapter.getId());
        jobProgressService.recordItemFailure(jobId, stage());
        Throwable cause = logCause(throwable);
        log.warn("章节Passage构建失败，已记录失败项，jobId: {}, novelId: {}, chapterId: {}, errorType: {}, errorMessage: {}",
                job.getId(), job.getNovelId(), chapter.getId(),
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("章节Passage构建失败堆栈，jobId: {}, novelId: {}, chapterId: {}",
                job.getId(), job.getNovelId(), chapter.getId(), throwable);
        return false;
    }

    private Throwable logCause(Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }

    /**
     * 获取本次需要处理的章节；存在失败项时只重试失败章节，否则跳过已完成章节。
     *
     * @param jobId 任务id
     * @param job 任务实体
     * @return 本次需要构建Passage的章节列表
     */
    private List<Chapter> targetChapters(Long jobId, Job job) {
        List<Long> failedChapterIds = redisStageFailureStore.consumeFailedLongItems(jobId, stage());
        if (!failedChapterIds.isEmpty()) {
            log.info("重试Passage构建失败章节，jobId: {}, novelId: {}, failedCount: {}",
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
            // 如果已完成部分不为空，说明已经处理过且存在失败
            queryWrapper.notIn(Chapter::getId, completedChapterIds);
            log.debug("跳过已完成Passage构建章节，jobId: {}, novelId: {}, completedCount: {}",
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

    private record ChapterPassages(Chapter chapter, List<NovelPassage> passages) {
    }
}
