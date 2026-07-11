package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;

import java.util.List;

/**
 * LLM 提出的单条角色调整候选
 */
public record RoleAdjustCandidate(
        @JsonPropertyDescription("调整类型。ADD 表示新增一条个人行为补丁；REPLACE 表示替换基线中某条已有补丁；DISABLE 表示停用基线中某条已有补丁。")
        RoleAdjustChangeType changeType,

        @JsonPropertyDescription("被调整的基线补丁临时编号。changeType 为 REPLACE 或 DISABLE 时必须填写；ADD 时返回 null。示例：1")
        Integer targetRef,

        @JsonPropertyDescription("调整适用的具体场景，必须收窄到可判断的聊天情境，避免泛化。示例：用户表达担心且与角色关系较熟时。")
        String applicability,

        @JsonPropertyDescription("该场景下角色应该呈现的行为、语气或表达倾向，必须能被原作证据支撑。")
        String expectedBehavior,

        @JsonPropertyDescription("该场景下角色应该避免的行为或表达边界；没有明确边界时返回空字符串。")
        String forbiddenBehavior,

        @JsonPropertyDescription("对用户调整意图的收窄说明，用于避免偏离原著。示例：只增强关心表达，不改变角色冷静克制的底色。")
        String intentNarrowing,

        @JsonPropertyDescription("支撑该候选的原文证据临时编号列表，编号来自本次输入中的 evidenceRef。至少引用 1 条证据。示例：[1,3]")
        List<Integer> evidenceRefs
) {
    public RoleAdjustCandidate {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
