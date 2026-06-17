# 小说角色分析系统 - Java MVP 设计

## 1. 项目概述

**目标**：上传小说，输入主角和目标角色，系统自动分析目标角色画像、互动画像和证据链。

**核心流程**：

```
上传小说 → 创建任务 → 切章节 → 切场景 → 分池分类 → 分层 → 提取证据 → 聚合画像
```

**不做**：
- 别名自动推断、联网搜索
- 多用户权限、集群
- 复杂可视化

---

## 2. 技术栈

| 组件 | 技术选型 |
|------|---------|
| 框架 | Spring Boot 3.5.x |
| ORM | MyBatis Plus |
| 数据库 | MySQL（生产） / H2（测试） |
| LLM | DeepSeek API（通过 Spring AI OpenAI starter 对接） |
| Java | 17 |

---

## 3. 项目结构（单体 Spring Boot）

```
com.wuming.novel
├── NovelApplication.java
├── config/
│   ├── FileUploadProperties.java    # 上传配置 (max 10MB, save-path)
│   ├── MybatisPlusConfig.java       # 分页插件 + 自动填充时间
│   ├── AsyncConfig.java             # 线程池配置（sceneSplit, poolClassify）
│   └── PromptConfig.java            # 所有 LLM Prompt 模板
├── controller/
│   └── NovelController.java         # POST /novel 上传, GET /{id} 切章节
├── domain/
│   ├── dto/
│   │   └── ApiResponse.java         # 统一响应 {code, data, message}
│   ├── entity/
│   │   ├── Novel.java               # 小说 (name, filePath, createTime)
│   │   ├── Chapter.java             # 章节 (novelId, title, sequence, content)
│   │   ├── Scene.java               # 场景 (novelId, chapterId, sequence, content)
│   │   ├── Job.java                 # 分析任务 (protagonistName, targetName, stage)
│   │   ├── Layer.java               # 叙事层 (layerIndex, layerName, start/endChapterSequence)
│   │   ├── CharacterProfile.java    # 角色画像 (basicSetting, personality, speechStyle)
│   │   ├── InteractionProfile.java  # 互动画像 (tone, keyEvents, conservationSamples)
│   │   └── rel/
│   │       └── ScenePool.java       # 场景-池关联 (sceneId, poolType, confidence)
│   ├── enums/
│   │   ├── JobStage.java            # PENDING 等阶段枚举
│   │   └── PoolType.java            # SETTING/PERSONALITY/SPEECH/INTERACTION/KEY_EVENT
│   └── llmresponse/                 # LLM 输出结构的 record
│       ├── SceneSplitResponse.java
│       ├── LayerSplitResponse.java
│       ├── ScenePoolResponse.java
│       └── ExtractEvidenceResponse.java
├── exception/
│   ├── FileTooLargeException.java
│   ├── FileNotSupportException.java
│   └── GlobalExceptionHandler.java
├── mapper/                          # 7 个 MyBatis Plus BaseMapper
├── service/                         # 7 个 IService 接口
└── serviceImpl/
    ├── NovelService.java            # 上传 + 文件校验 + 编码检测
    ├── ChapterService.java          # 按"第X章"正则切分章节
    ├── SceneService.java            # LLM 切场景（@Async 线程池）
    ├── LayerService.java            # LLM 分层（@Transactional）
    ├── ScenePoolService.java        # LLM 池分类（@Async 线程池）
    ├── CharacterProfileService.java
    ├── InteractionProfileService.java
    └── JobService.java
```

### 测试结构

```
src/test/
├── java/.../
│   ├── NovelApplicationTests.java
│   ├── serviceImpl/
│   │   ├── NovelServiceTest.java
│   │   ├── ChapterServiceTest.java
│   │   ├── ScenePoolServiceTest.java
│   │   └── ...
└── resources/
    ├── application-test.yml      # H2 内存库配置
    └── schema.sql                # 测试用建表语句
```

---

## 4. 数据库设计（MySQL 生产 / H2 测试）

### 4.1 核心表

