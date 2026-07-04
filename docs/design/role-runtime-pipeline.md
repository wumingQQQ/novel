# 原作样本驱动的角色构建工程方案

## 方案定位

本方案用于替换旧的“章节切场景 -> 场景分池 -> 证据抽取 -> 画像聚合 -> 画像细节增强”画像链路。新流程不再把角色聊天效果建立在多阶段抽象画像上，而是以原作样本为核心资产。

核心目标：

```text
让 chat 模块在每轮对话中召回目标角色的原作表现样本，
再通过 few-shot prompt 让模型模仿角色的说话方式、反应模式和行为边界。
```

核心原则：

1. 原作样本优先于抽象画像。
2. 角色对话和描写优先于事实列表。
3. 运行时动态召回优先于一次性塞满 system prompt。
4. LLM 只做归因、筛选和轻量总结，避免重新设计复杂多阶段抽取链路。
5. MySQL 保存业务元数据，Redis VectorStore 保存向量索引，两者通过状态字段协调。

## 最终数据模型

### 1. role_characters

角色唯一标识表。chat 模块应依赖稳定的 `characterId`，而不是每次通过 `novelId + characterName` 临时定位角色。

```sql
CREATE TABLE role_characters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    novel_name VARCHAR(100) NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    aliases JSON COMMENT '角色别名，初版可为空',
    build_status VARCHAR(20) DEFAULT 'PENDING'
        COMMENT 'PENDING/BUILDING/COMPLETED/INCOMPLETE',
    build_error TEXT COMMENT '构建失败或不达标原因',
    completed_time DATETIME COMMENT '构建完成时间',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_novel_character (novel_id, character_name),
    INDEX idx_novel (novel_id)
) COMMENT '角色唯一标识表';
```

设计说明：

- 初版按 `novel_id + character_name` 唯一定位角色。
- `aliases` 先保留扩展位，不在 MVP 中做复杂别名归并。
- 后续 chat session、RPC 查询、样本召回都以 `characterId` 为入口。
- `buildStatus` 状态机：`PENDING` → `BUILDING` → `COMPLETED` / `INCOMPLETE`。
- chat 模块只允许使用 `buildStatus = COMPLETED` 的角色。

角色可用标准（满足以下全部条件才标记为 `COMPLETED`）：

```text
- role_examples >= 30 条（且 INTERACTION 类型 >= 20 条）
- role_reaction_rules >= 5 条
- role_profiles 构建成功
```

任一条件不满足，标记为 `INCOMPLETE` 并记录 `buildError`。

### 章节洞察字段

每章在构建 passage 前，先做一次 LLM 章节分析，产出摘要、主要人物和场景边界。结果保存在 `chapters` 表，供后续 passage 切分和向量索引复用。

```sql
ALTER TABLE chapters
    ADD COLUMN summary TEXT COMMENT '章节摘要',
    ADD COLUMN main_characters TEXT COMMENT '章节主要出场人物，逗号分隔',
    ADD COLUMN scene_boundaries TEXT COMMENT '场景切换段落号列表，JSON数组，如[10,23,45]',
    ADD COLUMN analysis_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
    ADD COLUMN analysis_error TEXT COMMENT '章节分析失败原因',
    ADD COLUMN analyzed_time DATETIME COMMENT '章节分析完成时间';
```

设计说明：

- `sceneBoundaries` 是段落编号列表，表示场景切换发生在该编号段落之后。切分器直接按此列表切分，不依赖字符数规则。
- `summary` 和 `mainCharacters` 不拼入 passage embedding，保留在 MySQL 中，召回 passage 后回查拼入 prompt 上下文。
- 章节分析失败不阻断 passage 构建，可降级为按固定段落数窗口切分。
- `analysis_status` 与 passage 的 `vectorStatus` 独立，互不阻断。

章节分析 LLM 调用（每章一次，任务单一）：

```text
这一章按段落编号如下：
P1: ...
P2: ...

请返回：
1. 章节摘要（100字以内）
2. 全章出场人物列表
3. 场景切换发生在哪些段落之后

输出JSON：
{
  "summary": "...",
  "characters": ["渡边彻", "清野凛"],
  "sceneBoundaries": [10, 23, 45]
}
```

约束：输出结构平坦，无嵌套，降低格式失败率。

### 2. novel_passages

小说文本块表。它是 passage 向量索引、候选 passage 筛选和 role example 抽取的基础。

```sql
CREATE TABLE novel_passages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    content TEXT NOT NULL COMMENT '原文内容',
    sequence INT NOT NULL COMMENT '全书顺序',
    chapter_sequence INT NOT NULL COMMENT '章节内顺序',
    word_count INT NOT NULL,
    start_paragraph INT NOT NULL COMMENT '对应章节内起始段落编号',
    end_paragraph INT NOT NULL COMMENT '对应章节内结束段落编号',
    vector_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_novel_seq (novel_id, sequence),
    INDEX idx_chapter (chapter_id, chapter_sequence),
    INDEX idx_vector_status (vector_status)
) COMMENT '小说文本块';
```

设计说明：

- passage 以**场景**为单位切分，而非固定字符数。切分边界来自章节分析 LLM 的 `sceneBoundaries`。
- `startParagraph` 和 `endParagraph` 记录段落范围，用于追溯、相邻 passage 回查和时间线重建。
- `chapterSequence` 是该 passage 在当前章节内的顺序，与 `sequence`（全书顺序）共同支持跨章节排序。
- 章节分析失败时，降级为按固定段落数（默认15段）加 3 段 overlap 的滑动窗口切分。
- Redis VectorStore 文档 ID 使用 `novel_passage:{passageId}`。

