package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.support.ChapterAnalysisService;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 章节分析流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterAnalysisStep implements PipelineStep {
    private final PipelineJobSupport pipelineJobSupport;
    private final StageItemSelector stageItemSelector;
    private final StageItemRecorder stageItemRecorder;
    private final AsyncStageItemRunner asyncStageItemRunner;
    private final IChapterService chapterService;
    private final ChapterAnalysisService chapterAnalysisService;
    private final NovelPreprocessCoordinator preprocessCoordinator;
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
        Job job = pipelineJobSupport.requireJob(jobId);
        preprocessCoordinator.execute(job.getNovelId(), NovelPreprocessStage.CHAPTER_ANALYSIS,
                () -> analyzeChapters(jobId, job));
    }

    /** 仅由取得小说预处理锁的 job 运行章节分析子任务。 */
    private void analyzeChapters(Long jobId, Job job) {
        List<Chapter> chapters = targetChapters(jobId, job);
        stageItemRecorder.initLongItemCounts(jobId, stage(), chapters.size());
        int successCount = asyncStageItemRunner.run(
                        chapters,
                        llmExecutor,
                        chapterAnalysisService::analyzeChapter,
                        (chapter, throwable) -> finishOneChapter(jobId, job, chapter, throwable)
                )
                .stream()
                .mapToInt(success -> success ? 1 : 0)
                .sum();
        log.info("章节分析执行完成，jobId: {}, novelId: {}, requestCount: {}, successCount: {}",
                jobId, job.getNovelId(), chapters.size(), successCount);
        if (successCount != chapters.size()) {
            throw new IllegalStateException("章节分析存在失败项，successCount: " + successCount
                    + ", requestCount: " + chapters.size());
        }
    }

    /**
     * 完成单章分析子任务收尾，统一记录检查点和进度。
     */
    private boolean finishOneChapter(Long jobId, Job job, Chapter chapter, Throwable throwable) {
        if (throwable == null) {
            stageItemRecorder.recordLongSuccess(jobId, stage(), chapter.getId());
            return true;
        }
        stageItemRecorder.recordLongFailure(jobId, stage(), chapter.getId());
        Throwable cause = pipelineJobSupport.rootCause(throwable);
        log.warn("章节分析失败，已记录失败项，jobId: {}, novelId: {}, chapterId: {}, errorType: {}, errorMessage: {}",
                jobId, job.getNovelId(), chapter.getId(),
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("章节分析失败堆栈，jobId: {}, novelId: {}, chapterId: {}",
                jobId, job.getNovelId(), chapter.getId(), throwable);
        return false;
    }

    /**
     * 获取本次需要分析的章节；存在失败项时只重试失败章节，否则跳过已完成章节。
     */
    private List<Chapter> targetChapters(Long jobId, Job job) {
        return stageItemSelector.selectLongBackedItems(
                jobId, stage(),
                failedChapterIds -> chapterService.list(chapterQuery(job).in(Chapter::getId, failedChapterIds)),
                () -> chapterService.list(chapterQuery(job)),
                Chapter::getId
        );
    }

    private LambdaQueryWrapper<Chapter> chapterQuery(Job job) {
        return new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, job.getNovelId())
                .orderByAsc(Chapter::getSequence);
    }
}
