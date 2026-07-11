package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 生成的角色调整候选集合。
 */
public record RoleAdjustCandidateResult(
        @JsonPropertyDescription("本次建议给用户评审的候选调整项列表。没有足够证据时返回空数组。")
        List<RoleAdjustCandidate> candidates
) {
    public RoleAdjustCandidateResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
