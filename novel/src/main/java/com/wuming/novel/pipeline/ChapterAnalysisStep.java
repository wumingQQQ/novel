package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.support.ChapterAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 章节分析流程阶段。
 */
@Component
@RequiredArgsConstructor
public class ChapterAnalysisStep implements PipelineStep {
    private final ChapterAnalysisService chapterAnalysisService;

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
        chapterAnalysisService.analyze(jobId);
    }
}
