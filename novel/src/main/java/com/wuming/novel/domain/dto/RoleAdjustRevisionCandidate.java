package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;

/**
 * LLM 对单个待改写角色调整候选项给出的修订结果。
 */
public record RoleAdjustRevisionCandidate(
        @JsonPropertyDescription("修订后的调整类型。通常沿用原候选项类型，除非用户反馈明确要求改变调整方向。")
        RoleAdjustChangeType changeType,

        @JsonPropertyDescription("修订后的目标补丁稳定标识。ADD 返回 null；REPLACE 或 DISABLE 通常沿用原候选项的目标补丁标识。")
        String targetAdjustmentId,

        @JsonPropertyDescription("修订后的适用场景，需要比原候选更清晰、可判断。")
        String applicability,

        @JsonPropertyDescription("修订后的期望行为，需要回应用户改写反馈并保持原作证据支撑。")
        String expectedBehavior,

        @JsonPropertyDescription("修订后的禁止行为边界；没有明确边界时返回空字符串。")
        String forbiddenBehavior
) {
}
