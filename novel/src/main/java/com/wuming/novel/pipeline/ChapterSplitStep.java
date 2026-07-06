package com.wuming.novel.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.chaptersplit.ChapterSplitCompletedEvent;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterSplitStep implements PipelineStep {
    private final IJobService jobService;
    private final IChapterService chapterService;
    private final EventPublisher<ChapterSplitCompletedEvent> eventPublisher;

    @Override
    public JobStage stage() {
        return JobStage.CHAPTER_SPLIT;
    }

    @Override
    public String name() {
        return "章节切分";
    }

    @Override
    public void execute(Long jobId) {
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "该job不存在，请创建后重试");
        }
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(job.getNovelId())) {
            chapterService.splitChapter(jobId);
            long chapterCount = chapterService.count(new LambdaQueryWrapper<Chapter>()
                    .eq(Chapter::getNovelId, job.getNovelId()));
            publishChapterSplitCompletedEvent(job, chapterCount);
        }
    }

    private void publishChapterSplitCompletedEvent(Job job, long chapterCount) {
        ChapterSplitCompletedEvent event = new ChapterSplitCompletedEvent();
        event.setJobId(job.getId());
        event.setNovelId(job.getNovelId());
        event.setChapterCount(chapterCount);
        try {
            eventPublisher.publish(event);
        } catch (RuntimeException e) {
            log.warn("章节切分完成事件发布失败，jobId: {}, novelId: {}",
                    job.getId(), job.getNovelId(), e);
        }
    }
}
