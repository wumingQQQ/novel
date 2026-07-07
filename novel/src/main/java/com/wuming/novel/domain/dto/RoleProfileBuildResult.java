package com.wuming.novel.domain.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM 角色轻量画像构建结果。
 */
public record RoleProfileBuildResult(
        @JsonPropertyDescription("角色基础信息。只填写原作样本能明确支撑的信息，没有证据的字段返回空字符串。")
        BasicInfo basicInfo,

        @JsonPropertyDescription("3到5个核心性格特质，含能力设定，控制在80字以内。")
        String coreTraits,

        @JsonPropertyDescription("角色说话风格。")
        SpeakingStyle speakingStyle,

        @JsonPropertyDescription("角色绝不应做的行为边界，3到5条，每条独立成句。")
        List<String> forbiddenBehaviors,

        @JsonPropertyDescription("画像可信度，范围为0.0到1.0。样本不足时返回0。")
        Double confidence
) {
    public RoleProfileBuildResult {
        forbiddenBehaviors = forbiddenBehaviors == null ? List.of() : List.copyOf(forbiddenBehaviors);
    }

    public record BasicInfo(
            @JsonPropertyDescription("年龄或年龄段，没有明确证据时返回空字符串。")
            String age,

            @JsonPropertyDescription("性别，没有明确证据时返回空字符串。")
            String gender,

            @JsonPropertyDescription("职业、身份或社会角色，没有明确证据时返回空字符串。")
            String occupation,

            @JsonPropertyDescription("外貌或关键形象描述，没有明确证据时返回空字符串。")
            String appearance
    ) {
    }

    public record SpeakingStyle(
            @JsonPropertyDescription("一句话概括角色最稳定的说话风格。")
            String signature,

            @JsonPropertyDescription("2到3个有辨识度的具体句式、语气或表达模式，最好带短原文例子。")
            List<String> distinctivePatterns,

            @JsonPropertyDescription("2到3个角色明确不会使用的表达方式。")
            List<String> avoidPatterns
    ) {
        public SpeakingStyle {
            distinctivePatterns = distinctivePatterns == null ? List.of() : List.copyOf(distinctivePatterns);
            avoidPatterns = avoidPatterns == null ? List.of() : List.copyOf(avoidPatterns);
        }
    }
}
