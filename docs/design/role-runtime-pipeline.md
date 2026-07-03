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

### 2. novel_passages

小说文本块表。它是 passage 向量索引、候选 passage 筛选和 role example 抽取的基础。

```sql
CREATE TABLE novel_passages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT,
    content TEXT NOT NULL COMMENT '原文内容',
    sequence INT NOT NULL COMMENT '全书顺序',
    word_count INT NOT NULL,
    vector_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_novel_seq (novel_id, sequence),
    INDEX idx_vector_status (vector_status)
) COMMENT '小说文本块';
```

设计说明：

- 目标长度建议 600-1000 字符。
- 优先按空行、自然段、句末标点切分。
- 初版不做复杂对话轮次识别，避免切分器过早复杂化。
- Redis VectorStore 文档 ID 使用 `novel_passage:{passageId}`。

### 3. role_examples

角色样本库，是本方案最核心的资产。

```sql
CREATE TABLE role_examples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    passage_id BIGINT NOT NULL,
    sample_type VARCHAR(30) NOT NULL COMMENT 'DIALOGUE/CHARACTER_DESCRIPTION',
    sample_text TEXT NOT NULL COMMENT '完整样本文本',
    dialogue_text TEXT COMMENT '对话原文，仅DIALOGUE类型使用',
    context_before TEXT COMMENT '前文',
    context_after TEXT COMMENT '后文',
    confidence DOUBLE COMMENT '0.0-1.0',
    extract_method VARCHAR(20) COMMENT 'RULE/LLM/RULE_LLM',
    emotional_tag VARCHAR(30) COMMENT '初版可为空',
    situation_tag VARCHAR(50) COMMENT '初版可为空',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_passage (passage_id),
    INDEX idx_type (sample_type),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色样本库';
```

MVP 只保留两类样本：

| 类型 | 说明 |
| --- | --- |
| `DIALOGUE` | 目标角色的原作台词，以及前后上下文 |
| `CHARACTER_DESCRIPTION` | 旁白或他人对目标角色的明确描写、评价、状态刻画 |

设计说明：

- `sampleText` 用于向量检索和 prompt 注入。
- `dialogueText` 只在 `DIALOGUE` 类型中保存角色原话。
- `confidence` 用于控制是否进入 profile 总结和默认召回。
- Redis VectorStore 文档 ID 使用 `role_example:{exampleId}`。

### 4. role_profiles

角色轻量摘要表。它只用于 system prompt 的基础约束，不是主资产。

```sql
CREATE TABLE role_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT UNIQUE NOT NULL,
    character_name VARCHAR(50),
    novel_id BIGINT,
    novel_name VARCHAR(100),
    basic_info JSON COMMENT '基础信息',
    core_traits TEXT COMMENT '3-5个核心性格特质',
    speaking_style TEXT COMMENT '说话风格描述',
    forbidden_behaviors TEXT COMMENT '绝不做的事',
    key_relationships JSON COMMENT '关键关系',
    representative_examples JSON COMMENT '代表性样本ID',
    build_version VARCHAR(20) DEFAULT 'v1.0.0',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_character (character_id)
) COMMENT '角色画像摘要';
```

设计说明：

- `RoleProfile` 构建失败不应阻断角色可用性。
- Chat 的生动性主要来自动态召回的 `RoleExample`。
- `RoleProfile` 只提供身份、性格、说话风格和行为禁忌等基础约束。

## 核心流程实现

### 阶段1：Passage 构建与向量化

职责类建议：

```text
NovelPassageBuilder
```

职责：

1. 根据 `novelId` 查询章节。
2. 按章节顺序切分为 `NovelPassage`。
3. 写入 `novel_passages`，初始 `vectorStatus = PENDING`。
4. 异步或批量写入 Redis VectorStore。
5. 成功后更新 `vectorStatus = INDEXED`，失败后更新 `FAILED` 和 `vectorError`。

关键约束：

- 不要用一个大事务包住整本小说构建和向量化。
- MySQL 写入和 Redis 写入不是同一事务，必须依赖状态字段重试。
- Embedding 优先批量调用，减少成本和网络开销。

### 阶段2：候选 Passage 筛选

职责类建议：

```text
CandidatePassageSelector
```

职责：

1. 先通过 `characterName` 显式命中筛选候选 passage。
2. 从候选 passage 中提取对话候选。
3. 从候选 passage 中提取角色描写候选。

MVP 候选 passage 策略：

```sql
WHERE novel_id = ?
  AND content LIKE CONCAT('%', characterName, '%')
```

后续增强：

- 使用别名命中。
- 加入相邻 passage。
- 使用 passage 向量召回补充隐性相关材料。

候选提取原则：

- 规则阶段追求高召回，不追求最终准确。
- 模糊对话和描写交给 LLM attribution 精判。
- 初版只提取 `DIALOGUE` 与 `CHARACTER_DESCRIPTION` 候选。

### 阶段3：RoleExample 抽取与 LLM Attribution

