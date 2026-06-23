package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IScenePoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenePoolClassifyStep implements PipelineStep {
    private final IScenePoolService scenePoolService;

    @Override
    public JobStage stage() {
        return JobStage.POOL_CLASSIFY;
    }

    @Override
    public String name() {
        return "场景分池";
    }

    @Override
    public void execute(Long jobId) {
        scenePoolService.divideSceneIntoPool(jobId);
    }
}
