package com.wuming.novel.pipeline;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChapterSplitStep implements PipelineStep {
    private final IChapterService chapterService;
    private final IJobService jobService;
    private final NovelPreprocessCoordinator preprocessCoordinator;

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
            throw new IllegalArgumentException("任务不存在: " + jobId);
        }
        preprocessCoordinator.execute(job.getNovelId(), NovelPreprocessStage.CHAPTER_SPLIT,
                () -> chapterService.splitChapter(jobId));
    }
}