```sql
-- 小说表
CREATE TABLE novels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 章节表
CREATE TABLE chapters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    sequence INT NOT NULL,
    title VARCHAR(255),
    content LONGTEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 场景表（LLM 语义切分结果）
CREATE TABLE scenes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    novel_id INT NOT NULL,
    chapter_id INT NOT NULL,
    sequence INT NOT NULL,
    content TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 分析任务表
CREATE TABLE jobs (
    job_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    protagonist_name VARCHAR(100) NOT NULL,
    target_name VARCHAR(100) NOT NULL,
    stage VARCHAR(20) DEFAULT 'PENDING',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 分层表
CREATE TABLE layers (
    id INT PRIMARY KEY AUTO_INCREMENT,
    layer_index INT NOT NULL,
    layer_name VARCHAR(255) NOT NULL,
    novel_id INT NOT NULL,
    start_chapter_sequence INT NOT NULL,
    end_chapter_sequence INT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 场景-池关联表
CREATE TABLE scene_pool (
    id INT PRIMARY KEY AUTO_INCREMENT,
    scene_id INT NOT NULL,
    pool_type VARCHAR(20) NOT NULL,
    confidence DECIMAL(3,2) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 角色画像表（使用 JacksonTypeHandler 存储 JSON 字段）
CREATE TABLE profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    basic_setting JSON,
    personality TEXT,
    speech_style JSON,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 互动画像表
CREATE TABLE interaction_profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    character_id INT NOT NULL,
    protagonist_name VARCHAR(100),
    tone VARCHAR(255),
    key_events JSON,
    conservation_samples JSON,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 证据条目表
CREATE TABLE evidence_items (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_id INT NOT NULL,
    layer_id INT,
    pool_type VARCHAR(20) NOT NULL,
    conclusion TEXT NOT NULL,
    supporting_quotes JSON NOT NULL,
    scene_indices JSON NOT NULL,
    confidence DECIMAL(3,2) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 4.2 枚举值

| 字段 | 允许值 |
|------|--------|
| `stage` | `PENDING`, `SCENE_SPLITTING`, `CLASSIFYING`, `LAYERING`, `EXTRACTING`, `AGGREGATING`, `DONE`, `FAILED` |
| `pool_type` | `SETTING`, `PERSONALITY`, `SPEECH`, `INTERACTION`, `KEY_EVENT` |
| `profile_type` | `character`, `interaction` |

### 4.3 PoolType 定义

| 池 | 含义 | 阈值 | 对应画像实体字段 |
|----|------|------|-----------------|
| `SETTING` | 基础设定、家庭背景 | 0.35 | CharacterProfile.basicSetting |
| `PERSONALITY` | 性格、价值观、动机 | 0.35 | CharacterProfile.personality |
| `SPEECH` | 语气、称呼、口癖、句式 | 0.40 | CharacterProfile.speechStyle |
| `INTERACTION` | 与主角的互动方式、关系变化 | 0.35 | InteractionProfile |
| `KEY_EVENT` | 转折性关键事件 | 0.50 | InteractionProfile.keyEvents |

---

## 5. 流水线设计

### 5.0 整体流程

```
PENDING → SCENE_SPLITTING → CLASSIFYING → LAYERING → EXTRACTING → AGGREGATING → DONE
```

### 5.1 各阶段职责

| 阶段 | 职责 | 输出 |
|------|------|------|
| `CHAPTER_SPLITTING` | 按"第X章"正则切分小说文本 | chapters 表 |
| `SCENE_SPLITTING` | 逐章 LLM 切语义场景 | scenes 表 |
| `CLASSIFYING` | 逐场景 LLM 分类到画像维度池 | scene_pool 表 |
| `LAYERING` | LLM 按章节标题划分叙事层 | layers 表 |
| `EXTRACTING` | 按层按池召回场景，LLM 提取证据 | evidence_items 表 |
| `AGGREGATING` | 聚合证据生成最终画像 | profiles / interaction_profiles |

### 5.2 阶段实现状态

| 阶段 | 实现 | 异步 |
|------|------|------|
| 小说上传 + 章节切分 | ✅ 完成 | 否 |
| 场景切分 | ✅ 完成 | ✅ @Async sceneSplitExecutor (5 线程) |
| 池分类 | ✅ 完成 | ✅ @Async poolClassifyExecutor (5 线程) |
| 叙事分层 | ✅ 完成 | 否（单次 LLM 调用） |
| 证据提取 | 📝 设计中 | 待定 |
| 画像聚合 | ❌ 未开始 | 待定 |
| 任务调度 | ❌ 未开始 | 待定 |

### 5.3 异步线程池配置

```yaml
# AsyncConfig.java
sceneSplitExecutor: core=5, max=10, queue=1000    # 场景切分
poolClassifyExecutor: core=5, max=10, queue=5000  # 池分类（场景数更多）
```

- 使用 `CallerRunsPolicy` 作为拒绝策略（满了则主线程执行，天然背压）
- `@EnableAsync` 在 `AsyncConfig` 上开启
- 异步方法内部独立写入数据库，不依赖事务传播

### 5.4 各阶段详细设计

#### 5.4.1 章节切分（CHAPTER_SPLITTING）

**方法**：正则匹配，非 LLM 调用。

```java
Pattern.compile("^第[一二三四五六七八九十百千\\d]+章[^。！？\n]*[。！？]?\\s*$", Pattern.MULTILINE)
```

- 使用 `juniversalchardet` 自动检测文件编码
- 章节标题提取首行，正文去除标题行
- 幂等设计：不存在章节标记时返回空列表

#### 5.4.2 场景切分（SCENE_SPLITTING）

**方式**：逐章调用 DeepSeek，每章调一次 LLM。

**LLM 返回**：仅返回锚点（原文首句），不返回场景全文。

```json
[
  { "sequence": 1, "anchor": "渡边推开教室的门..." },
  { "sequence": 2, "anchor": "\"我喜欢你。\"清野凛突然说道。" }
]
```

**后处理**：根据锚点在原文中定位，substring 切出场景内容。使用 5 级降级策略：
1. 精确匹配
2. 全角/半角归一匹配
3. 前 20 字符匹配
4. 首句片段匹配
5. 失败跳过，整章作为一个场景降级

**异步**：`@Async("sceneSplitExecutor")`，500 章约 1.5 分钟（5 线程并行）。

#### 5.4.3 池分类（CLASSIFYING）

**方式**：每场景调一次 LLM，判断属于哪些画像维度池。

```json
{
  "poolConfidence": {
    "SETTING": 0.05,
    "PERSONALITY": 0.72,
    "SPEECH": 0.68,
    "INTERACTION": 0.65,
    "KEY_EVENT": 0.00
  }
}
```

**后处理**：Java 端根据 `PoolType.getThreshold()` 过滤低置信度池，写入 `scene_pool` 表。

**异步**：`@Async("poolClassifyExecutor")`，每场景一次 LLM 调用。

**幂等校验**：`divideSceneIntoPool()` 入口检查是否有已有分类结果。

#### 5.4.4 叙事分层（LAYERING）

**方式**：将所有章节标题一次性发给 LLM，由 LLM 按叙事弧划分。

**输入**：总章节数、章节标题列表（不传正文，节省 token）。

**约束参数**（可配置）：

```yaml
novel:
  layer:
    min-chapter-per-layer: 15    # 每层最少章节数
    max-chapter-per-layer: 35    # 每层最大章节数
    min-layer-size: 8            # 最少层数
    max-layer-size: 30           # 最大层数
