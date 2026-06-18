package com.wuming.novel.config;

import com.wuming.novel.domain.enums.PoolType;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


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

    private static final String SCENE_POOL_PROMPT = """
            你是一个角色画像分析专家。请分析以下场景，判断它揭示了目标角色的哪些维度信息。
            
            【主角（叙事视角人物）】：{protagonistName}
            【目标角色（需要分析的对象）】：{targetName}
            
            【场景内容】
            {sceneContent}
            
            【维度定义】
            
            | 维度 | 关注什么 | 最终输出到 |
            |------|---------|-----------|
            | SETTING | 目标角色的基础设定：年龄/身份/家庭背景/性格设定（如"不会说谎"） | 角色基础信息 |
            | PERSONALITY | 目标角色的性格、价值观、动机、内心想法 | 性格特征 |
            | SPEECH | 目标角色的语气/口癖/常用称呼/代表性台词 | 说话风格 |
            | INTERACTION | 目标角色与主角的互动方式、关系亲密度、信任程度 | 互动画像 |
            | KEY_EVENT | 目标角色与主角之间发生的转折性事件 | 关键事件 |
            
            【分析要求】
            1. 仔细阅读场景，判断它包含以上哪些维度的信息
            2. 置信度 0.0-1.0，越高表示该维度信息越明确、越重要
            
            【置信度参考】
            - 0.8以上：该场景主要就在展现这个维度
            - 0.5-0.8：场景包含该维度的明确信息
            - 0.2-0.5：暗示了该维度但不够明确
            - 0.2以下：几乎没有该维度信息
            
            【输出格式】严格按以下 JSON 格式：
            [
              {{"SETTING": 0.05}},
              {{"PERSONALITY": 0.72}},
              {{"SPEECH": 0.68}},
              {{"INTERACTION": 0.65}},
              {{"KEY_EVENT": 0.00}}
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

    private static final Map<PoolType, String> POOL_TYPE_DESCRIPTIONS = Map.of(
            PoolType.SETTING, """
          【提取要求】
          从场景中提取目标角色的基础设定信息。只提取以下内容：

          1. 身份信息：年龄、职业、身份地位（如"学生会会长"、"富家千金"）
          2. 背景信息：家庭出身、成长环境、教育经历
          3. 设定信息：明确交代的人物设定（如"天生不会说谎"、"路痴"）
          4. 外貌特征：显著的外貌描写

          【原则】
          - 只提取原文明确提及的事实，不推测、不脑补
          - 同一设定在不同场景重复出现时，取证据更充足的信息
          - 忽略情绪和态度相关内容（那些归 PERSONALITY 池）
          """,

            PoolType.PERSONALITY, """
          【提取要求】
          从场景中分析目标角色的性格特征，关注以下维度：

          1. 核心性格：用 2-3 个关键词概括（如"傲娇"、"外冷内热"、"温柔坚定"）
          2. 价值观：角色在意什么、坚持什么原则
          3. 动机：角色想要什么、害怕什么
          4. 情感模式：表达情感的方式（压抑/外放/别扭）
          5. 矛盾点：角色内心冲突（如"想靠近又害怕受伤"）

          【原则】
          - 必须跨场景验证：只在单个场景出现的行为不能作为性格结论
          - 区分"状态"和"特质"：暂时的心情≠稳定的性格
          - 如果前后矛盾（前期冷漠、后期热情），两个都写并说明转变
          """,

            PoolType.SPEECH, """
          【提取要求】
          从场景中分析目标角色的语言风格，关注以下维度：

          1. 称呼方式：如何称呼主角/他人（如直呼其名、"喂"、"前辈"）
          2. 语气基调：冷淡/傲娇/温柔/暴躁/俏皮
          3. 口癖：习惯性用语（如"笨蛋"、"无聊"、"啧"）
          4. 句式特点：反问句多/陈述句多/说话简洁或啰嗦
          5. 语言习惯：说话前先叹气/喜欢打断别人/说话带刺

          【原则】
          - 区分口头禅和情境性用语（生气时说"笨蛋"≠日常口癖）
          - 注意角色在不同对象面前说话方式是否有差异
          """,

            PoolType.INTERACTION, """
          【提取要求】
          从场景中分析目标角色与主角之间的互动关系，关注以下维度：

          1. 关系距离：从陌生→熟悉→亲密的程度和变化
          2. 信任程度：角色是否信任主角、愿意分享什么
          3. 互动模式：——谁主动、谁回避、谁主导关系
          4. 冲突模式：——争吵方式、和好方式、冷战时长
          5. 情感基调：——轻松/紧张/暧昧/敌对/依赖等

          【原则】
          - 关注"变化"而非"状态"：关系是动态的
          - 标注证据所在的阶段（如"前期"、"中期"）
          - 如果关系有多次转折，按时间线排列
          """,

            PoolType.KEY_EVENT, """
          【提取要求】
          从场景中识别目标角色与主角之间的关键事件。关键事件必须同时满足：
          - 是叙事转折点（之后关系或剧情发生明显变化）
          - 涉及目标角色（不单是主角的个人事件）

          按以下类型分类：

          A. 关系转折：初遇、表白、决裂、和解、重逢
          B. 情感爆发：争吵、哭泣、坦白、告白
          C. 身份变化：转学、毕业、晋升、觉醒
          D. 重大选择：角色做出的影响后续的重要决定

          【原则】
          - 日常互动不算关键事件（那些归 INTERACTION 池）
          - 每个事件注明"发生原因→事件经过→后续影响"
          """
    );

    private static final String EVIDENCE_EXTRACT_PROMPT = """
                你是一个角色画像分析专家。请根据以下场景，归纳目标角色的画像信息。
                
                【目标角色】：{targetName}
                【池类型】：{poolTypeName}
                【层信息】：{layerName}
               
                【相关场景】（共{sceneCount}个，每个以“[sceneId]: 场景”格式给出）
                {scenes}
               
                {poolDescription}
               
                【引用限制】
                每条画像结论的 supportingQuotes 数量必须控制在 2~3 条。请优先选取信息最丰富、指向最明确的原文引用，避免使用重复或冗余的引用凑数。
               
                【输出格式】JSON数组，每个元素包含：
                - conclusion: 画像结论（50字内）
                - supportingQuotes: 支撑结论的原文引用列表（2~3条，来自多个场景）
                - sceneIndices: 引用对应的场景索引列表，需使用原文中给定的sceneId
                - confidence: 置信度（0.0-1.0）
               
                [
                  {{
                    "conclusion": "画像结论",
                    "supportingQuotes": ["引用1", "引用2"],
                    "sceneIndices": [0, 3],
                    "confidence": 0.85
                  }}
                ]
               
                【结果确认要求】
                在输出最终结果前，请逐条进行以下确认：
                1. 每条supportingQuotes中的引用必须是来自上述【相关场景】中的原文原句，不得改写或概括
                2. sceneIndices是与supportingQuotes等长的整数数组，其中 sceneIndices[i] 表示 supportingQuotes[i] 真实来源的场景编号，且该引用必须能在对应场景的原文中找到
                3. 所有conclusion必须能直接由supportingQuotes中的原文引用支撑，不得包含无原文依据的推断
                4. confidence评分需基于引用场景的数量和一致性进行合理评估，引用场景数量越多且证据方向越一致，confidence 应越高。但请注意，supportingQuotes存在数量限制，不得为了提升 confidence 而突破此数量限制
               
                返回结果前请确认以上各项均已验证通过
                """;

    private static final String AGGREGATION_PROMPT = """
            你是一个角色画像聚合专家。请根据新证据更新和完善目标角色的完整画像。
          
            【当前画像】
            {currentProfile}
  
            【当前叙事层】：{layerName}
            
            【证据池类型】： {poolType}
  
            【新证据】
            {evidences}
  
            【聚合规则与字段绑定】
            以下规则明确适用于所有字段（characterProfile 和 interactionProfile 下的全部字段），除非特别指明：
            1. 如果当前画像为空（所有字段为 null），直接用证据中的信息构建初始画像，填充所有字段
            2. 新证据与当前画像一致时，保留已有内容并补充更具体的细节，适用于所有字段
            3. 新证据与当前画像冲突时，以新证据为准修正对应字段——该原则适用于所有字段，包括但不限于：
               - basicSetting.characterName、basicSetting.age、basicSetting.identity、basicSetting.presume
               - personality、speechStyle.tone、speechStyle.wordsHabit、speechStyle.representativeLines
               - protagonistName、tone、keyEvents、conservationSamples
            4. 新证据涉及当前画像未覆盖的维度时，直接添加对应字段内容，适用于所有字段
            5. 当前画像中已有但新证据未涉及的维度，保持原样不变，适用于所有字段
            6. 所有结论必须有证据中的 supportingQuotes 支撑，禁止推测或脑补——此原则适用于所有字段的更新与填充
  
            【字段说明】
            characterProfile:
              - basicSetting.characterName: 角色名称
              - basicSetting.age: 年龄
              - basicSetting.identity: 身份（学生、会长等）
              - basicSetting.presume: 角色特殊设定（如"不会说谎"、"路痴"等）
              - personality: 性格描述（核心性格、价值观）
              - speechStyle.tone: 语气基调（冷淡/傲娇/温柔/暴躁/俏皮等）
              - speechStyle.wordsHabit: 口癖或习惯用语
              - speechStyle.representativeLines: 代表台词列表（2-5 句）
  
            interactionProfile:
              - protagonistName: 主角名称
              - tone: 与主角的互动基调
              - keyEvents: 关键事件列表（关系转折、重大选择，按时间顺序）
              - conservationSamples: 对话示例列表（2-5 条能体现互动模式的对话）
  
            【输出格式】严格按以下 JSON 返回完整画像，不得省略任何字段：
            {{
              "characterProfile": {{
                "basicSetting": {{
                  "characterName": "角色名",
                  "age": 0,
                  "identity": "身份描述",
                  "presume": "特殊设定"
                }},
                "personality": "性格描述",
                "speechStyle": {
                  "tone": "语气基调",
                  "wordsHabit": "口癖",
                  "representativeLines": ["台词1", "台词2"]
                }}
              }},
              "interactionProfile": {{
                "protagonistName": "主角名",
                "tone": "互动基调",
                "keyEvents": ["事件1", "事件2"],
                "conservationSamples": ["对话1", "对话2"]
              }}
            }}
            """;

    public String getAggregationPrompt() {
        return AGGREGATION_PROMPT;
    }


    public String getEvidenceExtractPrompt(PoolType poolType) {
        String poolDescription = POOL_TYPE_DESCRIPTIONS.get(poolType);
        if (poolDescription == null) {
            throw new IllegalArgumentException("Unsupported PoolType: " + poolType);
        }

        return EVIDENCE_EXTRACT_PROMPT.replace("{poolDescription}", poolDescription);
    }

    public String getSceneSplitPrompt() {
        return SCENE_SPLIT_PROMPT;
    }

    public String getLayerSplitPrompt() {
        return LAYER_SPLIT_PROMPT;
    }

    public String getScenePoolPrompt() {
        return SCENE_POOL_PROMPT;
    }
}
