package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 角色原作样本抽取结果。
 */
public record RoleExampleExtractionResult(
        @JsonPropertyDescription("从原文片段中抽取到的角色样本列表。没有合适样本时返回空数组。")
        List<Example> examples
) {
    public RoleExampleExtractionResult {
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    public record Example(
            @JsonPropertyDescription("样本类型，只能是 INTERACTION 或 NARRATION_EVAL")
            String type,

            @JsonPropertyDescription("完整样本原文。INTERACTION 需要包含触发内容和角色反应，NARRATION_EVAL 需要包含完整旁白评价。")
            String sampleText,

            @JsonPropertyDescription("归因置信度，范围为0.0到1.0。无法确认归属时不要输出样本。")
            Double confidence
    ) {
    }
}
