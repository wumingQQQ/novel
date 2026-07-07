package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 角色情境反应规则归纳结果。
 */
public record RoleReactionRuleBuildResult(
        @JsonPropertyDescription("基于原作样本归纳出的角色反应规则。证据不足时固定返回“证据不足”。")
        String rule,

        @JsonPropertyDescription("规则置信度，范围为0.0到1.0。证据不足时返回0。")
        Double confidence
) {
}