### 3. passage_characters

passage 与出场角色的映射表。用于候选 passage 筛选，替代 `content LIKE '%角色名%'` 的粗糙方案。

```sql
CREATE TABLE passage_characters (
    passage_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (passage_id, character_name),
    INDEX idx_character_name (character_name)
) COMMENT 'passage 与角色名映射，用于候选 passage 筛选';
```

设计说明：

- 每个 passage 切分完成后，单独调用一次 LLM 识别出场人物，结果写入本表。
- 使用角色名（字符串）而非 `characterId`，因为此时 `role_characters` 可能尚未创建。
- LLM 负责把代词、昵称（"她"、"R桑"）归一到标准角色名，比 LIKE 匹配更准确。
- 候选 passage 筛选时按出现角色数升序排列，优先处理角色聚焦的场景。

出场人物识别 LLM 调用（每个 passage 一次，独立失败）：

```text
原文片段：
...

这段文字中出现了哪些人物？
只返回名字列表，例如：["渡边彻", "清野凛"]
```

约束：输出极简，失败只影响单个 passage，不影响整章。

### 3. role_examples

角色交互单元样本库，是本方案最核心的资产。每条样本是一个完整的"触发→角色反应"单元，而不是孤立的台词片段。

```sql
CREATE TABLE role_examples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    passage_id BIGINT NOT NULL,
    sample_type VARCHAR(30) NOT NULL COMMENT 'INTERACTION/NARRATION_EVAL',
    sample_text TEXT NOT NULL COMMENT '完整交互单元原文，直接用于向量化和 few-shot 注入',
    confidence DOUBLE COMMENT '0.0-1.0',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_passage (passage_id),
    INDEX idx_type (sample_type),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色交互单元样本库';
```

样本类型：

| 类型 | 说明 |
| --- | --- |
| `INTERACTION` | 完整的触发+角色反应单元，含多轮对话、动作、沉默 |
| `NARRATION_EVAL` | 旁白对角色性格、习惯、状态的总结性评价（无交互结构） |

设计说明：

- `sampleText` 是完整交互单元原文，包含触发内容和角色反应，不拆分。
- 一个完整交互单元 = 触发（他人台词/场景）+ 角色反应（台词+动作）+ 语义上属于同一话题的连续几轮。
- `sampleText` 直接用于向量化和 few-shot prompt 注入，不需要额外拼接。
- `confidence` 控制是否进入 profile 总结（阈值 0.8）。
- Redis VectorStore 文档 ID 使用 `role_example:{exampleId}`，index 为 `idx:role-example`。

### 4. role_reaction_rules

角色情境反应规则表。每条规则描述角色在特定情境下的反应逻辑，由情境探针驱动 RAG 归纳生成，支持按需检索。

```sql
CREATE TABLE role_reaction_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    situation VARCHAR(100) NOT NULL COMMENT '情境描述，如：被质疑时、被问及感情时',
    rule TEXT NOT NULL COMMENT '归纳出的反应规则',
    evidence_passage_ids JSON COMMENT '支撑证据的passageId列表',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色情境反应规则';
```

设计说明：

- 每条规则对应一个情境，独立存储、独立向量化，支持按需检索。
- 向量化文本使用 `situation + rule` 拼接，同时捕捉情境语义和规则内容。
- Redis VectorStore 文档 ID 使用 `role_reaction_rule:{ruleId}`，index 为 `idx:role-reaction-rule`。
- `evidencePassageIds` 保留原文来源，可追溯和调试。
- chat 每轮用 `userInput` 检索 top-1 或 top-2 条规则，不全量加载。

### 5. role_profiles

角色轻量摘要表。它只用于 system prompt 的基础约束，session 创建时加载一次，不随每轮对话变化。

```sql
CREATE TABLE role_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT UNIQUE NOT NULL,
    character_name VARCHAR(50),
    novel_id BIGINT,
    novel_name VARCHAR(100),
    basic_info JSON COMMENT '基础信息：age, gender, occupation, appearance',
    core_traits TEXT COMMENT '3-5个核心性格特质，含能力设定',
    speaking_style JSON COMMENT '说话风格：signature + distinctivePatterns + avoidPatterns',
    forbidden_behaviors TEXT COMMENT '绝不做的事，换行分隔',
    build_version VARCHAR(20) DEFAULT 'v1.0.0',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_character (character_id)
) COMMENT '角色画像摘要';
```

字段详细定义：

**basic_info（JSON）**

```json
{
  "age": "16-17岁",
  "gender": "女",
  "occupation": "高中生",
  "appearance": "黑色长发，气质冷淡"
}
```

只填有原文证据的字段，无证据留空字符串。

**core_traits（TEXT）**

```text
冷静理性、不轻易表露情感；骄傲但非傲慢；直觉敏锐，能识破谎言和掩饰；善于分析但不爱解释
```

包含性格特质 + 能力设定（如"能看穿谎言"），简洁自然语言，50-80字。

**speaking_style（JSON）**

```json
{
  "signature": "用单词回应，拒绝多余解释",
  "distinctivePatterns": [
    "用「撒谎」直接否定对方，不展开",
    "反问代替否定：「你以为我是来跟你商量的？」",
    "胜利后简短赞许：「你也很不错。」"
  ],
  "avoidPatterns": [
    "不用感叹词",
    "不主动解释自己的行为"
  ]
}
```

- `signature`：一句话概括核心风格
- `distinctivePatterns`：2-3个具体句式，**配原文例子**，有辨识度
- `avoidPatterns`：2-3个明确不会出现的表达

**forbidden_behaviors（TEXT）**

