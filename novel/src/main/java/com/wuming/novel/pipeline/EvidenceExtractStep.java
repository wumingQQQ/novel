package com.wuming.novel.pipeline;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvidenceExtractStep implements PipelineStep {
    private final IEvidenceService evidenceService;

    @Override
    public JobStage stage() {
        return JobStage.EVIDENCE_EXTRACT;
    }

    @Override
    public String name() {
        return "证据提取";
    }

    @Override
    public void execute(Long jobId) {
        evidenceService.extractEvidence(jobId);
    }
}
