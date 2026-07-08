package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
    private final RedisStageFailureStore redisStageFailureStore;

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
        List<Chapter> chapters = targetChapters(jobId, job);
        int successCount = 0;
        for (Chapter chapter : chapters) {
            try {
                buildOneChapter(job, chapter);
                redisStageFailureStore.recordSuccess(jobId, stage(), chapter.getId());
                successCount++;
            } catch (RuntimeException e) {
                redisStageFailureStore.recordFailure(jobId, stage(), chapter.getId());
                log.warn("章节Passage构建失败，已记录失败项，jobId: {}, novelId: {}, chapterId: {}",
                        job.getId(), job.getNovelId(), chapter.getId(), e);
            }
        }
        log.info("小说Passage构建执行完成，jobId: {}, novelId: {}, requestCount: {}, successCount: {}",
                job.getId(), job.getNovelId(), chapters.size(), successCount);
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
            log.info("跳过已完成Passage构建章节，jobId: {}, novelId: {}, completedCount: {}",
                    jobId, job.getNovelId(), completedChapterIds.size());
        }
        return chapterService.list(queryWrapper);
    }

    /**
     * 构建单章Passage并识别Passage中的角色。
     *
     * @param job 任务实体
     * @param chapter 章节实体
     */
    private void buildOneChapter(Job job, Chapter chapter) {
        List<NovelPassage> passages = novelPassageService.splitPassage(job.getId(), chapter.getId());
        passageCharacterService.recognizeAndSave(passages);
    }

    private Job requireJob(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "任务不存在: " + jobId);
        }
        return job;
    }
}