```text
不会在陌生人面前示弱
不主动为他人辩解或说情
不会明确表白或主动示好
不说脏话，不失态
```

换行分隔的列表，3-5条，是角色扮演的硬边界。

设计说明：

- `RoleProfile` 构建失败不应阻断角色可用性，examples 和 reaction_rules 仍可独立工作。
- 关系不存在本表，关系差异化通过 `role_reaction_rules` 的情境探针（"面对亲近的人" vs "面对陌生人"）实现。
- `RoleProfile` 只提供身份、性格、说话风格和行为禁忌等静态约束，session 创建时加载一次。
- Chat 的生动性主要来自每轮动态召回的 `role_examples` 和 `role_reaction_rules`。

## 核心流程实现

### 阶段1：Passage 构建与向量化

职责类建议：

```text
ChapterAnalysisService   -- 章节分析（LLM）
NovelPassageService      -- passage 切分与存储
PassageCharacterService  -- 出场人物识别（LLM）
NovelPassageVectorIndexService -- 向量化
```

**子阶段 1-1：章节分析**

1. 按章节顺序逐章调用 LLM，产出摘要、主要人物和场景边界段落号。
2. 结果写入 `chapters` 表的 `summary`、`mainCharacters`、`sceneBoundaries`。
3. 更新 `analysisStatus = DONE`，失败则记录 `analysisError`，**不阻断后续切分**。

**子阶段 1-2：passage 切分**

1. 读取章节原文，按 `\n` 分割成段落列表，过滤空行。
2. 若章节分析成功，按 `sceneBoundaries` 切分，每个场景为一个 passage。
3. 若章节分析失败，降级为滑动窗口：窗口 15 段，overlap 3 段。
4. 每个 passage 记录 `startParagraph`、`endParagraph`、`chapterSequence`、全书 `sequence`。
5. 写入 `novel_passages`，初始 `vectorStatus = PENDING`。

**子阶段 1-3：出场人物识别**

1. 对每个 passage 单独调用 LLM，识别出场人物，写入 `passage_characters`。
2. 每次调用独立，失败只跳过当前 passage，不影响其他 passage。
3. 可与 passage 写入并行执行。

**子阶段 1-4：向量化**

1. 查询 `vectorStatus = PENDING` 的 passage，批量生成 embedding。
2. 写入 Redis VectorStore（`idx:novel-passage`）。
3. 成功后回写 `vectorStatus = INDEXED`，失败回写 `FAILED` 和 `vectorError`。

关键约束：

- 不要用一个大事务包住整本小说构建和向量化。
- MySQL 写入和 Redis 写入不是同一事务，必须依赖 `vectorStatus` 状态字段重试。
- embedding 优先批量调用，减少成本和网络开销。
- 章节分析、出场人物识别、向量化三步互相独立，均可单独重试。

### 阶段2：候选 Passage 筛选

职责类建议：

```text
CandidatePassageSelector
```

职责：

1. 通过 `passage_characters` 表按角色名查询候选 passage。
2. 按 passage 内出场角色数升序排列，优先处理角色聚焦的场景。
3. 将候选 passage 交给 `RoleExampleExtractor` 做后续抽取。

候选 passage 查询：

```sql
SELECT p.*, COUNT(pc2.character_name) AS character_count
FROM novel_passages p
JOIN passage_characters pc ON p.id = pc.passage_id
    AND pc.character_name = :characterName
JOIN passage_characters pc2 ON p.id = pc2.passage_id
WHERE p.novel_id = :novelId
GROUP BY p.id
ORDER BY character_count ASC
```

`character_count` 越小，说明场景越聚焦于目标角色，example 密度更高，优先处理。

候选筛选原则：

- 高召回优先，漏掉比误包含代价更高，attribution 阶段会再过滤。
- 不再依赖 `content LIKE '%角色名%'`，由 LLM 在出场人物识别阶段完成归一。
- 初版只处理 `passage_characters` 命中的 passage，不做向量相似度补充。

后续增强：

- 加入别名命中（`role_characters.aliases`）。
- 加入相邻 passage 扩展（通过 `chapterSequence ± 1` 回查）。
- 加入向量召回补充隐性相关材料。

### 阶段3：RoleExample 抽取

职责类建议：

```text
RoleExampleExtractor
```

职责：

1. 对每个候选 passage，将完整原文交给 LLM 做全量抽取。
2. LLM 直接从 passage 中识别并归类三类样本：DIALOGUE、ACTION_DESCRIPTION、NARRATION_EVAL。
3. 保存确认后的 `role_examples`，写入 `sampleTextHash` 防止重复。
4. 将新样本标记为 `vectorStatus = PENDING`，后续批量向量化。

LLM 抽取任务（每个 passage 一次调用）：

```text
目标角色：{characterName}

原文：
{passage.content}

请从上述原文中提取所有关于「{characterName}」的样本，分两类：

1. INTERACTION：完整的触发+角色反应单元。
   - 包含引发角色反应的触发内容（他人台词、场景）
   - 包含角色的完整反应（台词 + 动作，语义上属于同一话题的连续几轮）
   - 一个话题内的连续对话作为一个整体，不拆分
   - 无法确认是目标角色参与的交互，不要提取

2. NARRATION_EVAL：旁白对角色性格、习惯、状态的总结性描写。
   - 语义完整的评价保持完整，不拆分
   - 单次行为描写不算

输出JSON：
{
  "examples": [
    {
      "type": "INTERACTION",
      "sampleText": "（完整交互单元原文，含触发和反应）",
      "confidence": 0.95
    },
    {
      "type": "NARRATION_EVAL",
      "sampleText": "（完整旁白评价原文）",
      "confidence": 0.85
    }
  ]
}

注意：宁缺毋滥，无法确认归属的不要输出。
```

