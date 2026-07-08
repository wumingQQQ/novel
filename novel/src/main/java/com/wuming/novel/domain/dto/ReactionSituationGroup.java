package com.wuming.novel.domain.dto;

import java.util.List;

/**
 * 反应规则情境配置分组。
 */
public record ReactionSituationGroup(
        String category,
        List<ReactionSituation> situations
) {
    public ReactionSituationGroup {
        situations = situations == null ? List.of() : List.copyOf(situations);
    }
}