```

**降级策略**：LLM 返回空时，按等间隔切分（总章数 ÷ 目标层数）。

**同步**：单次 LLM 调用，无需异步。

#### 5.4.5 证据提取（EXTRACTING）

**核心思路**：按层按池召回场景，分批注入 LLM，从**多个场景中归纳模式**，而非提取单句。

> 性格、说话风格、互动模式——这些都是从多个场景中反复出现的模式，不是单个句子能证明的。

**召回逻辑**：

```
1. 遍历所有 layers
2. 对每层，遍历所有 PoolType
3. 通过 chapter.sequence 桥接 layer 范围 → 查 scenes
4. 通过 scene_pool 过滤出属于该池的场景
5. 按置信度降序排列
```

**分批注入**：每批 15-25 个场景，超出则拆成多批分别调 LLM。

**批间合并**：不需要合并。每批产生独立的证据条目，直接追加写入 `evidence_items` 表，后续聚合阶段统一处理。

**LLM 返回结构**：

```json
[
  {
    "conclusion": "清野凛表面冷淡实则内心敏感，对渡边的关心用傲娇的方式表达",
    "supportingQuotes": [
      "场景3：'我才没有担心你呢'清野凛别过头去，耳朵却微微发红",
      "场景8：她嘴上说着'笨蛋'，却还是把伞塞到了渡边手里"
    ],
    "sceneIndices": [3, 8],
    "confidence": 0.88
  }
]
```

**证据条目表**（`evidence_items`）：

| 字段 | 说明 |
|------|------|
| job_id | 所属分析任务 |
| layer_id | 所属层（可为空，跨层证据） |
| pool_type | 所属池 |
| conclusion | 画像结论，如"表面冷淡内心敏感" |
| supporting_quotes | 支撑结论的多条原文引用（JSON 数组） |
| scene_indices | 涉及场景在本次输入中的索引（JSON 数组） |
| confidence | 置信度 |

**单个证据的特征**：
- 一个结论对应 2-3 条来自不同场景的引用
- 每条引用标注来源场景
- 多个批次产生多条独立证据，不做跨批合并

### 5.5 锚点定位（场景切分）

LLM 返回锚点（原文首句），后端在章节原文中定位截取场景内容。

降级策略：
1. 精确匹配
2. 全角/半角归一匹配
3. 前 20 字符匹配
4. 首句片段匹配
5. 失败跳过，记录失败率

### 5.6 分层设计（长篇网络小说）

**分层目的**：组织场景召回，按叙事弧提取阶段性角色画像。

**宏层划分依据**：叙事弧 + 剧情阶段

| 层类型 | 说明 | 数量 |
|--------|------|------|
| `ARC` | 剧情弧（大阶段） | 8-30 层 |

**层边界判定依据**：
- 场景/地点大幅切换
- 时间跳跃（数天后、数年后）
- 目标角色从"出现"变为"消失"又出现
- 情节从 A 事件切换到 B 事件
- 情绪基调明显变化

### 5.7 LLM Prompt 设计

#### 5.7.1 场景切分（SCENE_SPLITTING）

**PromptConfig**：`SCENE_SPLIT_PROMPT`

**输入**：章节标题、章节完整内容

**变量**：`{chapterTitle}`, `{chapterContent}`

**返回**：`SceneSplitResponse[]`（sequence, anchor）

#### 5.7.2 池分类（CLASSIFYING）

**PromptConfig**：`POOL_CLASSIFY_PROMPT`

**输入**：主角名、目标角色名、场景内容

**变量**：`{protagonistName}`, `{targetName}`, `{sceneContent}`

**返回**：`ScenePoolResponse[]`（code, confidence）

#### 5.7.3 叙事分层（LAYERING）

**PromptConfig**：`LAYER_SPLIT_PROMPT`

**输入**：总章节数、章节标题列表、分层约束参数

**变量**：`{novelName}`, `{totalChapters}`, `{minChaptersPerLayer}`, `{maxChaptersPerLayer}`, `{minLayers}`, `{maxLayers}`, `{chapterList}`

**返回**：`LayerSplitResponse[]`（layerIndex, layerName, startChapter, endChapter）

#### 5.7.4 证据提取（EXTRACTING）

**Prompt**：

```
你是一个角色画像分析专家。请根据以下场景，归纳目标角色的画像信息。

