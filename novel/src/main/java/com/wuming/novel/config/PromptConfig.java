package com.wuming.novel.config;

import com.wuming.novel.domain.enums.PoolType;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


@Configuration
public class PromptConfig {
    private static final String  SCENE_SPLIT_PROMPT = """
            你是一个小说场景分析专家。请将下面的章节精准地切分为语义完整的场景，确保每个场景的划分有明确的语义边界，避免模糊或重叠。
            
            【章节信息】
            标题：{chapterTitle}

            【章节内容】
            {chapterContent}

            【任务定义】
            请基于语义边界标志，将本章小说内容切分为多个独立的叙事场景单元。

            【语义边界标志判定标准】
            在阅读全文时，必须严格依据以下任一标志的出现，判定为场景切分点：
            1. 时间跳跃：文本中出现表示时间发生明显跨越的词语或句子，例如“第二天”、“几小时后”、“一周后”、“那年冬天”等。
            2. 地点转换：文本中明确描述人物从一处空间环境移动到另一处，例如“离开房间来到花园”、“从机场前往酒店”、“走出大楼”等。
            3. 视角切换：叙事聚焦的人物发生改变，从描写一个人物的心理、感官或行为，转向另一个人物（例如从张三的内心独白切换到李四的所见所闻）。
            4. 话题明显转折：情节线、对话主题或核心冲突发生根本性改变（例如从商业谈判突然转为追忆往事，或从日常对话转为紧急事件）。

            【切分与组织规则】
            1. 最小叙事单元：每个切分出的场景应为一个完整的叙事单元，包含对话、动作、心理活动或环境描写等要素，能独立表达一个情节片段。
            2. 顺序编号：为每个场景分配从1开始连续递增的序号，不得跳号或重复。
            3. 场景粒度控制：
               - 目标字数：每个场景建议控制在800-2000字的篇幅。
               - 合并：若根据语义边界切出的原文片段少于800字且能与相邻片段在时间、地点、视角和话题上保持连贯，则应将其合并到相邻场景中，直到满足字数要求或遇到强制语义边界。
               - 拆分：若单个场景超过2000字且内部存在隐含的次级语义转折（如小的时间跳跃或次要话题切换），可在该次级转折处进行二次拆分。
            4. 优先切分点：当检测到任何上述语义边界标志时，应优先考虑于此处切分。

            【锚点（Anchor）绝对规则】
            1. 定义：锚点是用于在原文中定位该场景起始位置的连续文本片段，要求5-20字左右。
            2. 来源：每个场景的锚点必须取该场景对应的原文第一个完整句子或连续的开头片段。
            3. 精准复制：锚点字符串必须是原文的逐字复制，严格保持所有字符不变，包括但不限于：
               - 全角/半角标点符号（如逗号“，”与“,”、句号“。”与“.”）
               - 中文/英文标点符号（如中文引号“”与英文引号"")
               - 特殊符号（如省略号……、破折号——）
            4. 连续性：锚点必须从原文中的一个位置连续截取，不得从不同段落或不同句子中跳跃式选取字符进行拼接，不得跳过中间任何字符。
            5. 禁止事项：严禁对锚点文本进行任何形式的改写、概括、缩写、补充或标点转换。

            【输出内容约束】
            - 绝对禁止输出角色分析、角色关系判断、人物性格总结等内容。
            - 输出必须且仅包含场景切分的结果。

            【输出格式】
            严格按照以下JSON对象格式输出，不要添加任何额外的解释或文本，不要直接返回数组：

            {{
              "scenes": [
                {{
                  "sequence": 1,
                  "anchor": "原文中定位该场景的首句话，必须严格逐字复制，且必须是原文中的连续片段。示例：张明推开门，走进了昏暗的房间。"
                }}
              ]
            }}

            【输出示例】
            {{
              "scenes": [
                {{
                  "sequence": 1,
                  "anchor": "张明推开门，走进了房间。"
                }},
                {{
                  "sequence": 2,
                  "anchor": "第二天清晨，雨停了。"
                }}
              ]
            }}
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
            
            【输出格式】严格按以下 JSON 对象格式输出，不要额外内容，不要直接返回数组：
            - code 只能取 SETTING、PERSONALITY、SPEECH、INTERACTION、KEY_EVENT
            - confidence 为 0.0-1.0 的数字
            - 只输出对象，不要输出额外文字
            {{
              "pools": [
                {{"code": "SETTING", "confidence": 0.05}},
                {{"code": "PERSONALITY", "confidence": 0.72}},
                {{"code": "SPEECH", "confidence": 0.68}},
                {{"code": "INTERACTION", "confidence": 0.65}},
                {{"code": "KEY_EVENT", "confidence": 0.00}}
              ]
            }}

            【输出示例】
            {{
              "pools": [
                {{"code": "SETTING", "confidence": 0.10}},
                {{"code": "PERSONALITY", "confidence": 0.85}},
                {{"code": "SPEECH", "confidence": 0.70}},
                {{"code": "INTERACTION", "confidence": 0.65}},
                {{"code": "KEY_EVENT", "confidence": 0.20}}
              ]
            }}
            """;