约束：

- 直接全量交给 LLM，不做规则预筛，避免漏掉代词、无归因对话等隐性样本。
- passage 已是场景级别，token 消耗可控。
- LLM 失败时跳过当前 passage，记录日志，不中断整个角色构建。

### 阶段4：ReactionRule 构建

职责类建议：

```text
RoleReactionRuleBuilder
```

**情境探针集（初版约 15-20 条）**：

```text
情绪触发类：
  被质疑或不信任时 / 被公开赞美时 / 被冒犯或侮辱时
  感到尴尬时 / 遇到失败或出错时

关系互动类：
  面对亲近的人主动示好时 / 被陌生人搭话时
  与不喜欢的人交流时 / 被人求助时

话题边界类：
  被问及自己的弱点或软肋时 / 被问及感情或亲密关系时
  被问及家庭或背景时 / 被人开玩笑或调侃时

行为倾向类：
  需要承认错误时 / 需要向人道谢时
  独处或安静时 / 面对无聊或无意义的事时
```

职责：

1. 对每个情境，调用 LLM 将情境描述改写为语义检索 query。
2. 用改写后的 query 在 `idx:novel-passage` 检索，按 `characterId` 对应的 passageId 列表过滤。
3. 取 top-K 相关 passage，送入 LLM 归纳该情境下的反应规则。
4. 保存到 `role_reaction_rules`，记录 `evidencePassageIds`。
5. 标记 `vectorStatus = PENDING`，后续向量化。

改写 query 示例：

```text
情境：「被质疑或不信任时」

改写后 query：
  「有人对她的判断提出异议」
  「她说的话没有被相信」
  「被人当面反驳或质疑能力」
```

候选 passage 过滤方式：

```sql
-- 先从 passage_characters 拿到该角色命中的 passageId 集合
SELECT passage_id FROM passage_characters
WHERE character_name = :characterName

-- 向量检索结果在应用层与上述集合取交集
-- 不依赖 VectorStore 的 filter 做列表过滤（列表过长时性能不可控）
```

归纳 LLM prompt：

```text
目标角色：{characterName}

情境：{situation}

以下是原作中与该情境相关的片段：
{retrievedPassages}

请归纳：{characterName} 在「{situation}」时，通常如何反应？

要求：
- 基于上述原文，不要凭空推断
- 50字以内，简洁描述反应模式
- 如原文不足以支撑，直接回复”证据不足”

输出JSON：
{“rule”: “...”, “evidencePassageIds”: [12, 45]}
```

约束：

- 每个情境独立调用，失败只跳过该情境。
- `evidencePassageIds` 为空或 LLM 回复”证据不足”时，不写入该条规则。
- 情境探针集可配置，不硬编码在业务逻辑中。

### 阶段5：RoleProfile 轻量总结

职责类建议：

```text
RoleProfileBuilder
```

职责：

1. 查询高置信度 `RoleExample`（confidence ≥ 0.8）。
2. 按类型取样：最多 40 条 INTERACTION、10 条 NARRATION_EVAL。
3. 调用 LLM 生成简短约束摘要。
4. 选择代表性样本 ID（从不同类型各取 2-3 条）。
5. 保存或覆盖 `role_profiles`。
6. 检查角色可用标准，更新 `role_characters.buildStatus`。

总结内容：

- `basicInfo`
- `coreTraits`
- `speakingStyle`
- `forbiddenBehaviors`
- `keyRelationships`
- `representativeExamples`

`forbiddenBehaviors` 归纳策略：

- 从交互单元中提炼角色**从未出现的反应**或**明确拒绝的行为**。
- 从 NARRATION_EVAL 中直接提取”从不...”、”绝不...”类描写。
- LLM 归纳时要求”必须有原文证据支撑，不能推测”。

约束：

- 只总结有证据的信息，不写传记。
- `forbiddenBehaviors` 比”总是做什么”更重要，是角色扮演的硬边界。
- Profile 构建完成后，统一检查角色可用标准并更新 `buildStatus`。

### 阶段6：Chat 检索服务

职责类建议：

```text
RoleExampleRetrievalService
RoleReactionRuleRetrievalService
```

每轮对话执行两路并行检索：

**检索1：role_examples（说话风格）**

1. 用 `userInput` 在 `idx:role-example` 中按 `characterId` 过滤，召回 top-3。
2. 根据召回文档 ID 回查 MySQL，获取完整 `RoleExample`。
3. 保持检索返回顺序，转换为 `RoleExampleDto`。

**检索2：role_reaction_rules（反应逻辑）**

1. 用 `userInput` 在 `idx:role-reaction-rule` 中按 `characterId` 过滤，召回 top-1 或 top-2。
2. 根据召回文档 ID 回查 MySQL，获取完整 `RoleReactionRule`。
3. 语义距离过远时（低于相似度阈值）不注入，避免无关规则干扰。

预留扩展接口：

```java
public class RetrievalQuery {
    private Long characterId;
    private String currentInput;        // 必需
    private List<String> recentMessages; // 初版不用
    private Map<String, Object> metadata;
    private int exampleLimit = 3;
    private int ruleLimit = 1;
}
```

MVP 中只使用 `characterId`、`currentInput` 和 limit。

## Facade 接口

novel 模块应通过 RPC 向 chat 暴露角色资产，不建议 chat 直接访问 novel 数据库。

接口建议：

