package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.ILayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LayerSplitStep implements PipelineStep {
    private final ILayerService layerService;

    @Override
    public JobStage stage() {
        return JobStage.LAYER_SPLIT;
    }

    @Override
    public String name() {
        return "剧情分层";
    }

    @Override
    public void execute(Long jobId) {
        layerService.splitLayer(jobId);
    }
}