【目标角色】：{{targetName}}
【池类型】：{{poolType}} — {{poolDescription}}
【层信息】：{{layerName}} — {{layerSummary}}

【相关场景】（共 N 个）
{{scenes}}

【提取要求】
1. 仔细阅读所有场景，找出目标角色在该维度的**模式化特征**
2. 不要只关注单句，要寻找**跨场景重复出现**的行为、态度、表达方式
3. 每个结论必须有 2-3 条来自不同场景的原文引用支撑
4. 如果场景之间表现出矛盾特征（前期 vs 后期），分别说明

【输出格式】JSON数组
[
  {
    "conclusion": "画像结论，100字内",
    "supportingQuotes": ["引用1（标注场景索引）", "引用2（标注场景索引）"],
    "sceneIndices": [3, 8],
    "confidence": 0.0-1.0
  }
]
```

**返回**：`ExtractEvidenceResponse[]`（conclusion, supportingQuotes, sceneIndices, confidence）

---

## 6. API 设计

### 6.1 统一响应

```json
{
  "code": 200,
  "data": {},
  "message": null
}
```

### 6.2 接口列表

| 方法 | 路径 | 说明 | 实现状态 |
|------|------|------|---------|
| `POST` | `/novel` | 上传小说（TXT） | ✅ |
| `GET` | `/novel/{id}` | 切分章节 | ✅ |
| `POST` | `/api/analysis/jobs` | 创建分析任务 | ❌ |
| `GET` | `/api/analysis/jobs/{id}` | 查询任务进度 | ❌ |
| `GET` | `/api/analysis/jobs/{id}/profile` | 获取角色画像 | ❌ |

### 6.3 创建任务请求

```json
{
  "novelId": 1,
  "protagonistName": "渡边彻",
  "targetCharacterName": "清野凛"
}
```

---

## 7. 配置

### 7.1 生产配置（application.yml）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/novel?useSSL=false&serverTimezone=UTC
    username: root
    password: ${MYSQL_PASSWORD}
  ai:
    openai:
      api-key: ${DS_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-v4-flash

novel:
  upload:
    max-file-size: 10MB
    save-path: data/novel
  layer:
    min-chapter-per-layer: 15
    max-chapter-per-layer: 35
    min-layer-size: 8
    max-layer-size: 30
```

