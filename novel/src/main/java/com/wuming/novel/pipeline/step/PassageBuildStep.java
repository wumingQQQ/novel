package com.wuming.novel.pipeline.step;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.pipeline.PipelineStep;
import com.wuming.novel.pipeline.support.AsyncStageItemRunner;
import com.wuming.novel.pipeline.support.PipelineJobSupport;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import com.wuming.novel.service.support.NovelPreprocessCheckpointStore;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import com.wuming.novel.service.support.NovelPreprocessProgress;
import com.wuming.novel.sse.JobProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Passage构建流程阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassageBuildStep implements PipelineStep {
    private final PipelineJobSupport pipelineJobSupport;
    private final AsyncStageItemRunner asyncStageItemRunner;
    private final IChapterService chapterService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;
    private final NovelPreprocessCoordinator preprocessCoordinator;
    private final NovelPreprocessCheckpointStore checkpointStore;
    private final JobProgressService jobProgressService;
    private final Executor llmExecutor;

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
        Job job = pipelineJobSupport.requireJob(jobId);
        List<Chapter> chapters = chapterService.list(chapterQuery(job));
        preprocessCoordinator.execute(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD,
                () -> buildPassages(jobId, job, chapters),
                progress -> mirrorProgress(jobId, chapters.size(), progress));
    }

    /** 仅由取得小说预处理锁的 job 构建并索引 Passage。 */
    private void buildPassages(Long jobId, Job job, List<Chapter> chapters) {
        List<Chapter> targetChapters = targetChapters(job, chapters);
        mirrorProgress(jobId, chapters.size(), checkpointStore.progress(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD));

        // Passage替换会同时改写多个二级索引，按章节串行执行以避免同一小说内的事务死锁。
        List<ChapterPassages> persistedChapters = persistPassages(jobId, job, chapters.size(), targetChapters);
        int successCount = persistedChapters.size();
        asyncStageItemRunner.run(
                persistedChapters,
                llmExecutor,
                chapterPassages -> recognizeCharacters(chapterPassages.passages()),
                (chapterPassages, throwable) -> finishCharacterRecognition(job, chapterPassages.chapter(), throwable)
        );
        log.info("小说Passage构建执行完成，jobId: {}, novelId: {}, requestCount: {}, successCount: {}",
                job.getId(), job.getNovelId(), targetChapters.size(), successCount);
        if (successCount != targetChapters.size()) {
            throw new IllegalStateException("Passage构建存在失败项，successCount: " + successCount
                    + ", requestCount: " + targetChapters.size());
        }
    }

    /**
     * 逐章写入 Passage，避免并发删除和批量插入竞争同一小说的二级索引锁。
     */
    private List<ChapterPassages> persistPassages(Long jobId, Job job, int totalChapterCount,
                                                  List<Chapter> chapters) {
        List<ChapterPassages> persistedChapters = new ArrayList<>(chapters.size());
        for (Chapter chapter : chapters) {
            try {
                List<NovelPassage> passages = novelPassageService.splitPassage(job.getId(), chapter.getId());
                finishOneChapter(jobId, job, totalChapterCount, chapter, null);
                persistedChapters.add(new ChapterPassages(chapter, passages));
            } catch (RuntimeException e) {
                finishOneChapter(jobId, job, totalChapterCount, chapter, e);
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
     * 人物识别只是 Passage 构建后的补充标签同步，不参与本阶段进度计数。
     */
    private boolean finishCharacterRecognition(Job job, Chapter chapter, Throwable throwable) {
        if (throwable == null) {
            return true;
        }
        Throwable cause = pipelineJobSupport.rootCause(throwable);
        log.warn("章节Passage人物识别失败，已忽略进度影响，jobId: {}, novelId: {}, chapterId: {}, errorType: {}, errorMessage: {}",
                job.getId(), job.getNovelId(), chapter.getId(),
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("章节Passage人物识别失败堆栈，jobId: {}, novelId: {}, chapterId: {}",
                job.getId(), job.getNovelId(), chapter.getId(), throwable);
        return false;
    }

    /**
     * 完成单章Passage切分和向量索引收尾，统一记录检查点和进度。
     */
    private boolean finishOneChapter(Long jobId, Job job, int totalChapterCount, Chapter chapter, Throwable throwable) {
        if (throwable == null) {
            checkpointStore.recordSuccess(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD, chapter.getId());
            mirrorProgress(jobId, totalChapterCount,
                    checkpointStore.progress(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD));
            return true;
        }
        checkpointStore.recordFailure(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD, chapter.getId());
        mirrorProgress(jobId, totalChapterCount,
                checkpointStore.progress(job.getNovelId(), NovelPreprocessStage.PASSAGE_BUILD));
        Throwable cause = pipelineJobSupport.rootCause(throwable);
        log.warn("章节Passage构建失败，已记录失败项，jobId: {}, novelId: {}, chapterId: {}, errorType: {}, errorMessage: {}",
                job.getId(), job.getNovelId(), chapter.getId(),
                cause.getClass().getSimpleName(), cause.getMessage());
        log.debug("章节Passage构建失败堆栈，jobId: {}, novelId: {}, chapterId: {}",
                job.getId(), job.getNovelId(), chapter.getId(), throwable);
        return false;
    }

    /**
     * 获取本次需要处理的章节；存在失败项时只重试失败章节，否则跳过已完成章节。
     *
     * @param job 任务实体
     * @return 本次需要构建Passage的章节列表
     */
    private List<Chapter> targetChapters(Job job, List<Chapter> chapters) {
        Set<Long> selectedChapterIds = Set.copyOf(checkpointStore.selectItems(
                job.getNovelId(),
                NovelPreprocessStage.PASSAGE_BUILD,
                chapters.stream().map(Chapter::getId).toList()
        ));
        return chapters.stream()
                .filter(chapter -> selectedChapterIds.contains(chapter.getId()))
                .toList();
    }

    private void mirrorProgress(Long jobId, int totalChapterCount, NovelPreprocessProgress progress) {
        jobProgressService.setStageItemCounts(
                jobId,
                stage(),
                totalChapterCount,
                Math.toIntExact(progress.successCount()),
                Math.toIntExact(progress.failureCount())
        );
    }

    private LambdaQueryWrapper<Chapter> chapterQuery(Job job) {
        return new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, job.getNovelId())
                .orderByAsc(Chapter::getSequence);
    }

    private record ChapterPassages(Chapter chapter, List<NovelPassage> passages) {
    }
}
