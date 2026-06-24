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
            
            【结果确认要求】
            在输出最终结果前，请逐条进行以下确认：
            1. 场景切分必须覆盖本章完整内容，不能遗漏关键叙事片段
            2. 每个 anchor 必须来自原文中的连续片段，不得改写、概括、补字、删字或替换标点
            3. 所有场景必须按原文叙事顺序排列，不得改变前后顺序
            
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 scenes 数组，不得直接返回数组
            2. scenes 中每项必须是完整对象，包含数字 sequence 和字符串 anchor
            3. sequence 从 1 开始连续递增
            4. JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配
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
            请判断该场景对每个维度的信息强度，并给出 0.0-1.0 的 confidence。
            
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
            
            【结果确认要求】
            在输出最终结果前，请逐条进行以下确认：
            1. 五个维度都要基于场景内容独立判断
            2. confidence 必须反映场景中的明确信息强度，不得因为目标角色重要就随意提高
            
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 pools 数组，不得直接返回数组
            2. pools 必须包含 5 个对象，code 分别为 SETTING、PERSONALITY、SPEECH、INTERACTION、KEY_EVENT
            3. confidence 必须是 0.0 到 1.0 之间的数字
            4. JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配
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
            2. 最终生成的总层数不得超过 {maxLayers}。
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
      
            【结果确认要求】
            在输出最终结果前，请逐条进行以下确认：
            1. 所有层必须连续覆盖第 1 章到第 {totalChapters} 章，不得遗漏或重叠
            2. 分层边界必须符合叙事节奏，不得为了凑层数而强行切分
            3. 层数不得超过 {maxLayers}；如果总章节数较少，应优先输出 1 层
            
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 layers 数组，不得直接返回数组
            2. layers 中每项必须是完整对象，包含 layerIndex、layerName、startChapter、endChapter
            3. layerIndex、startChapter、endChapter 必须是数字，layerName 必须是简短字符串
            4. startChapter 和 endChapter 必须使用章节列表左侧的系统内部章节序号
            5. JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配
            """;

    private static final Map<PoolType, String> POOL_TYPE_DESCRIPTIONS = Map.of(
              PoolType.SETTING, """
            【提取要求】
            关注目标角色的年龄、身份、家庭背景、成长经历、特殊设定和显著外貌特征。

            【原则】
            - 只提取原文明确事实，不推测
            - 忽略情绪、态度和互动关系内容
            """,

              PoolType.PERSONALITY, """
            【提取要求】
            关注目标角色的核心性格、价值观、动机、情感模式和内在矛盾。

            【原则】
            - 区分临时状态和稳定特质
            - 只在证据足够时形成性格结论
            - 前后表现变化时，说明变化方向
            """,

              PoolType.SPEECH, """
            【提取要求】
            关注目标角色的称呼方式、语气基调、口癖、句式特点和代表性台词。

            【原则】
            - 区分口头禅和情境性用语
            - 注意角色在不同对象面前说话方式是否有差异
            """,

              PoolType.INTERACTION, """
            【提取要求】
            关注目标角色与主角的关系距离、信任程度、互动模式、冲突方式和情感基调。

            【原则】
            - 关注关系变化，而不是静态标签
            - 如果关系有多次转折，按时间线描述
            """,

              PoolType.KEY_EVENT, """
            【提取要求】
            识别目标角色与主角之间的关键事件。关键事件必须同时满足：
            - 是叙事转折点（之后关系或剧情发生明显变化）
            - 涉及目标角色（不单是主角的个人事件）

            【原则】
            - 日常互动不算关键事件
            - 说明事件原因、经过和后续影响
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
            1. conclusion 必须能由 supportingQuotes 支撑，不得包含无依据的推断
            2. supportingQuotes 应优先直接摘自【相关场景】，sceneIds 必须指向引用来源场景
            3. confidence 应基于证据数量、一致性和明确程度合理评估
           
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 evidences 数组，不得直接返回数组
            2. evidences 中每项必须是完整对象，包含 conclusion、supportingQuotes、sceneIds、confidence
            3. conclusion 为 50 字内字符串；supportingQuotes 为 2~3 条字符串数组；sceneIds 为等长整数数组
            4. confidence 必须是 0.0 到 1.0 之间的数字
            5. JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配
            """;

    private static final String AGGREGATION_PROMPT = """
            你是一个角色画像聚合专家。请根据新证据更新和完善目标角色的完整画像。
          
            【当前画像】
            {currentProfile}
  
            【当前叙事层】：{layerName}
            
            【证据池类型】： {poolType}
  
            【新证据】
            {evidences}
  
            【聚合规则】
            1. 当前画像为空时，根据新证据初始化完整画像
            2. 新证据与当前画像一致时，保留原结论并补充更具体细节
            3. 新证据与当前画像冲突时，以新证据为准修正对应字段
            4. 新证据未涉及的字段保持原样
            5. 所有新增或修改内容必须能被 supportingQuotes 支撑，禁止推测
  
            【字段提示】
            - presume 表示角色特殊设定，如“不会说谎”“路痴”
            - coreTraits 使用短标签，每个标签不超过 12 个中文字符
            - keyEvents 按时间顺序记录关系转折或关键事件
            - conversationSamples 只保留能体现互动模式的代表性对话
  
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
            
            【结果确认要求】
            在输出最终结果前，请逐条进行以下确认：
            1. 新画像必须基于【当前画像】和【新证据】更新，不得忽略已有画像内容
            2. 新证据与当前画像一致时补充细节；冲突时以新证据为准修正
            3. 所有画像内容都必须能由输入证据支撑，字段值保持简洁
            
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 characterProfile 和 interactionProfile
            2. 输出结构必须与上方 JSON 结构一致，不得省略嵌套对象或数组字段
            3. coreTraits、representativeLines、keyEvents、conversationSamples 必须是字符串数组
            4. age 必须是数字；无法确认年龄时使用 0
            5. JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配
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