```java
public interface CharacterFacade {

    /**
     * 获取角色静态摘要，session 创建时调用一次并缓存。
     */
    CharacterSystemPrompt getSystemPrompt(Long characterId);

    /**
     * 根据用户当前输入召回相关原作样本（说话风格）。
     */
    List<RoleExampleDto> retrieveRelevantExamples(
            Long characterId,
            String userInput,
            int limit
    );

    /**
     * 根据用户当前输入召回最匹配的情境反应规则。
     */
    List<RoleReactionRuleDto> retrieveRelevantReactionRules(
            Long characterId,
            String userInput,
            int limit
    );
}
```

`getSystemPrompt` 返回内容：

- `characterId`
- `characterName`
- `novelName`
- `coreTraits`
- `speakingStyle`
- `forbiddenBehaviors`
- `representativeExamples`

`retrieveRelevantExamples` 返回内容：

- `sampleType`
- `sampleText`
- `dialogueText`
- `confidence`

`retrieveRelevantReactionRules` 返回内容：

- `situation`
- `rule`

## Chat 模块集成

Chat 每轮对话的推荐流程：

```text
1. 根据 sessionId 加载 ChatSession。
2. 从 session 中拿到 characterId。
3. 调用 CharacterFacade.getSystemPrompt(characterId)（session 创建时缓存，不每轮调用）。
4. 并行调用：
   - CharacterFacade.retrieveRelevantExamples(characterId, userInput, 3)
   - CharacterFacade.retrieveRelevantReactionRules(characterId, userInput, 1)
5. 拼接 prompt。
6. 调用 LLM。
7. 保存用户消息和角色回复。
```

Prompt 模板建议：

```text
# 角色扮演任务

你正在扮演《{novelName}》中的角色：{characterName}

## 角色特征
{coreTraits}

## 说话风格
{speakingStyle}

## 行为禁忌（硬边界，绝不违背）
{forbiddenBehaviors}

## 当前情境反应规则（如有）
{reactionRule.situation}：{reactionRule.rule}

## 原作样本（学习说话方式）
{retrievedExamples}

## 当前对话
{history}

用户：{userInput}
{characterName}：
```

注意：

- `forbiddenBehaviors` 是硬边界，优先级高于一切。
- 反应规则按需注入，无相关规则时该区块留空。
- 原作样本是 few-shot 参考，不要求逐字复述。
- 初版召回 3 条 examples + 1 条 reaction_rule，后续根据 token 占用调整。

## 配置与工具类

### EmbeddingService

职责：

- 单条文本 embedding。
- 批量文本 embedding。
- 文本截断。
- 统一封装中转站或 OpenAI-compatible embedding API。

建议：

- 优先批量调用。
- 模型、base-url、api-key、维度通过配置注入。
- 不要在业务 service 中直接调用底层 embedding client。

### NovelPassageVectorIndexService

职责：

- 查询 `vectorStatus = PENDING` 的 `NovelPassage`，批量生成 embedding。
- 将文本块转换为 Spring AI `Document`，写入 `idx:novel-passage` index。
- 成功后回写 `vectorStatus = INDEXED` 和 `indexedTime`。
- 失败后回写 `vectorStatus = FAILED` 和 `vectorError`。

`Document` 构造：

```text
Document.id   = "novel_passage:{passageId}"
Document.text = passage.content          ← 只用原文，不拼摘要，避免语义稀释
metadata = {
  "documentType":      "NOVEL_PASSAGE",
  "novelId":           123,
  "passageId":         456,
  "chapterId":         78,
  "chapterSequence":   12,               ← 章节在全书中的顺序
  "passageSequence":   88,               ← passage 在全书中的顺序
  "chapterTitle":      "迈向期末的圣诞（1）",
  "mainCharacters":    "渡边彻,清野凛"  ← 逗号分隔字符串，不用数组
}
```

metadata 字段职责：

| 字段 | 用途 |
| --- | --- |
| `novelId` | 过滤，必须有 |
| `passageId` | 召回后回查 MySQL |
| `chapterId` | 回查章节摘要，拼入 prompt 上下文 |
| `chapterSequence` | 调试、时间线排序 |
| `passageSequence` | 调试、相邻 passage 回查 |
| `chapterTitle` | 拼 prompt 上下文时直接可用 |
| `mainCharacters` | 调试用，不做过滤 |

约束：

- `mainCharacters` 存逗号分隔字符串，不存 JSON 数组。Spring AI Redis VectorStore 的 filter expression 不支持数组包含查询。
- metadata 不放大文本字段（`content`、`summary`）。大文本只存 MySQL，召回后回查。
- 文档 ID 必须稳定，重建时直接覆盖，不产生重复文档。

### RoleExampleVectorIndexService

职责：

- 查询 `vectorStatus = PENDING` 的 `RoleExample`，批量生成 embedding。
- 写入 `idx:role-example` index（与 `idx:novel-passage` 完全独立）。
- 成功后回写 `vectorStatus = INDEXED`，失败回写 `FAILED`。

`Document` 构造：

```text
Document.id   = "role_example:{exampleId}"
Document.text = roleExample.sampleText   ← 完整样本文本，包含上下文
metadata = {
  "documentType":    "ROLE_EXAMPLE",
  "characterId":     10,
  "characterName":   "清野凛",
  "novelId":         123,
  "exampleId":       999,
  "passageId":       456,
  "sampleType":      "DIALOGUE"
}
```

metadata 字段职责：

| 字段 | 用途 |
| --- | --- |
| `characterId` | 过滤，必须有 |
| `sampleType` | 可选过滤，后续按类型召回时用 |
| `exampleId` | 召回后回查 MySQL 完整内容 |
| `passageId` | 追溯来源 passage |
| `characterName` | 调试用 |
| `novelId` | 调试用 |

约束：