    private static final String LAYER_SPLIT_PROMPT = """
            你是一个小说结构分析专家。请根据你对小说的理解与章节信息，以自然流畅的方式将整部小说划分为多个叙事阶段，在保持叙事连续性的前提下进行分层。
            
            【目标小说】：{novelName}
      
            【章节信息】
            总章节数：{totalChapters}
      
            章节列表（每行左侧数字为系统内部章节序号，标题中的“第X章”可能不是内部序号）：
            {chapterList}
      
            【分层约束】
            1. 每层应包含 {minChaptersPerLayer} 至 {maxChaptersPerLayer} 章，若叙事完整性要求超出此范围，仅在迫不得已时方可触及边界，但不要输出额外字段。
            2. 最终生成的总层数必须小于{maxLayers} 之间。
            3. 当上述两条约束冲突时，优先满足每层章节数约束。
            4. 如果总章节数小于每层最小章数或总章数不大于每层最大章数的1.2倍，则只输出 1 层：startChapter=1，endChapter={totalChapters}，此时 maxLayers约束以 1 层为准。
      
            【层边界判定依据】
            请优先选择叙事节奏自然转换的节点作为层边界，确保层与层之间的过渡顺滑，不割裂故事的连贯性：
            - 场景/地点大幅切换
            - 时间跳跃（数天后、数年后等）
            - 目标角色的出现/消失模式变化
            - 情节线明显转折
            - 情绪基调变化
      
            【输出格式】严格按以下 JSON 对象格式输出，不要额外内容，不要直接返回数组：
            {{
              "layers": [
                {{
                  "layerIndex": 1,
                  "layerName": "初遇",
                  "startChapter": 1,
                  "endChapter": 52
                }}
              ]
            }}

            【输出示例1 — 短篇小说（12章，每层最小章数大于总章数）】
            {{
              "layers": [
                {{"layerIndex": 1, "layerName": "完整短篇", "startChapter": 1, "endChapter": 12}}
              ]
            }}

            【输出示例2 — 中篇小说（80章）】
            {{
              "layers": [
                {{"layerIndex": 1, "layerName": "日常相遇", "startChapter": 1, "endChapter": 20}},
                {{"layerIndex": 2, "layerName": "情感萌芽", "startChapter": 21, "endChapter": 45}},
                {{"layerIndex": 3, "layerName": "冲突与考验", "startChapter": 46, "endChapter": 65}},
                {{"layerIndex": 4, "layerName": "告白与确认", "startChapter": 66, "endChapter": 80}}
              ]
            }}
      
            【输出格式校验规则】
            1. startChapter 和 endChapter 必须使用章节列表左侧的系统内部章节序号，不得使用标题中的原始章节号。
            2. 第一层必须从第1章开始，最后一层必须在第{totalChapters}章结束。
            3. 前后层的章节必须严格连续：每一层的 startChapter 应等于上一层的 endChapter + 1。
            4. layerIndex 必须从 1 开始连续递增，不得跳跃或重复。
            5. 输出前请自行校验：a) 总章数是否为{totalChapters}；b) 层数是否小于{maxLayers}；c) 是否存在章节重叠或遗漏。
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
               
                【输出格式】严格按以下 JSON 对象格式输出，不要额外内容，不要直接返回数组。evidences 数组中的每个元素包含：
                - conclusion: 画像结论（50字内）
                - supportingQuotes: 支撑结论的原文引用列表（2~3条，来自多个场景）
                - sceneIds: 引用对应的场景id列表，需使用原文中给定的sceneId
                - confidence: 置信度（0.0-1.0）
               
                {{
                  "evidences": [
                    {{
                      "conclusion": "画像结论",
                      "supportingQuotes": ["引用1", "引用2"],
                      "sceneIds": [0, 3],
                      "confidence": 0.85
                    }}
                  ]
                }}

                【输出示例】
                {{
                  "evidences": [
                    {{
                      "conclusion": "目标角色表面冷淡，但会在关键时刻关心主角。",
                      "supportingQuotes": ["她沉默片刻，还是把伞递给了他。", "她别过脸说：不用谢。"],
                      "sceneIds": [12, 18],
                      "confidence": 0.86
                    }}
                  ]
                }}
               
                【结果确认要求】
                在输出最终结果前，请逐条进行以下确认：
                1. 每条supportingQuotes中的引用必须是来自上述【相关场景】中的原文原句，不得改写或概括
                2. sceneIds是与supportingQuotes等长的整数数组，其中 sceneIds[i] 表示 supportingQuotes[i] 真实来源的场景编号，且该引用必须能在对应场景的原文中找到
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
               - coreTraits、valueSystem、behaviorPatterns、emotionalPatterns、relationshipAttitude、weaknesses
               - speechStyle.tone、speechStyle.wordsHabit、speechStyle.representativeLines
               - protagonistName、tone、keyEvents、conversationSamples
            4. 新证据涉及当前画像未覆盖的维度时，直接添加对应字段内容，适用于所有字段
            5. 当前画像中已有但新证据未涉及的维度，保持原样不变，适用于所有字段
            6. 所有结论必须有证据中的 supportingQuotes 支撑，禁止推测或脑补——此原则适用于所有字段的更新与填充
  
            【字段说明】
            characterProfile:
              - basicSetting.characterName: 角色名称
              - basicSetting.age: 年龄
              - basicSetting.identity: 身份（学生、会长等）
              - basicSetting.presume: 角色特殊设定（如"不会说谎"、"路痴"等）
              - coreTraits: 核心性格标签，多个短标签，每个标签不超过 12 个中文字符
              - valueSystem: 价值观与判断标准，400 个中文字符以内
              - behaviorPatterns: 行为模式，描述遇事通常如何行动，400 个中文字符以内
              - emotionalPatterns: 情绪模式，描述情绪触发点与表达方式，400 个中文字符以内
              - relationshipAttitude: 关系态度，描述对主角和他人的亲疏、防备、信任方式，400 个中文字符以内
              - weaknesses: 弱点、缺陷或内在矛盾，400 个中文字符以内
              - speechStyle.tone: 语气基调（冷淡/傲娇/温柔/暴躁/俏皮等）
              - speechStyle.wordsHabit: 口癖或习惯用语
              - speechStyle.representativeLines: 代表台词列表（2-5 句）
  
            interactionProfile:
              - protagonistName: 主角名称
              - tone: 与主角的互动基调
              - keyEvents: 关键事件列表（关系转折、重大选择，按时间顺序）
              - conversationSamples: 对话示例列表（2-5 条能体现互动模式的对话）
  
            【输出格式】严格按以下 JSON 返回完整画像，不得省略任何字段：
            {{
              "characterProfile": {{
                "basicSetting": {{
                  "characterName": "角色名",
                  "age": 0,
                  "identity": "身份描述",
                  "presume": "特殊设定"
                }},
                "coreTraits": ["核心标签1", "核心标签2"],
                "valueSystem": "价值观与判断标准",
                "behaviorPatterns": "行为模式",
                "emotionalPatterns": "情绪模式",
                "relationshipAttitude": "关系态度",
                "weaknesses": "弱点与矛盾点",
                "speechStyle": {{
                  "tone": "语气基调",
                  "wordsHabit": "口癖",
                  "representativeLines": ["台词1", "台词2"]
                }}
              }},
              "interactionProfile": {{
                "protagonistName": "主角名",
                "tone": "互动基调",
                "keyEvents": ["事件1", "事件2"],
                "conversationSamples": ["对话1", "对话2"]
              }}
            }}

            【输出示例】
            {{
              "characterProfile": {{
                "basicSetting": {{
                  "characterName": "清野凛",
                  "age": 16,
                  "identity": "学生会相关人物",
                  "presume": "坚持诚实，不擅长说谎"
                }},
                "coreTraits": ["冷静克制", "原则感强"],
                "valueSystem": "重视真实与自我选择，不愿因他人期待改变判断。",
                "behaviorPatterns": "遇到冲突时先保持距离和观察，再用直接语言指出问题。",
                "emotionalPatterns": "情绪表达克制，常以沉默、反问或简短回应掩饰真实波动。",
                "relationshipAttitude": "对亲近关系保持谨慎，但会在信任后允许对方进入私人空间。",
                "weaknesses": "过度坚持原则时容易显得疏离，也可能忽略他人的情绪缓冲。",
                "speechStyle": {{
                  "tone": "冷静、直接、偶尔带讽刺",
                  "wordsHabit": "常用反问和简短判断回应对方",
                  "representativeLines": ["你在说什么？", "请不要擅自下结论。"]
                }}
              }},
              "interactionProfile": {{
                "protagonistName": "渡边彻",
                "tone": "表面斗嘴，实际互相信任",
                "keyEvents": ["初次合作调查", "关系因共同事件加深"],
                "conversationSamples": ["“你又在胡说。” “这是合理推测。”", "“需要帮忙吗？” “不用，但你可以留下。”"]
              }}
            }}
            """;

    public String getAggregationPrompt() {
        return AGGREGATION_PROMPT;
    }


    public String getPoolDescription(PoolType poolType) {
        String poolDescription = POOL_TYPE_DESCRIPTIONS.get(poolType);
        if (poolDescription == null) {
            throw new IllegalArgumentException("Unsupported PoolType: " + poolType);
        }

        return poolDescription;
    }

    public String getEvidenceExtractPrompt() {
        return EVIDENCE_EXTRACT_PROMPT;
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
