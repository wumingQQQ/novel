package com.wuming.novel.domain.dto;

import java.util.List;

/**
 * 单个情境配置，包含情境名称和场景触发模式。
 */
public record ReactionSituation(
        String situation,
        List<String> scenePatterns
) {
    public ReactionSituation {
        scenePatterns = scenePatterns == null ? List.of() : List.copyOf(scenePatterns);
    }
}
