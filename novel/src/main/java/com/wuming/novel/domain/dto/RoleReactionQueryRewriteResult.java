package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 情境检索问题改写结果。
 */
public record RoleReactionQueryRewriteResult(
        @JsonPropertyDescription("适合检索目标角色原作样本的多条查询语句，2-3条，角度各不相同。")
        List<String> queries
) {
}
