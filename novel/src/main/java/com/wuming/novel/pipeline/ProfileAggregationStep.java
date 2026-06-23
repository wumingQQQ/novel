package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.serviceImpl.AggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileAggregationStep implements PipelineStep {
    private final AggregationService aggregationService;

    @Override
    public JobStage stage() {
        return JobStage.PROFILE_AGGREGATION;
    }

    @Override
    public String name() {
        return "画像聚合";
    }

    @Override
    public void execute(Long jobId) {
        aggregationService.aggregation(jobId);
    }
}
