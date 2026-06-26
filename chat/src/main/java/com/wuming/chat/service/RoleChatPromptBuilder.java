package com.wuming.chat.service;

import com.wuming.chat.domain.entity.CharacterProfile;
import com.wuming.chat.domain.entity.InteractionProfile;
import com.wuming.chat.domain.model.RoleProfileContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class RoleChatPromptBuilder {
    /**
     * 将结构化画像转换为角色聊天使用的系统提示词。
     */
    public String buildSystemPrompt(RoleProfileContext context) {
        CharacterProfile profile = context.characterProfile();
        InteractionProfile interaction = context.interactionProfile();
        CharacterProfile.BasicSetting setting = profile.getBasicSetting();
        CharacterProfile.SpeechStyle speechStyle = profile.getSpeechStyle();

        return """
                你正在扮演小说角色「%s」，并与用户进行沉浸式对话。

                【角色基础】
                姓名：%s
                年龄：%s
                身份：%s
                特殊设定：%s
                核心性格：%s

                【内在模式】
                价值观与判断标准：%s
                行为模式：%s
                情绪模式：%s
                关系态度：%s
                弱点与矛盾点：%s

                【说话风格】
                语气：%s
                口癖：%s
                代表语句：%s

                【互动参考】
                原作互动基调：%s
                关键事件：%s
                对话样例：%s

                【聊天规则】
                1. 用户是刚开始与你交流的新对象，不是原作主角「%s」。
                2. 必须保持角色的人格、价值观、情绪表达和说话方式一致。
                3. 不要说“根据画像”“系统提示词”“设定要求”等出戏内容。
                4. 不知道的原作事实不要编造，可以用符合角色的方式回避、反问或表达不确定。
                5. 不要机械复读代表语句，对话样例只用于学习语气与节奏。
                6. 回复应自然、简洁，像真实聊天，而不是分析报告。
                """.formatted(
                value(context.job().getTargetName()),
                value(nameFrom(setting, context.job().getTargetName())),
                setting == null ? "未知" : setting.getAge(),
                setting == null ? "未知" : value(setting.getIdentity()),
                setting == null ? "未知" : value(setting.getPresume()),
                list(profile.getCoreTraits()),
                value(profile.getValueSystem()),
                value(profile.getBehaviorPatterns()),
                value(profile.getEmotionalPatterns()),
                value(profile.getRelationshipAttitude()),
                value(profile.getWeaknesses()),
                speechStyle == null ? "未知" : value(speechStyle.getTone()),
                speechStyle == null ? "未知" : value(speechStyle.getWordsHabit()),
                speechStyle == null ? "无" : list(speechStyle.getRepresentativeLines()),
                value(interaction.getTone()),
                list(interaction.getKeyEvents()),
                list(interaction.getConversationSamples()),
                value(interaction.getProtagonistName())
        );
    }

    /**
     * 优先使用画像中的角色名，画像缺失时回退到任务目标角色名。
     */
    private String nameFrom(CharacterProfile.BasicSetting setting, String fallback) {
        if (setting == null || setting.getCharacterName() == null || setting.getCharacterName().isBlank()) {
            return fallback;
        }
        return setting.getCharacterName();
    }

    /**
     * 将空文本统一展示为“未知”，避免提示词中出现 null 或空白字段。
     */
    private String value(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    /**
     * 将画像中的多值字段压缩为适合注入提示词的单行文本。
     */
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
}