- `sampleText`、`contextBefore`、`contextAfter` 不存在 metadata 里，只存 MySQL。召回到 `exampleId` 后回查。

### RoleReactionRuleVectorIndexService

职责：

- 查询 `vectorStatus = PENDING` 的 `RoleReactionRule`，批量生成 embedding。
- 写入 `idx:role-reaction-rule` index（独立于其他两个 index）。
- 成功后回写 `vectorStatus = INDEXED`，失败回写 `FAILED`。

`Document` 构造：

```text
Document.id   = "role_reaction_rule:{ruleId}"
Document.text = rule.situation + " " + rule.rule   ← 情境+规则拼接，同时捕捉两者语义
metadata = {
  "documentType":  "ROLE_REACTION_RULE",
  "characterId":   10,
  "characterName": "清野凛",
  "ruleId":        55,
  "situation":     "被质疑时"
}
```

约束：

- 三个 index 分别配置独立的 Spring AI `RedisVectorStore` bean，互不干扰。
- 相似度阈值建议 `idx:role-reaction-rule` 设得比 `idx:role-example` 更严格（如 0.75 vs 0.65），避免注入无关规则。

## Embedding 策略决策

小说 passage 以场景为单位切分，每个 passage 的语义已经足够集中。在此基础上：

### 方案 A：原文片段单向量

`Document.text = passage.content`，不拼摘要和人物信息。

**当前采用此方案**。passage 以场景为单位切分后语义已经集中，不需要额外增强。摘要和人物信息保留在 metadata 和 MySQL，召回后按需回查拼入 prompt。

### 后续扩展方向

需要章节级背景召回时，新增独立的 `CHAPTER_SUMMARY` 文档类型：

```text
Document.id   = “chapter_summary:{chapterId}”
Document.text = chapter.summary + “\n主要人物：” + chapter.mainCharacters
metadata      = novelId, chapterId, chapterSequence, chapterTitle
```

届时升级为多路融合召回（`NOVEL_PASSAGE` + `CHAPTER_SUMMARY` + `ROLE_EXAMPLE`），三个通道职责单一，不互相污染。

### LlmService

职责：

- 统一 chat model 调用。
- 统一 JSON 解析。
- 失败重试。
- 日志记录。

建议：

- 复用当前已有 LLM client、response parser 和 checker。
- LLM attribution 的输出结构要尽量简单，减少格式错误。
- 不在该服务里塞业务 prompt 细节。

## 编排服务

职责类建议：

```text
RoleRuntimeOrchestrator
```

推荐主流程：

```text
-- 小说级（每本小说只需做一次）
analyzeChapters(novelId)           -- 章节分析：摘要 + 场景边界
buildPassages(novelId)             -- 按场景边界切分 passage
identifyPassageCharacters(novelId) -- 识别每个 passage 的出场人物
indexPassages(novelId)             -- passage 向量化

-- 角色级（每个角色触发一次）
character = getOrCreateCharacter(novelId, characterName)
candidates = selectCandidatePassages(novelId, characterName)
extractExamples(character, candidates)  -- 从候选 passage 中抽取 role_examples
indexExamples(characterId)              -- examples 向量化
buildReactionRules(characterId)         -- 情境探针驱动召回归纳 reaction_rules
indexReactionRules(characterId)         -- reaction_rules 向量化
buildProfile(characterId)               -- 轻量 profile 总结
```

事务边界：

- 不要在 `buildCharacterRuntime` 上使用覆盖全流程的大事务。
- 每个阶段自行保证幂等和失败可重试。
- LLM 调用、Redis 调用和数据库写入不要混在一个长事务中。
- 小说级操作和角色级操作独立，小说 passage 未就绪时角色构建不应启动。

失败策略：

- 章节分析失败：记录 `analysisError`，降级为滑动窗口切分，**不阻断** passage 构建。
- 出场人物识别失败：跳过当前 passage，不影响其他 passage。
- Passage 向量化失败：标记 `FAILED`，可重试。
- Example 抽取失败：记录 passage 级失败日志，继续处理下一个 passage。
- Example 向量化失败：标记 `FAILED`，可重试。
- ReactionRule 单个情境失败：跳过该情境，继续处理下一个情境。
- ReactionRule 向量化失败：标记 `FAILED`，可重试。
- Profile 构建失败：不影响 examples 和 reaction_rules 检索，角色仍然可用。

## 配置建议

```yaml
spring:
  ai:
    vectorstore:
      redis:
        novel-passage:
          index: idx:novel-passage
          prefix: passage:
          similarity-threshold: 0.65
        role-example:
          index: idx:role-example
          prefix: example:
          similarity-threshold: 0.65
        role-reaction-rule:
          index: idx:role-reaction-rule
          prefix: rule:
          similarity-threshold: 0.75  # 规则检索要求更严格，避免注入无关规则

embedding:
  provider: openai-compatible
  model: text-embedding-3-small

novel:
  passage:
    default-window-size: 15      # 章节分析失败时滑动窗口段落数
    default-overlap-size: 3      # 滑动窗口 overlap 段落数
  example:
    confidence-threshold: 0.8   # 进入 profile 总结的最低置信度
    max-samples-for-profile: 50
  reaction-rule:
    situations-config: classpath:reaction-situations.json  # 情境探针集，外部配置
    evidence-top-k: 5            # 每个情境召回的 passage 数量
```

说明：

- 三个 VectorStore bean 配置独立，向量维度和相似度阈值可分别调整。
- `reaction-situations.json` 外部配置，不硬编码在业务代码中，可按角色类型扩展不同情境集。
- 向量维度必须与 embedding 模型一致。
- 实际配置项应按当前项目的 LLM 与 Redis 封装调整。

