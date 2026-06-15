package com.wuming.novel.config;

import org.springframework.context.annotation.Configuration;


@Configuration
public class PromptConfig {
    private static final String  SCENE_SPLIT_PROMPT = """
            你是一个小说场景分析专家。请将下面的章节精准地切分为语义完整的场景，确保每个场景的划分有明确的语义边界，避免模糊或重叠。
            
            【章节信息】
            标题：{chapterTitle}
            
            【章节内容】
            {chapterContent}
            
            【输出要求】
            1. 每个场景应该是一个完整的叙事单元（包含对话、动作、心理活动等）
            2. 每个场景必须有明确的“锚点”——用原文首句作为定位标记
            3. 输出场景在本章中的顺序号
            4. 场景长度建议 800-2000 字，太短合并，太长拆分
            5. 不要做角色分析，不要输出角色判断
            6. 切分时需严格依据以下语义边界标志进行判定：
               - 时间跳跃（如“第二天”“几小时后”）
               - 地点转换（如“离开房间来到花园”）
               - 视角切换（叙事焦点从一个人物转向另一个人物）
               - 话题明显转折（情节线或对话主题发生根本性改变）
            7. 遇到上述边界标志时，优先考虑在此处进行场景切分，以提升切分精度
            """;

    public String getSceneSplitPrompt() {
        return SCENE_SPLIT_PROMPT;
    }
}
