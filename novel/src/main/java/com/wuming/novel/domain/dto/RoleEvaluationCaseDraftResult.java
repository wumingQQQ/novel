package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 基于原作Passage构造的评测案例草稿。
 */
public record RoleEvaluationCaseDraftResult(
        @JsonPropertyDescription("用户向角色发出的自然测试输入，不得复述原作答案") String testInput,
        @JsonPropertyDescription("该情境下角色应体现的行为、语气或边界") String expectedBehaviors,
        @JsonPropertyDescription("Judge评分时应关注的要点") String scoringRubric,
        @JsonPropertyDescription("EASY、MEDIUM或HARD") String difficulty
) {
}
