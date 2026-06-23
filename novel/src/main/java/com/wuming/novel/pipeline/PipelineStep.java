package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;

public interface PipelineStep {
    JobStage stage();

    String name();

    void execute(Long jobId);
}