职责类建议：

```text
RoleExampleExtractor
```

职责：

1. 接收候选 passage、对话候选和描写候选。
2. 调用 LLM 判断候选是否确实属于目标角色。
3. 保存确认后的 `role_examples`。
4. 将新样本标记为 `vectorStatus = PENDING`。
5. 对 role examples 进行向量化。

LLM attribution 只回答三个问题：

```text
1. 该对话是否真的是目标角色说的？
2. 该描写是否真的是在刻画目标角色？
3. 置信度是多少？
```

提示词输出建议：

```json
{
  "dialogues": [
    {"index": 0, "confirmed": true, "confidence": 0.95}
  ],
  "descriptions": [
    {"index": 0, "relevant": true, "confidence": 0.9}
  ]
}
```

约束：

- 不要在该阶段生成完整画像。
- LLM 失败时记录日志并跳过当前 passage，不中断整本小说或整个角色。
- 重跑时要避免无限追加重复样本，建议引入 `sampleTextHash` 或唯一约束。

### 阶段4：RoleProfile 轻量总结

职责类建议：

```text
RoleProfileBuilder
```

职责：

1. 查询高置信度 `RoleExample`。
2. 按类型取样，例如最多 30 条对话、20 条描写。
3. 调用 LLM 生成简短约束摘要。
4. 选择代表性样本 ID。
5. 保存或覆盖 `role_profiles`。

总结内容：

- `basicInfo`
- `coreTraits`
- `speakingStyle`
- `forbiddenBehaviors`
- `keyRelationships`
- `representativeExamples`

约束：

- 只总结有证据的信息。
- 不写传记。
- `forbiddenBehaviors` 要比“总是做什么”更重要，因为它是角色扮演边界。
- 样本不足时可以跳过，不阻断 examples 检索。

### 阶段5：Chat 检索服务

职责类建议：

```text
RoleExampleRetrievalService
```

职责：

1. 根据用户输入生成检索 query。
2. 在 Redis VectorStore 中按 `characterId` 过滤召回 `role_example` 文档。
3. 根据召回文档 ID 查询 MySQL 中的完整 `RoleExample`。
4. 保持向量检索返回顺序。
5. 转换为 `RoleExampleDto` 提供给 chat。

初版检索 query：

```text
currentInput
```

预留扩展：

```java
public class RetrievalQuery {
    private Long characterId;
    private String currentInput;
    private List<String> recentMessages;
    private String intimacyStage;
    private Map<String, Object> metadata;
    private int limit = 3;
}
```

MVP 中只使用 `characterId`、`currentInput` 和 `limit`。

## Facade 接口

novel 模块应通过 RPC 向 chat 暴露角色资产，不建议 chat 直接访问 novel 数据库。

接口建议：

```java
public interface CharacterFacade {

    /**
     * 获取角色 system prompt 所需的轻量摘要和代表样本。
     */
    CharacterSystemPrompt getSystemPrompt(Long characterId);

    /**
     * 根据用户当前输入召回相关原作样本。
     */
    List<RoleExampleDto> retrieveRelevantExamples(
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
- `contextBefore`
- `contextAfter`
- `confidence`

## Chat 模块集成

Chat 每轮对话的推荐流程：

```text
1. 根据 sessionId 加载 ChatSession。
2. 从 session 中拿到 characterId。
3. 调用 CharacterFacade.getSystemPrompt(characterId)。
4. 调用 CharacterFacade.retrieveRelevantExamples(characterId, userInput, 3)。
5. 拼接 system prompt、原作样本、对话历史和当前输入。
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

## 行为禁忌
{forbiddenBehaviors}

## 原作样本
以下是原作中该角色的真实表现，请学习其说话方式和反应边界：

{retrievedExamples}

## 当前对话
{history}

用户：{userInput}
{characterName}：
```

注意：

- 原作样本是 few-shot 参考，不要求逐字复述。
- `forbiddenBehaviors` 是硬边界。
- 初版召回 3 条样本即可，后续根据 token 占用调整。

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

### VectorStoreService

职责：

- 添加文档。
- 删除文档。
- 相似度搜索。
- 构造过滤表达式。

建议：

- 复用当前项目已有 Redis VectorStore 封装。
- 不强行照搬伪代码中的 `Document.embedding(...)`，以项目当前 Spring AI 版本实际 API 为准。
- 文档 ID 必须稳定，便于重建和覆盖。

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
ensurePassagesBuilt(novelId)
indexPassages(novelId)
character = getOrCreateRole(novelId, characterName)
candidates = selectCandidatePassages(novelId, characterName)
extractExamples(character, candidates)
indexExamples(characterId)
buildProfile(characterId)
```

事务边界：

- 不要在 `buildCharacterRuntime` 上使用覆盖全流程的大事务。
- 每个阶段自行保证幂等和失败可重试。
- LLM 调用、Redis 调用和数据库写入不要混在一个长事务中。

失败策略：