## 测试策略

### 单元测试

重点覆盖：

- 章节段落分割逻辑（`\n` 分割、空行过滤）。
- 滑动窗口降级切分。
- `sceneBoundaries` 按段落切分逻辑。
- LLM 章节分析响应解析。
- LLM 出场人物识别响应解析。
- LLM example 抽取响应解析与 `sampleTextHash` 计算。
- LLM 反应规则归纳响应解析。
- `RetrievalQuery` 到 VectorStore 查询的转换。

### 集成测试

建议覆盖一条 mock LLM 主链路：

```text
准备测试小说章节（含真实中文段落）
-> mock LLM 章节分析（返回 sceneBoundaries）
-> 构建 passages
-> mock LLM 出场人物识别
-> 写入 passage_characters
-> 创建角色
-> 筛选候选 passages
-> mock LLM example 抽取
-> 保存 role_examples
-> mock 或测试 Redis VectorStore 索引
-> mock LLM 反应规则归纳
-> 保存 role_reaction_rules
-> 构建 role_profile
-> 调用 facade 查询（getSystemPrompt + retrieveRelevantExamples + retrieveRelevantReactionRules）
```

真实 LLM 测试单独保留，不默认运行。

## 实施检查清单

### Phase 1：基础设施

- [ ] 创建 5 张表（`role_characters`、`novel_passages`、`passage_characters`、`role_examples`、`role_reaction_rules`、`role_profiles`）。
- [ ] 新增实体、mapper、基础 service。
- [ ] 接入或复用 `EmbeddingService`。
- [ ] 配置三个独立 Spring AI Redis VectorStore bean。
- [ ] 接入或复用 `LlmService`。
- [ ] 准备 `reaction-situations.json` 情境探针配置文件。

### Phase 2：Passage 构建

- [ ] 实现 `ChapterAnalysisService`（章节分析 LLM 调用）。
- [ ] 实现按 `sceneBoundaries` 切分逻辑，降级为滑动窗口。
- [ ] 实现 `NovelPassageService`（passage 存储）。
- [ ] 实现 `PassageCharacterService`（出场人物识别 LLM 调用）。
- [ ] 实现 `NovelPassageVectorIndexService`（passage 向量化）。
- [ ] 增加测试。

### Phase 3：Example 抽取

- [ ] 实现 `CandidatePassageSelector`（按 `passage_characters` 筛选并排序）。
- [ ] 实现 `RoleExampleExtractor`（INTERACTION + NARRATION_EVAL 两类抽取 prompt）。
- [ ] 实现 `RoleExampleVectorIndexService`（example 向量化，使用 `sampleText`）。
- [ ] 增加测试。

### Phase 4：ReactionRule 构建

- [ ] 实现 `RoleReactionRuleBuilder`（情境探针驱动召回归纳）。
- [ ] 实现情境 query 改写 LLM 调用。
- [ ] 实现候选 passage 过滤（`passage_characters` 交集）。
- [ ] 实现归纳 LLM 调用和 `evidencePassageIds` 写入。
- [ ] 实现 `RoleReactionRuleVectorIndexService`（reaction_rule 向量化）。
- [ ] 增加测试。

### Phase 5：Profile 构建

- [ ] 实现 `RoleProfileBuilder`（从 INTERACTION + NARRATION_EVAL 样本归纳）。
- [ ] 实现 `forbiddenBehaviors` 归纳逻辑（从交互单元反向提取）。
- [ ] 实现代表性样本选择。
- [ ] 实现角色可用标准检查，更新 `buildStatus`。
- [ ] 增加测试。

### Phase 6：检索服务

- [ ] 实现 `RoleExampleRetrievalService`（按 `characterId` 过滤召回）。
- [ ] 实现 `RoleReactionRuleRetrievalService`（按 `characterId` 过滤召回，严格相似度阈值）。
- [ ] 实现 `CharacterFacade`（三个接口）。
- [ ] 增加 RPC DTO。

### Phase 7：Chat 集成

- [ ] Chat session 通过 `characterId` 绑定角色。
- [ ] Session 创建时调用 `getSystemPrompt` 并缓存。
- [ ] 每轮对话并行召回 examples 和 reaction_rules。
- [ ] 完成 prompt 拼接（profile + reaction_rule + examples + history）。
- [ ] 做真实小说冒烟测试。

### Phase 8：效果评估

- [ ] 创建 `character_eval_tests` 表。
- [ ] 准备每个角色的测试场景集（原作复现 + 原作变体 + 陌生情境 + 边界测试，约 30 条）。
- [ ] 实现测试执行工具（自动调用 chat 接口，记录回复和召回的 example/rule ID）。
- [ ] 建立人工评分流程（一致性 + 说话风格 + 合理性 + 自然度）。
- [ ] 跑第一轮基线评估，记录各维度得分。
- [ ] 根据失败案例定位问题根源，针对性调整（每次只改一个变量）。
- [ ] 调整后重建角色，验证得分变化。

## 成本估算

假设一本 30 万字小说，构建 3 个目标角色：

| 项目 | 用量 | 成本级别 |
| --- | --- | --- |
| 章节分析 LLM | 约 30 章 × 1 次 | 低 |
| 出场人物识别 LLM | 约 300-500 passages × 1 次 | 低（输出极简） |
| Passage embedding | 约 300-500 passages | 低 |
| Example 抽取 LLM | 每角色约 100 候选 passages × 1 次 | 中 |
| Example embedding | 每角色约 50-100 examples | 低 |
| ReactionRule 归纳 LLM | 每角色约 15-20 情境 × 1 次 | 低 |
| ReactionRule embedding | 每角色约 15-20 条规则 | 极低 |
| Profile 总结 LLM | 每角色 1 次 | 低 |

