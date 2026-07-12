package com.wuming.chat.service;

import com.wuming.api.role.dto.RoleRuntimeContextDto;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class RoleChatPromptBuilder {
    @Value("classpath:prompts/role-chat-system-prompt.st")
    private Resource systemPromptResource;

    /**
     * 将角色运行时上下文转换为聊天使用的系统提示词。
     *
     * @param context 角色运行时上下文
     * @return 系统提示词
     */
    public String buildSystemPrompt(RoleRuntimeContextDto context) {
        RoleRuntimeContextDto.BasicInfo basicInfo = context.getBasicInfo();
        RoleRuntimeContextDto.SpeakingStyle speakingStyle = context.getSpeakingStyle();
        PromptTemplate promptTemplate = new PromptTemplate(systemPromptResource);
        return promptTemplate.render(Map.ofEntries(
                Map.entry("novelName", value(context.getNovelName())),
                Map.entry("characterName", value(context.getCharacterName())),
                Map.entry("age", basicInfo == null ? "未知" : value(basicInfo.getAge())),
                Map.entry("gender", basicInfo == null ? "未知" : value(basicInfo.getGender())),
                Map.entry("occupation", basicInfo == null ? "未知" : value(basicInfo.getOccupation())),
                Map.entry("appearance", basicInfo == null ? "未知" : value(basicInfo.getAppearance())),
                Map.entry("coreTraits", value(context.getCoreTraits())),
                Map.entry("speakingStyleSignature", speakingStyle == null ? "未知" : value(speakingStyle.getSignature())),
                Map.entry("distinctivePatterns", speakingStyle == null ? "无" : list(speakingStyle.getDistinctivePatterns())),
                Map.entry("avoidPatterns", speakingStyle == null ? "无" : list(speakingStyle.getAvoidPatterns())),
                Map.entry("forbiddenBehaviors", value(context.getForbiddenBehaviors())),
                Map.entry("behaviorAdjustments", behaviorAdjustments(context.getBehaviorAdjustments()))
        ));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String list(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "无";
        }
        StringJoiner joiner = new StringJoiner("；");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                joiner.add(value);
            }
        }
        return joiner.length() == 0 ? "无" : joiner.toString();
    }

    /**
     * 将用户个人版本中的行为补丁压缩为提示词片段。
     */
    private String behaviorAdjustments(List<RoleRuntimeContextDto.BehaviorAdjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) {
            return "无";
        }
        StringJoiner joiner = new StringJoiner("\n");
        adjustments.stream()
                .filter(adjustment -> adjustment != null)
                .sorted(Comparator.comparing(
                        RoleRuntimeContextDto.BehaviorAdjustment::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(adjustment -> joiner.add(formatAdjustment(adjustment)));
        return joiner.length() == 0 ? "无" : joiner.toString();
    }

    private String formatAdjustment(RoleRuntimeContextDto.BehaviorAdjustment adjustment) {
        StringBuilder builder = new StringBuilder();
        builder.append("- 适用场景：").append(value(adjustment.getApplicability()));
        builder.append("；应当表现：").append(value(adjustment.getExpectedBehavior()));
        if (adjustment.getForbiddenBehavior() != null && !adjustment.getForbiddenBehavior().isBlank()) {
            builder.append("；避免表现：").append(adjustment.getForbiddenBehavior());
        }
        return builder.toString();
    }
}