- Passage 向量化失败：标记 `FAILED`，可重试。
- Example 抽取失败：记录 passage 级失败日志，继续处理下一个 passage。
- Example 向量化失败：标记 `FAILED`，可重试。
- Profile 构建失败：不影响角色样本召回。

## 配置建议

```yaml
spring:
  ai:
    vectorstore:
      redis:
        index: role-examples
        prefix: doc:

embedding:
  provider: openai-compatible
  model: text-embedding-3-small

novel:
  passage:
    min-length: 600
    max-length: 1000
  example:
    confidence-threshold: 0.8
    max-samples-for-profile: 50
```

说明：

- 实际配置项应按当前项目的 LLM 与 Redis 封装调整。
- 如果 passage 和 role example 共用 Redis VectorStore，需要明确 index、prefix 或 metadata 过滤策略。
- 向量维度必须与 embedding 模型一致。

## 测试策略

### 单元测试

重点覆盖：

- Passage 切分。
- 候选对话提取。
- 候选描写提取。
- LLM attribution 响应解析。
- RoleExample 保存映射。
- RetrievalQuery 到 VectorStore 查询的转换。

### 集成测试

建议覆盖一条 mock LLM 主链路：

```text
准备测试小说章节
-> 构建 passages
-> 创建角色
-> 筛选候选 passages
-> mock LLM 返回 attribution
-> 保存 role_examples
-> mock 或测试 Redis VectorStore 索引
-> 构建 role_profile
-> 调用 facade 查询
```

真实 LLM 测试单独保留，不默认运行。

## 实施检查清单

### Phase 1：基础设施

- [ ] 创建 4 张表。
- [ ] 新增实体、mapper、基础 service。
- [ ] 接入或复用 `EmbeddingService`。
- [ ] 接入或复用 `VectorStoreService`。
- [ ] 接入或复用 `LlmService`。

### Phase 2：Passage 构建

- [ ] 实现 `NovelPassageBuilder`。
- [ ] 实现文本切分逻辑。
- [ ] 实现 passage 向量化。
- [ ] 实现向量化失败状态记录。
- [ ] 增加测试。

### Phase 3：Example 抽取

- [ ] 实现 `CandidatePassageSelector`。
- [ ] 实现规则候选提取。
- [ ] 实现 LLM attribution prompt。
- [ ] 实现 `RoleExampleExtractor`。
- [ ] 实现 example 向量化。
- [ ] 增加测试。

### Phase 4：Profile 构建

- [ ] 实现 `RoleProfileBuilder`。
- [ ] 实现代表性样本选择。
- [ ] 实现 profile 总结 prompt。
- [ ] 增加测试。

### Phase 5：检索服务

- [ ] 实现 `RoleExampleRetrievalService`。
- [ ] 实现按 `characterId` 过滤召回。
- [ ] 实现 `CharacterFacade`。
- [ ] 增加 RPC DTO。

### Phase 6：Chat 集成

- [ ] Chat session 通过 `characterId` 绑定角色。
- [ ] Chat 调用 `CharacterFacade` 获取 prompt 摘要。
- [ ] Chat 每轮对话召回 relevant examples。
- [ ] 完成 prompt 拼接。
- [ ] 做真实小说冒烟测试。

## 成本估算

假设一本 30 万字小说，构建 3 个目标角色：

| 项目 | 用量 | 成本级别 |
| --- | --- | --- |
| Passage embedding | 约 300-500 passages | 低 |
| Example embedding | 每角色约 50 examples | 低 |
| LLM attribution | 每角色约 100 candidate passages | 中 |
| Profile 总结 | 每角色 1 次 | 低 |

结论：

- 成本主要来自 LLM attribution。
- 相比多阶段画像抽取，LLM 调用次数显著减少。
- 后续可以通过候选 passage 过滤、批量 attribution、低价模型降低成本。

## 主要风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 候选 passage 只靠角色名命中 | 漏掉代词和隐性描写 | 后续加入别名、相邻 passage、向量召回 |
| LLM attribution 错误 | 样本污染 | 保存置信度，低置信样本不进入 profile 和默认召回 |
| MySQL 与 Redis 双写不一致 | 检索结果缺失 | 使用 `vectorStatus` 和失败重试 |
| 样本重复 | 浪费 prompt token | 增加 `sampleTextHash` 或唯一约束 |
| RoleProfile 总结失败 | system prompt 约束不足 | 不阻断 examples 检索 |
| Prompt 过长 | 回复质量下降 | 限制召回数量，后续加入 rerank 和 token 预算 |

## 核心优势

1. 简单可控：4 张表，5 个核心服务，流程清晰。
2. 成本较低：主要成本在 embedding 和少量 LLM attribution。
3. 可追溯：每个 example 都能追溯到 passage。
4. 可扩展：后续可以增加样本类型、历史 query、rerank、亲密度等能力。
5. 更贴近聊天目标：直接使用原作样本，避免从抽象画像再重建角色表现。
