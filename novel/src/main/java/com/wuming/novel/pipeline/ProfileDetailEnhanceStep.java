package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.support.ProfileDetailEnhanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileDetailEnhanceStep implements PipelineStep {
    private final ProfileDetailEnhanceService profileDetailEnhanceService;

    @Override
    public JobStage stage() {
        return JobStage.PROFILE_DETAIL_ENHANCE;
    }

    @Override
    public String name() {
        return "画像细节增强";
    }

    @Override
    public void execute(Long jobId) {
        profileDetailEnhanceService.enhance(jobId);
    }
}
