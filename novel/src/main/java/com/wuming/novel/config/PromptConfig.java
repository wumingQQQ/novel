package com.wuming.novel.config;

import com.wuming.novel.domain.enums.PoolType;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


@Configuration
public class PromptConfig {
    private static final String  SCENE_SPLIT_PROMPT = """
            你是小说章节叙事场景切分专家。请根据章节内容，将整章切分为语义完整、顺序连续、边界清晰的叙事场景，并仅输出每个场景的起点锚点。

            【章节信息】
            标题：{chapterTitle}
            【章节内容】
            {chapterContent}
            【任务目标】
            识别章节中发生明显叙事转换的位置，将章节拆分为若干个场景。场景切分的核心依据是情节推进或叙事焦点发生实质变化；时间、地点等变化应作为辅助判断，只有当它们引发或伴随新的情节单元、行动目标、描写对象或叙事重心变化时，才应切分新场景。每个场景必须对应原文中连续的一段叙事内容，不能打乱顺序、不能遗漏章节开头、不能虚构不存在的场景。
            【场景切分标准】
            仅在以下至少一种变化明显发生，并形成新的相对完整叙事单元时切分新场景；其中应优先判断情节目标或叙事焦点是否发生实质变化：
            1. 情节目标变化：如从寻找线索转为逃跑、从争执转为战斗、从铺垫转为揭示结果、从准备行动转为执行行动等。
            2. 叙事焦点变化：如主要描写对象、视角人物、行动主体、关注重点发生明显转移，并导致叙事重心改变。
            3. 核心话题变化：如对话或内心独白从一个主要议题转向另一个主要议题，且新话题承担独立的情节推进功能。
            4. 时间变化：如从白天到夜晚、数小时后、次日、回忆与现实切换等；但只有当时间变化带来新的情节阶段或叙事焦点时才切分。
            5. 地点变化：如从房间转到街道、从现实地点转到梦境或记忆场景等；但只有当地点变化伴随新的行动、目标、冲突或叙事重心时才切分。
            【场景长度控制】
            1. 每个场景对应的原文正文长度应尽量控制在 1000–2500 个中文字符之间；场景长度按该场景 anchor 起点到下一个场景 anchor 起点之前的连续原文内容计算，最后一个场景计算到章节结尾。
            2. 若某一叙事单元超过 2500 字，应在不破坏语义完整性的前提下，优先寻找其中情节阶段、行动目标、话题焦点或叙事重心发生相对变化的位置进行切分。
            3. 若某一候选场景不足 1000 字，且缺乏足够独立的叙事功能，应优先并入前后语义最接近、情节最连续的场景。
            4. 字数限制不得凌驾于语义完整性和真实叙事边界之上；当原文章节本身较短、章节结尾剩余内容不足 1000 字，或强行切分会破坏叙事连续性时，可以保留不足 1000 字的场景。
            5. 不得为了满足字数范围而在没有明显叙事转换的位置机械切分，也不得为了合并字数而跨越明显独立的叙事单元。
            【合并与避免过度切分规则】
            1. 不要因为自然段换行、短暂停顿、普通对话轮次、情绪细微变化而切分。
            2. 不要仅因时间或地点轻微变化就切分；若情节目标、人物焦点和叙事重心仍连续，应合并为同一场景。
            3. 如果多个连续片段围绕同一时间、地点、人物焦点和情节目标展开，应合并为同一场景。
            4. 过短且缺乏独立叙事功能的片段不得单独成场景，应并入前后语义最接近的场景。
            5. 每个场景应能概括为一个相对完整的叙事单元，而不是单句事件或零散描写。
            6. 若切分边界不确定，优先少切分，保证场景语义完整，并优先维持情节与叙事焦点的连续性。
            7. 在满足语义完整和叙事边界清晰的前提下，优先使每个场景的原文长度落在 1000–2500 字范围内。
            【顺序规则】
            1. scenes 数组必须严格按原文出现顺序排列。
            2. sequence 从 1 开始，按 1、2、3 连续递增，不得跳号、重复或乱序。
            3. 第一个场景的 anchor 应指向章节正文的第一个有效叙事起点；若正文前有标题、空行或分隔符，应跳过这些非正文内容。
            【anchor 规则】
            1. anchor 必须是对应场景起点处的原文连续片段，必须逐字复制自 {chapterContent}，不得改写、概括、补字、删字或调整标点。
            2. anchor 用于唯一定位场景起点；如果候选片段在章节中不唯一，必须向后延长，直到能唯一定位。
            3. anchor 不限制固定字数，但应尽量简洁；在能唯一定位的前提下，优先选择 10–40 个中文字符左右的连续正文片段。
            4. anchor 优先选择普通正文叙述文字，尽量避免包含引号、括号、破折号、省略号、分隔符、项目符号等特殊符号。
            5. 必须包含标点时，只保留原文中的逗号、句号、问号、感叹号、冒号、分号等常规标点，不得自行替换标点。
            6. 如果场景起点前有单独成行的分隔符，例如 ◇、*、---、——、***，不得将分隔符放入 anchor，应选择其后的第一段正文作为 anchor。
            7. 如果场景以对话开始，优先选择引号内或引号后的连续纯文字部分作为 anchor，不要把引号本身放入 anchor；若无法唯一定位，可适当延长到相邻正文。
            8. anchor 必须对应场景的真实起点附近，不能选取场景中段或结尾的文字。
            【输出内容要求】
            1. 只输出 JSON 对象，不要输出解释、分析、注释、Markdown 代码块或任何额外文字。
            2. 最外层必须是 JSON 对象，且只包含 scenes 字段。
            3. scenes 必须是数组；每个元素必须只包含 sequence 和 anchor 两个字段。
            4. 不要输出场景标题、场景摘要、正文内容、结束锚点或切分理由。
            5. 若整章只有一个完整叙事场景，也必须输出 scenes 数组，且只包含 sequence 为 1 的对象。
            【输出格式】
            {{
              "scenes": [
                {{
                  "sequence": 1,
                  "anchor": "张明推开门，走进了昏暗的房间。"
                }},
                {{
                  "sequence": 2,
                  "anchor": "张明找到一间密室"
                }}
              ]
            }}
            【输出前自检】
            输出前请自行校验：
            1. 最外层是合法 JSON 对象，不是数组。
            2. JSON 完整闭合，所有 {{、}}、[、]、双引号和逗号均合法匹配。
            3. scenes 数组非空，sequence 从 1 开始连续递增。
            4. 每个 anchor 都能在 {chapterContent} 中逐字匹配，并且能唯一定位对应场景起点。
            5. 每个场景对应的原文长度已尽量控制在 1000–2500 字之间；若存在不足或超出，必须是因章节长度、语义完整性或明确叙事边界限制所导致。
            6. 没有输出 JSON 以外的任何文字。
            """;

