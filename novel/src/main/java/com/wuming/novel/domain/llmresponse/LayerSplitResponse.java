package com.wuming.novel.domain.llmresponse;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record LayerSplitResponse(
        @JsonPropertyDescription("层序号")
        int layerIndex,
        @JsonPropertyDescription("层名")
        String layerName,
        @JsonPropertyDescription("该层起始章节")
        int startChapter,
        @JsonPropertyDescription("该层结束章节")
        int endChapter
) {
}