### 7.2 测试配置（application-test.yml）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:novel_test;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:test/schema.sql

novel:
  upload:
    save-path: data/test
```

---

## 8. MVP 验收标准

- [x] 能上传 TXT 小说，解析章节
- [x] 场景切分锚点定位准确
- [x] 画像池分类有效
- [x] 分层结果合理（8-30 个 ARC 层）
- [ ] 证据链可追溯（evidence_items 表）
- [ ] 画像聚合输出
- [ ] 创建分析任务 Job 全流程调度
- [ ] 失败任务可重试

---

## 9. 扩展点（便于后续质量提升）

### 9.1 证据提取优化
- 多角色同时分析
- 别名自动推断
- 跨层证据对比 → 角色弧光

### 9.2 分层优化
- 支持自定义层边界
- 微分层（BEAT）自动生成

### 9.3 画像优化
- 版本历史记录
- 人工编辑覆盖
- 画像置信度校准

### 9.4 架构升级
- H2 → MySQL（生产已完成）
- 异步任务队列 + 多 worker 并发
- Redis 缓存

---

## 10. 关键设计决策

### 10.1 为什么异步线程池而不是消息队列
- MVP 阶段简单直接
- 线程池 + `CallerRunsPolicy` 天然背压
- 后续可按需迁移到消息队列

### 10.2 为什么 LLM 只返回锚点不返回全文
- 节省 token 成本（场景切分阶段）
- 后端本地切分更精确可控

### 10.3 为什么证据提取按模式而非单句
- 性格/说话风格/互动模式是跨场景的重复特征
- 多场景引用比单句更有说服力
- 符合"证据链"的语义

### 10.4 为什么 MySQL
- 结构化数据存储成熟稳定
- 支持事务和索引
- 便于后续扩展

### 10.5 为什么按叙事弧分层
- 长篇网络小说有明确的剧情节奏
- 分层反映叙事弧（初遇→冲突→高潮→收尾）
- 按层召回场景 → 获取阶段性画像
- 跨层聚合 → 角色发展轨迹
