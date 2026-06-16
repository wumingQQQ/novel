package com.wuming.novel.domain.llmresponse;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ScenePoolResponse(
        @JsonPropertyDescription("池编码")
        String code,
        @JsonPropertyDescription("置信度")
        double confidence
) {
}
