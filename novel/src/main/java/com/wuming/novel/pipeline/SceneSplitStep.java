package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.ISceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SceneSplitStep implements PipelineStep {
    private final ISceneService sceneService;

    @Override
    public JobStage stage() {
        return JobStage.SCENE_SPLIT;
    }

    @Override
    public String name() {
        return "场景切分";
    }

    @Override
    public void execute(Long jobId) {
        sceneService.splitScene(jobId);
    }
}
