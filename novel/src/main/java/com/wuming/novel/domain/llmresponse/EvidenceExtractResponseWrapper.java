package com.wuming.novel.domain.llmresponse;

import java.util.List;

public record EvidenceExtractResponseWrapper(
        List<EvidenceExtractResponse> evidences
) {
}
