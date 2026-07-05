package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.impl.RoleRuntimePhaseOneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChapterSplitStep implements PipelineStep {
    private final RoleRuntimePhaseOneService roleRuntimePhaseOneService;

    @Override
    public JobStage stage() {
        return JobStage.CHAPTER_SPLIT;
    }

    @Override
    public String name() {
        return "Passage构建与向量化";
    }

    @Override
    public void execute(Long jobId) {
        roleRuntimePhaseOneService.build(jobId);
    }
}
