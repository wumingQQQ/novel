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
            3. 输出场景在本章中的顺序号, 从1开始
            4. 场景长度建议 800-2000 字，太短合并，太长拆分
            5. 不要做角色分析，不要输出角色判断
            6. 切分时需严格依据以下语义边界标志进行判定：
               - 时间跳跃（如“第二天”“几小时后”）
               - 地点转换（如“离开房间来到花园”）
               - 视角切换（叙事焦点从一个人物转向另一个人物）
               - 话题明显转折（情节线或对话主题发生根本性改变）
            7. 遇到上述边界标志时，优先考虑在此处进行场景切分，以提升切分精度
            
            【输出格式】JSON数组
            [
            {
            “sequence”: 1,
            “anchor”: “【锚点】原文首句，精确匹配（只输出原文句子，不含‘【锚点】’等标记，示例：“张明推开门，走进了昏暗的房间。”）”,
            }
            ]
            """;

    private static final String LAYER_SPLIT_PROMPT = """
            你是一个小说结构分析专家。请根据你对小说的理解与章节信息，以自然流畅的方式将整部小说划分为多个叙事阶段，在保持叙事连续性的前提下进行分层。
            
            【目标小说】：{novelName}
      
            【章节信息】
            总章节数：{totalChapters}
      
            章节列表：
            {chapterList}
      
            【分层约束】
            1. 每层应包含 {minChaptersPerLayer} 至 {maxChaptersPerLayer} 章，若叙事完整性要求超出此范围，仅在迫不得已时方可触及边界，并需在 boundaryReason 中特别说明。
            2. 最终生成的总层数必须介于 {minLayers} 到 {maxLayers} 之间。
            3. 当上述两条约束冲突时，优先满足每层章节数约束。
      
            【层边界判定依据】
            请优先选择叙事节奏自然转换的节点作为层边界，确保层与层之间的过渡顺滑，不割裂故事的连贯性：
            - 场景/地点大幅切换
            - 时间跳跃（数天后、数年后等）
            - 目标角色的出现/消失模式变化
            - 情节线明显转折
            - 情绪基调变化
      
            【输出格式】严格按以下 JSON 数组格式输出，不要额外内容：
            [
              {{
                "layerIndex": 1,
                "layerName": "初遇",
                "startChapter": 1,
                "endChapter": 52,
              }}
            ]
      
            【输出格式校验规则】
            1. 第一层必须从第1章开始，最后一层必须在第{totalChapters}章结束。
            2. 前后层的章节必须严格连续：每一层的 startChapter 应等于上一层的 endChapter + 1。
            3. layerIndex 必须从 1 开始连续递增，不得跳跃或重复。
            4. 输出前请自行校验：a) 总章数是否为{totalChapters}；b) 层数是否在{minLayers}至{maxLayers}之间；c) 是否存在章节重叠或遗漏。
            """;

    public String getSceneSplitPrompt() {
        return SCENE_SPLIT_PROMPT;
    }

    public String getLayerSplitPrompt() {
        return LAYER_SPLIT_PROMPT;
    }
}
