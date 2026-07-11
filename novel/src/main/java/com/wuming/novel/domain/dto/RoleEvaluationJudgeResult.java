package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM Judge 对角色评测回复给出的结构化结论。
 */
public record RoleEvaluationJudgeResult(
        @JsonPropertyDescription("角色一致性，1到5分") Integer characterConsistency,
        @JsonPropertyDescription("情境反应贴合度，1到5分") Integer situationResponse,
        @JsonPropertyDescription("原作忠实性，1到5分") Integer sourceFaithfulness,
        @JsonPropertyDescription("风格自然度，1到5分") Integer styleNaturalness,
        @JsonPropertyDescription("行为边界遵守度，1到5分") Integer boundaryCompliance,
        @JsonPropertyDescription("综合评分理由与原作证据说明") String reason
) {
    /**
     * 计算五个评分维度的平均分。
     *
     * @return 平均分；任一维度缺失时返回null
     */
    public Double totalScore() {
        if (characterConsistency == null || situationResponse == null || sourceFaithfulness == null
                || styleNaturalness == null || boundaryCompliance == null) {
            return null;
        }
        return (characterConsistency + situationResponse + sourceFaithfulness
                + styleNaturalness + boundaryCompliance) / 5.0;
    }
}
