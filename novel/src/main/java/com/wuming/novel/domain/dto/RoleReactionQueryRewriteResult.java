package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 情境检索问题改写结果。
 */
public record RoleReactionQueryRewriteResult(
        @JsonPropertyDescription("适合检索目标角色原作样本的单条查询语句。")
        String query
) {
}