    private static final String SCENE_POOL_PROMPT = """
            你是一个“角色画像维度识别与置信度评估”专家。请基于给定场景内容，分析该场景分别揭示了目标角色在 5 个角色画像维度上的信息强度，并为每个维度输出 0.0-1.0 的 confidence。
            
            【主角（叙事视角人物）】
            {protagonistName}
            【目标角色（需要分析的对象）】
            {targetName}
            【场景内容】
            {sceneContent}
            【维度定义与判断标准】
            维度	需要识别的信息	判断重点	最终输出到
            SETTING	目标角色的基础设定信息	年龄、身份、职业、种族、家庭背景、社会地位、特殊能力、固定人设规则等。例如“不会说谎”“是某组织成员”“出身贵族”。	角色基础信息
            PERSONALITY	目标角色稳定或阶段性的心理与性格特征	性格倾向、价值观、动机、欲望、恐惧、执念、道德判断、内心想法、行为背后的心理原因。	性格特征
            SPEECH	目标角色的语言表达特征	语气、口癖、常用称呼、说话节奏、礼貌程度、攻击性/亲昵感、代表性台词。仅当目标角色有直接发言，或场景明确描述其说话方式时提高置信度。	说话风格
            INTERACTION	目标角色与主角之间的互动模式	关系亲疏、信任程度、依赖/对抗/保护/试探/疏离等互动方式，双方在该场景中的行为影响与情感张力。	互动画像
            KEY_EVENT	目标角色与主角之间具有转折意义的事件	会改变两人关系、目标角色命运、主线推进或后续行为动机的重要事件。普通对话、日常互动、背景补充不应高估为 KEY_EVENT。	关键事件
            【分析原则】
                只根据【场景内容】进行判断，不要引入场景外信息、常识推测或剧情脑补。
                每个维度必须独立评分，不得因为目标角色在场景中很重要就整体提高所有维度分数。
                confidence 表示“该场景对该维度提供的可用信息强度”，不是该维度本身的重要性。
                若信息来自明确叙述、直接行为、直接台词，可给较高分；若只是隐约暗示，应给较低分。
                若目标角色未出场、未发言，或场景几乎没有关于目标角色的信息，相关维度应接近 0。
                若场景中出现多个角色，只分析【目标角色】{targetName}，不要把主角或其他角色的信息误算到目标角色上。
                对 SPEECH 的判断必须重点关注目标角色自己的语言风格；其他角色对目标角色的评价不等于目标角色说话风格。
                对 KEY_EVENT 的判断必须关注“是否发生了转折性事件”，不要将普通信息披露、气氛描写或一般互动误判为关键事件。
            【confidence 评分参考】
                0.90-1.00：该场景的核心内容就在强烈展现该维度，信息明确、充分、可直接用于画像沉淀。
                0.80-0.89：该维度信息非常明显，是场景的重要组成部分。
                0.60-0.79：该维度有明确可提取信息，但不是唯一重点或信息量中等。
                0.40-0.59：该维度有一定信息，但较零散、有限或需要结合上下文才能更完整理解。
                0.20-0.39：仅有轻微暗示，信息不够明确，不宜作为强画像依据。
                0.01-0.19：几乎没有有效信息，仅存在极弱相关性。
                0.00：完全没有该维度信息。
            【输出要求】
            严格输出一个合法 JSON 对象，不要输出任何解释、分析过程、Markdown、注释或额外文本。
            输出格式必须如下：
            {{
            “pools”: [
            {{“code”: “SETTING”, “confidence”: 0.00}},
            {{“code”: “PERSONALITY”, “confidence”: 0.00}},
            {{“code”: “SPEECH”, “confidence”: 0.00}},
            {{“code”: “INTERACTION”, “confidence”: 0.00}},
            {{“code”: “KEY_EVENT”, “confidence”: 0.00}}
            ]
            }}
            【格式校验规则】
                最外层必须是 JSON 对象，且只包含pools字段。
                pools必须是数组，且必须包含 5 个对象。
                5 个对象的 code 必须且只能分别为：SETTING、PERSONALITY、SPEECH、INTERACTION、KEY_EVENT。
                每个对象必须包含code和confidence两个字段。
                confidence必须是 0.0 到 1.0 之间的数字，不要使用字符串。
                JSON 必须完整闭合，所有 {{、}}、[、]、双引号和逗号都必须合法匹配。
                输出前自行确认五个维度均已基于场景内容独立判断，且分数没有因目标角色重要性而被不当提高。
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
            每条画像结论的 supportingQuotes 数量应控制在 2~3 条。请优先选取信息最丰富、指向最明确的原文引用，避免使用重复或冗余的引用凑数。若相关场景不足或有效引用少于 2 条，允许降至 1 条引用，但必须将对应结论的 confidence 降至 0.6 以下，并优先确保结论仍有直接文本支撑，不得为满足数量要求生成无效引用。
            【输出格式】严格按以下 JSON 对象格式输出，不要额外内容，不要直接返回数组。evidences 数组中的每个元素包含：
            - conclusion: 画像结论（50字内）
            - supportingQuotes: 支撑结论的原文引用列表（通常 2~3 条；仅在有效引用不足时可为 1 条，必须来自原文）
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
            3. confidence 应基于以下规则评估：当引用来自 3 个不同场景且高度一致时，可设为 0.9 以上；当来自 2 个场景且基本一致时，设为 0.7-0.9；当仅有 1 条引用或引用指向模糊时，设为 0.4-0.6；默认不得低于 0.3
            【输出格式校验规则】
            输出时请自行校验：
            1. 最外层必须是 JSON 对象，包含 evidences 数组，不得直接返回数组
            2. evidences 中每项必须是完整对象，包含 conclusion、supportingQuotes、sceneIds、confidence
            3. conclusion 为 50 字内字符串；supportingQuotes 通常为 2~3 条字符串数组，仅在有效引用不足时可为 1 条；sceneIds 为等长整数数组
            4. confidence 必须是 0.0 到 1.0 之间的数字；仅有 1 条引用时必须低于 0.6
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
