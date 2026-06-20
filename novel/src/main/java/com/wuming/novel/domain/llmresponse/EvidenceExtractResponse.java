package com.wuming.novel.domain.llmresponse;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record EvidenceExtractResponse(
        @JsonPropertyDescription("画像结论")
        String conclusion,
        @JsonPropertyDescription("支撑证据，原文引用")
        List<String> supportingQuotes,
        @JsonPropertyDescription("引用对应场景id")
        List<Long> sceneIds,
        @JsonPropertyDescription("置信度")
        Double confidence
) {
}
