package com.wuming.chat.rag.role;

import com.wuming.api.rag.dto.SearchHit;

import java.util.List;

/**
 * 本轮角色回复召回到的结构化参考材料。
 *
 * @param reactionRules 与当前输入相关的角色反应规则
 * @param roleExamples 与当前输入相关的原作角色样本
 */
public record RoleRetrievalSnapshot(
        List<SearchHit> reactionRules,
        List<SearchHit> roleExamples
) {
    public RoleRetrievalSnapshot {
        reactionRules = reactionRules == null
                ? List.of()
                : List.copyOf(reactionRules);
        roleExamples = roleExamples == null
                ? List.of()
                : List.copyOf(roleExamples);
    }
}
