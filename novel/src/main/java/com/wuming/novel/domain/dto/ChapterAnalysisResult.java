package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 章节分析结果。
 */
public record ChapterAnalysisResult(
        @JsonPropertyDescription("200字以内的章节摘要，概括本章主要事件和信息。")
        String summary,

        @JsonPropertyDescription("本章明确出场的人物名称列表，只包含人物名，不包含身份描述，并去重。")
        List<String> mainCharacters,

        @JsonPropertyDescription("发生场景切换的新场景起始段落编号列表，编号从1开始，不包含1；无法判断时返回空数组。")
        List<Integer> sceneBoundaries
) {
    public ChapterAnalysisResult {
        mainCharacters = mainCharacters == null ? List.of() : List.copyOf(mainCharacters);
        sceneBoundaries = sceneBoundaries == null ? List.of() : List.copyOf(sceneBoundaries);
    }
}