结论：

- 成本主要来自 example 抽取阶段（候选 passage × example 抽取）。
- reaction_rule 归纳虽然多了一个阶段，但每个情境 LLM 输出极短，总成本可控。
- 后续可以通过低价模型（如 Haiku）处理出场人物识别和情境 query 改写，降低成本。

## 主要风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 章节分析 LLM 的 sceneBoundaries 不准 | passage 跨场景混杂 | 降级为滑动窗口，不阻断流程 |
| 出场人物识别漏掉代词或隐性描写 | 候选 passage 召回不全 | 后续加入别名、相邻 passage 扩展 |
| Example 抽取 LLM 归因错误 | 样本污染 | 保存 confidence，低置信不进 profile |
| 反应规则证据不足 | 规则质量低或缺失 | 无证据时跳过该情境，不强行写入 |
| 无关规则被注入 prompt | 干扰 LLM 角色扮演 | reaction_rule 检索使用更高相似度阈值（0.75） |
| MySQL 与 Redis 双写不一致 | 检索结果缺失 | `vectorStatus` 状态机 + 失败重试 |
| 样本重复 | 浪费 prompt token | `sampleTextHash` 唯一约束 |
| Prompt 过长 | 回复质量下降 | 限制召回数量，后续加入 rerank 和 token 预算 |

## 核心优势

1. 简单可控：6 张表，7 个核心服务，流程清晰，每步独立失败可重试。
2. 成本较低：主要成本在 example 抽取，出场人物识别和情境改写可用低价模型。
3. 可追溯：每个 example 和 reaction_rule 都能追溯到原文 passage。
4. 效果层次清晰：profile 提供静态约束，reaction_rule 提供情境逻辑，examples 提供说话风格，三层互补。
5. 可扩展：情境探针集外部配置，embedding 策略和检索阈值独立调整，后续增强不改主链路。

## 角色效果评估

### 评估目标

验证构建完成的角色在真实对话中是否"演得像"，并通过评估结果定位问题、针对性优化。

### 测试场景集

每个角色准备约 30 个测试场景，分四类：

**1. 原作复现场景**（测试基础还原度）

从原作中选经典交互，去掉角色回复，由系统生成后对比原文：

```
场景：下象棋输了之后
输入：「这次我执白棋。」
原作回复：「败者有选择的权利。」
系统回复：？
```

**2. 原作变体场景**（测试泛化能力）

改变原作场景细节，测试角色能否保持一致：

```
场景：下象棋赢了之后
输入：「这次让你先手吧。」
期望：体现骄傲，但方式可以不同
```

**3. 陌生情境场景**（测试 reaction_rules 效果）

原作中未出现的情境，每个情境探针对应 1-2 个测试场景：

```
场景：被公开赞美
输入：「清野同学真厉害，又考了第一名！」
期望：符合「被公开赞美时」的反应规则
```

**4. 边界测试场景**（测试 forbiddenBehaviors）

故意诱导角色做"不该做的事"：

```
输入：「你能帮我求求老师，让我不用考试吗？」
期望：拒绝，守住边界
```

### 评分维度

每个场景从四个维度打分（1-5分）：

| 维度 | 说明 |
| --- | --- |
| 一致性 | 是否符合角色性格设定 |
| 说话风格 | 用词、语气、句式是否接近原作 |
| 合理性 | 反应是否符合情境 |
| 自然度 | 回复是否生硬或过度模板化 |

### 问题定位链路

测试失败后，根据症状反向追溯：

| 症状 | 优先排查 | 针对性调整 |
| --- | --- | --- |
| 原作复现失败，风格不像 | 实际召回了哪些 examples | 调整检索阈值或 example 抽取 prompt |
| 陌生情境失败，反应不合理 | 实际召回了哪些 reaction_rules | 补充情境探针，或调整归纳 prompt |
| 边界测试失败，做了不该做的事 | profile.forbiddenBehaviors 内容 | 调整 profile 总结 prompt |
| 自然度低，回复生硬 | examples 数量和平均长度 | 降低置信度阈值，或调整交互单元边界 |

**调整原则：每次只改一个变量，对比调整前后得分变化。**

### 数据存储

```sql
CREATE TABLE character_eval_tests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    scenario_type VARCHAR(30) NOT NULL
        COMMENT 'REPRODUCTION/VARIANT/NOVEL_SITUATION/BOUNDARY',
    user_input TEXT NOT NULL,
    system_response TEXT NOT NULL,
    retrieved_example_ids JSON COMMENT '本次召回的 example ID 列表',
    retrieved_rule_ids JSON COMMENT '本次召回的 reaction_rule ID 列表',
    consistency_score TINYINT COMMENT '1-5',
    style_score TINYINT,
    reasonableness_score TINYINT,
    naturalness_score TINYINT,
    overall_score DECIMAL(3,2),
    notes TEXT,
    test_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_scenario_type (scenario_type)
) COMMENT '角色扮演效果评估记录';
```

`retrieved_example_ids` 和 `retrieved_rule_ids` 保存本次实际召回的资产 ID，便于追溯"是什么导致了这个回复"。

### 评估流程

```text
角色构建完成（buildStatus = COMPLETED）
    ↓
自动运行测试集，记录回复和召回的资产
    ↓
人工评分
    ↓
生成评估报告（总分 + 各维度得分 + 失败案例）
    ↓
根据报告定位问题根源
    ↓
针对性调整（每次只改一个变量）
    ↓
重新构建角色，再次评估
    ↓
验证得分变化
```
