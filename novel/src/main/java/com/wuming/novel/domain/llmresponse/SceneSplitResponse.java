package com.wuming.novel.domain.llmresponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SceneSplitResponse(
        @JsonPropertyDescription("场景在本章中的序号，从1开始")
        int sequence,
        @JsonPropertyDescription("锚点--原文某场景的首句，精确匹配原文，不加任何额外标记")
        String anchor
) {
}
