package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChapterSplitStep implements PipelineStep {
    private final IChapterService chapterService;

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
        chapterService.splitChapter(jobId);
    }
}
