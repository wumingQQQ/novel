package com.wuming.novel.pipeline.step;

import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.pipeline.PipelineStep;
import com.wuming.novel.pipeline.support.PipelineJobSupport;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChapterSplitStep implements PipelineStep {
    private final PipelineJobSupport pipelineJobSupport;
    private final IChapterService chapterService;
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
        Job job = pipelineJobSupport.requireJob(jobId);
        preprocessCoordinator.execute(job.getNovelId(), NovelPreprocessStage.CHAPTER_SPLIT,
                () -> chapterService.splitChapter(jobId));
    }
}
