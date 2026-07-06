package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM Passage 出场人物识别结果。
 */
public record PassageCharacterResult(
        @JsonPropertyDescription("片段中明确出场或被直接对话提及的人物名称列表，只包含人物名，不包含身份描述，并去重。")
        List<String> characters
) {
    public PassageCharacterResult {
        characters = characters == null ? List.of() : List.copyOf(characters);
    }
}
