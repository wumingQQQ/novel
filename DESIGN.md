# 小说角色分析系统 - Java MVP 设计

## 1. 项目概述

**目标**：上传小说，输入主角和目标角色，系统自动分析目标角色画像、互动画像和证据链。

**核心流程**：

```
上传小说 → 创建任务 → 切场景 → 分池分类 → 分层 → Embedding → 提取证据 → 聚合画像
```

**不做**：
- 别名自动推断、联网搜索
- 多用户权限、集群
- PostgreSQL 迁移（SQLite MVP）
- 复杂可视化

---

## 2. 技术栈

| 组件 | 技术选型 |
|------|---------|
| 框架 | Spring Boot 3.x |
| ORM | MyBatis Plus |
| 数据库 | SQLite |
| 向量库 | ChromaDB |
| LLM | DeepSeek API |
| Embedding | bge-m3 |

---

## 3. 项目结构（单体 Spring Boot）

```
novel-analysis/
├── pom.xml
├── src/main/java/com/novel/
│   ├── NovelApplication.java
│   ├── common/
│   │   ├── Result.java           # 统一响应
│   │   ├── BizException.java      # 业务异常
│   │   └── PageQuery.java         # 分页查询
│   ├── controller/
│   │   ├── NovelController.java
│   │   └── AnalysisController.java
│   ├── config/
│   │   └── WebConfig.java
│   ├── service/
│   │   ├── NovelService.java
│   │   ├── AnalysisService.java
│   │   └── pipeline/
│   │       ├── PipelineContext.java
│   │       ├── SceneSplitter.java
│   │       ├── SceneClassifier.java
│   │       ├── LayerService.java
│   │       ├── EmbeddingService.java
│   │       ├── ExtractionService.java
│   │       └── AggregationService.java
│   ├── mapper/
│   │   ├── NovelMapper.java
│   │   ├── ChapterMapper.java
│   │   ├── SceneMapper.java
│   │   ├── LayerMapper.java
│   │   └── ProfileMapper.java
│   └── domain/
│       ├── entity/
│       │   ├── Novel.java
│       │   ├── Chapter.java
│       │   ├── Scene.java
│       │   ├── LayerNode.java
│       │   └── Profile.java
│       ├── dto/
│       │   ├── NovelUploadDTO.java
│       │   ├── AnalysisJobDTO.java
│       │   └── ProfileDTO.java
│       └── enums/
│           ├── StageStatus.java
│           └── PoolType.java
└── src/main/resources/
    ├── application.yml
    └── schema.sql
```

---

## 4. 数据库设计（SQLite）

### 4.1 核心表

```sql
-- 小说表
CREATE TABLE novels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 章节表
CREATE TABLE chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    novel_id INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    sequence INTEGER NOT NULL,
    content TEXT NOT NULL,
    FOREIGN KEY (novel_id) REFERENCES novels(id)
);

-- 分析任务表
CREATE TABLE analysis_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    novel_id INTEGER NOT NULL,
    protagonist_name VARCHAR(100) NOT NULL,
    target_name VARCHAR(100) NOT NULL,
    stage VARCHAR(50) DEFAULT 'PENDING',
    stage_status TEXT DEFAULT '{}',
    failed_stage VARCHAR(50),
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (novel_id) REFERENCES novels(id)
);

-- 场景表（LLM 语义切分）
CREATE TABLE scenes (
    id INTEGER PRIMARY KEY,
    job_id INTEGER NOT NULL,
    chapter_id INTEGER NOT NULL,
    layer_index INTEGER DEFAULT -1,
    sequence INTEGER NOT NULL,
    content TEXT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    start_offset INTEGER DEFAULT 0,
    pools TEXT DEFAULT '[]',           -- 多池模式：[池名数组]
    pool_confidence TEXT DEFAULT '{}', -- 置信度：{POOL: 0.0-1.0}
    target_relevance DECIMAL(3,2) DEFAULT 0,
    uncertain TINYINT DEFAULT 0,
    scene_type VARCHAR(20),
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id)
);

-- 分层节点表
CREATE TABLE layer_nodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    parent_id INTEGER,
    layer_type VARCHAR(10) DEFAULT 'ARC',     -- ARC / BEAT
    layer_index INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,               -- 层名称
    summary VARCHAR(500),                     -- 层概要
    start_chapter_id INTEGER,
    end_chapter_id INTEGER,
    boundary_reason VARCHAR(500),             -- 边界划分理由
    scene_count INTEGER DEFAULT 0,            -- 该层场景数
    target_appearance_rate DECIMAL(3,2),      -- 目标角色出场率
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id)
);

-- 画像快照表
CREATE TABLE phase_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    layer_index INTEGER NOT NULL,
    character_json TEXT,
    interaction_json TEXT,
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id)
);

-- 最终画像表
CREATE TABLE profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL UNIQUE,
    profile_type VARCHAR(20) NOT NULL,  -- character / interaction
    basic_setting TEXT,
    personality TEXT,
    speech_style TEXT,
    relationships TEXT,
    other TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id)
);

-- 证据链表
CREATE TABLE evidence_links (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    profile_id INTEGER NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    scene_id INTEGER NOT NULL,
    confidence VARCHAR(10),
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id),
    FOREIGN KEY (scene_id) REFERENCES scenes(id)
);
```

### 4.2 枚举值

| 字段 | 允许值 |
|------|--------|
| `stage` | `PENDING`, `SCENE_SPLITTING`, `CLASSIFYING`, `LAYERING`, `EMBEDDING`, `EXTRACTING`, `AGGREGATING`, `DONE`, `FAILED` |
| `layer_type` | `ARC`, `BEAT` |
| `scene_type` | `dialogue`, `action`, `internal`, `description`, `mixed` |
| `pools` | `SETTING`, `PERSONALITY`, `SPEECH`, `RELATIONSHIP`, `BEHAVIOR`, `KEY_EVENT` |
| `profile_type` | `character`, `interaction` |

---

## 5. 流水线设计

```
PENDING → SCENE_SPLITTING → CLASSIFYING → LAYERING → EMBEDDING → EXTRACTING → AGGREGATING → DONE
```

### 5.1 各阶段职责

| 阶段 | 职责 | 输出 |
|------|------|------|
| `SCENE_SPLITTING` | 逐章 LLM 切语义场景 | scenes 表 |
| `CLASSIFYING` | 按池分类场景，打分 | 更新 scenes.pools, pool_confidence |
| `LAYERING` | 按叙事弧划分剧情层 | layer_nodes 表 |
| `EMBEDDING` | 场景向量写入 ChromaDB | ChromaDB collection |
| `EXTRACTING` | 按层按池提取证据 | phase_snapshots 表 |
| `AGGREGATING` | 聚合生成最终画像 | profiles + evidence_links |

### 5.2 锚点定位（场景切分）

LLM 返回锚点（原文首句），后端在章节原文中定位截取场景内容。

降级策略：
1. 精确匹配
2. 全角/半角归一匹配
3. 前 20 字符匹配
4. 首句片段匹配
5. 失败跳过，记录失败率

### 5.3 画像维度池

每个场景可属于多个池，按置信度加权，而非互斥。

| 池 | 阈值 | 内容 |
|----|------|------|
| `SETTING` | 0.35 | 基础设定、家庭背景、身份资源 |
| `PERSONALITY` | 0.35 | 性格、价值观、动机、禁忌 |
| `SPEECH` | 0.40 | 语气、称呼、句式 |
| `RELATIONSHIP` | 0.35 | 与主角的距离、信任、冲突 |
| `BEHAVIOR` | 0.35 | 稳定行为模式、技能 |
| `KEY_EVENT` | 0.50 | 阶段转折、关键事件 |

**多池分类示例**：
```json
{
  "pools": ["PERSONALITY", "SPEECH"],
  "pool_confidence": {
    "PERSONALITY": 0.72,
    "SPEECH": 0.65,
    "SETTING": 0.10,
    "RELATIONSHIP": 0.05
  }
}
```

**证据提取规则**：按层聚合时，同一池内所有场景的置信度加权求和，超过阈值才算有效证据。

### 5.4 分层设计（长篇网络小说）

**分层目的**：组织场景召回，按叙事弧提取阶段性角色画像。

**宏层划分依据**：叙事弧 + 剧情阶段

| 层类型 | 说明 | 数量 |
|--------|------|------|
| `ARC` | 剧情弧（大阶段：开局、冲突、高潮、收尾） | 10-30 层 |
| `BEAT` | 叙事节拍（小单元：事件序列） | 按需生成 |

**宏层划分策略**：

```
输入：章节列表 + 每章首尾各 200 字
输出：层边界位置 + 边界理由

边界信号：
- 场景/地点大幅切换
- 时间跳跃（数天后、数年后）
- 目标角色从"出现"变为"消失"又出现
- 情节从 A 事件切换到 B 事件
- 情绪基调明显变化
```

**层数量控制**：

```yaml
novel:
  chapters-per-layer: 50   # 每层约 50 章
  min-layers: 10           # 最少 10 层
  max-layers: 30           # 最多 30 层
```

**场景召回粒度**：

```java
// 召回策略
List<Scene> findByLayerAndPool(jobId, layerIndex, pool);     // 单层单池
List<Scene> findByLayerRange(pool, startLayer, endLayer);    // 多层单池（跨阶段）
List<Scene> findByPool(pool);                                 // 全局单池（角色发展线）
```

**画像聚合策略**：

| 召回范围 | 画像内容 |
|---------|---------|
| 单层 + 单池 | 该阶段该维度的具体表现 |
| 跨层 + 单池 | 该维度的角色成长轨迹 |
| 单层 + 跨池 | 该阶段的角色全貌 |

**分层节点表扩展**：

```sql
CREATE TABLE layer_nodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    parent_id INTEGER,
    layer_type VARCHAR(10) DEFAULT 'ARC',     -- ARC / BEAT
    layer_index INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,               -- 层名称（如"初遇"、"冲突爆发"）
    summary VARCHAR(500),                     -- 层概要
    start_chapter_id INTEGER,
    end_chapter_id INTEGER,
    boundary_reason VARCHAR(500),             -- 边界划分理由
    scene_count INTEGER DEFAULT 0,            -- 该层场景数
    target_appearance_rate DECIMAL(3,2),      -- 目标角色出场率
    FOREIGN KEY (job_id) REFERENCES analysis_jobs(id)
);
```

**枚举值更新**：

| 字段 | 允许值 |
|------|--------|
| `layer_type` | `ARC`, `BEAT` |

### 5.5 LLM Prompt 设计

#### 5.5.1 场景切分（SCENE_SPLITTING）

**目标**：将章节内容切分为语义完整的场景。

**输入**：
- 章节标题、章节序号
- 章节完整内容

**Prompt**：

```
你是一个小说场景分析专家。请将下面的章节切分为语义完整的场景。

【主角名】：{{protagonistName}}
【目标角色】：{{targetName}}

【章节信息】
序号：第 {{chapterSeq}} 章
标题：{{chapterTitle}}

【章节内容】
{{chapterContent}}

【输出要求】
1. 每个场景应该是一个完整的叙事单元（包含对话、动作、心理活动等）
2. 每个场景必须有明确的"锚点"——用原文首句作为定位标记
3. 每个场景需要一句话总结（不超过 50 字）
4. 场景长度建议 200-800 字，太短合并，太长拆分

【输出格式】JSON数组
[
  {
    "anchor": "【锚点】原文首句，精确匹配",
    "summary": "场景总结",
    "sceneType": "dialogue|action|internal|description|mixed",
    "targetAppears": true,
    "protagonistAppears": true
  }
]
```

**输出示例**：

```json
[
  {
    "anchor": "渡边推开教室的门，看见清野凛正坐在窗边看书。",
    "summary": "渡边初遇清野凛，对方冷淡回应",
    "sceneType": "mixed",
    "targetAppears": true,
    "protagonistAppears": true
  },
  {
    "anchor": "\"我喜欢你。\"清野凛突然说道。",
    "summary": "清野凛主动表白，出乎渡边意料",
    "sceneType": "dialogue",
    "targetAppears": true,
    "protagonistAppears": true
  }
]
```

---

#### 5.5.2 池分类（CLASSIFYING）

**目标**：判断每个场景属于哪些画像维度池，并给出置信度。

**输入**：
- 场景内容
- 目标角色名
- 主角名

**Prompt**：

```
你是一个角色画像分析专家。请分析以下场景，判断它揭示了目标角色的哪些维度特征。

【主角名】：{{protagonistName}}
【目标角色】：{{targetName}}

【场景内容】
{{sceneContent}}

【画像维度池定义】

| 池 | 描述 | 典型场景 |
|----|------|---------|
| SETTING | 基础设定、家庭背景、身份资源 | 介绍角色出身、揭示家庭情况 |
| PERSONALITY | 性格、价值观、动机、禁忌 | 角色做出选择、表达态度 |
| SPEECH | 语气、称呼、句式特点 | 角色说话方式、常用词汇 |
| RELATIONSHIP | 与主角的距离、信任、冲突 | 两人互动、情感变化 |
| BEHAVIOR | 稳定行为模式、技能特长 | 角色习惯动作、专业能力 |
| KEY_EVENT | 阶段转折、关键事件 | 高潮情节、关系转折 |

【分析要求】
1. 仔细阅读场景，找出所有相关的维度
2. 置信度 0.0-1.0，越高表示该维度越明确
3. 低于阈值（见下表）的维度可以忽略

【置信度阈值】
- SETTING: 0.35
- PERSONALITY: 0.35
- SPEECH: 0.40
- RELATIONSHIP: 0.35
- BEHAVIOR: 0.35
- KEY_EVENT: 0.50

【输出格式】JSON
{
  "pools": ["主要池名"],
  "poolConfidence": {
    "POOL_NAME": 0.0-1.0,
    ...
  },
  "reasoning": "简要分析理由（50字内）"
}
```

**输出示例**：

```json
{
  "pools": ["PERSONALITY", "SPEECH"],
  "poolConfidence": {
    "PERSONALITY": 0.72,
    "SPEECH": 0.68,
    "RELATIONSHIP": 0.45,
    "SETTING": 0.10,
    "BEHAVIOR": 0.05,
    "KEY_EVENT": 0.00
  },
  "reasoning": "清野凛用'笨蛋'称呼渡边，体现傲娇性格和亲密关系"
}
```

---

#### 5.5.3 叙事分层（LAYERING）

**目标**：将章节序列划分为叙事弧层。

**输入**：
- 章节列表（标题 + 首尾各 200 字）
- 目标角色名

**Prompt**：

```
你是一个小说结构分析专家。请分析章节序列，划分叙事弧层。

【目标角色】：{{targetName}}

【章节信息】
总章节数：{{totalChapters}}
目标层数：{{targetLayerCount}} 层（每层约 {{chaptersPerLayer}} 章）

【章节摘要】
{{chaptersSummary}}

【分层标准】
1. ARC（剧情弧）：反映情节阶段变化
   - 边界信号：场景/地点切换、时间跳跃、情节切换、情绪变化
   - 目标角色从"出现"→"消失"→"再出现"的节点
2. 叙事节奏：高潮后通常有缓冲期
3. 层数控制：10-30 层之间，优先保证叙事完整性而非精确数量

【输出格式】JSON数组
[
  {
    "layerIndex": 0,
    "name": "层名称（如'初遇'、'冲突爆发'）",
    "startChapter": 1,
    "endChapter": 50,
    "summary": "该层叙事概要（100字内）",
    "boundaryReason": "边界划分理由（50字内）",
    "targetAppearance": "high|medium|low|none"
  }
]
```

**输出示例**：

```json
[
  {
    "layerIndex": 0,
    "name": "初遇",
    "startChapter": 1,
    "endChapter": 52,
    "summary": "渡边与清野凛在校园初遇，清野表现出明显的冷淡和排斥",
    "boundaryReason": "清野凛突然转学离开，标志着关系第一阶段结束",
    "targetAppearance": "high"
  },
  {
    "layerIndex": 1,
    "name": "消失与重逢",
    "startChapter": 53,
    "endChapter": 108,
    "summary": "清野凛消失三个月，渡边辗转寻找，最终在图书馆重逢",
    "boundaryReason": "两人关系从排斥转向主动接近，发生质变",
    "targetAppearance": "medium"
  }
]
```

---

#### 5.5.4 证据提取（EXTRACTING）

**目标**：从某层的某池场景中提取角色画像片段。

**输入**：
- 层信息（层名 + 概要 + 章节范围）
- 池类型（SETTING/PERSONALITY/SPEECH/RELATIONSHIP/BEHAVIOR/KEY_EVENT）
- 该层的该池场景列表

**Prompt**：

```
你是一个角色画像分析专家。请根据以下场景，提取目标角色的画像信息。

【目标角色】：{{targetName}}
【池类型】：{{poolType}}
【池描述】：{{poolDescription}}

【层信息】
层名：{{layerName}}
层概要：{{layerSummary}}

【相关场景】（按置信度降序排列）
{{scenesList}}

【提取要求】
1. 从场景中提取具体的画像信息点
2. 每个信息点必须能追溯到具体场景
3. 提取内容要有原文支撑，避免过度推断
4. 输出要简洁，每个画像点不超过 100 字

【输出格式】JSON数组
[
  {
    "content": "画像描述",
    "evidenceScenes": [场景索引列表],
    "confidence": 0.0-1.0
  }
]
```

**输出示例**（PERSONALITY 池）：

```json
[
  {
    "content": "清野凛表面冷淡，实则内心敏感，对渡边的接近表现出傲娇式的抗拒",
    "evidenceScenes": [0, 2, 5],
    "confidence": 0.85
  },
  {
    "content": "清野凛有强烈的自尊心，不愿在人前示弱，但私下会流露真实情感",
    "evidenceScenes": [1, 3, 7],
    "confidence": 0.78
  }
]
```

---

#### 5.5.5 画像聚合（AGGREGATING）

**目标**：将所有层的画像片段聚合成最终的角色画像。

**输入**：
- 目标角色名
- 各池的跨层画像片段
- 角色关系概述（可选）

**Prompt**：

```
你是一个角色画像分析专家。请将所有画像片段聚合成完整的角色画像。

【目标角色】：{{targetName}}

【画像片段】
{{profileFragments}}

【聚合要求】
1. 合并相似片段，去重
2. 识别矛盾点并说明（如角色前期冷漠、后期热情）
3. 提炼核心特征，形成结构化画像
4. 每个画像维度 200-500 字
5. 保持原文证据的引用

【输出格式】JSON
{
  "profileType": "character",
  "basicSetting": "【基础设定】",
  "personality": "【性格特征】",
  "speechStyle": "【说话风格】",
  "relationships": "【关系动态】",
  "keyEvents": "【关键事件】",
  "characterArc": "【角色弧光】"
}
```

**输出示例**：

```json
{
  "profileType": "character",
  "basicSetting": "清野凛，18岁，父亲是外交官，母亲早逝。从小被严格教育，外表出众但内心孤独。",
  "personality": "表面：冷淡、傲娇、不善表达。实际：敏感、渴望被理解、害怕被抛弃。对陌生人保持距离，对亲近的人会流露真实情感。",
  "speechStyle": "称呼：直接叫名字不带敬语。语气：常用反问句、否定句。口头禅：'笨蛋'、'无聊'。说话简洁有力，不喜欢冗长解释。",
  "relationships": "与渡边彻：从最初的排斥到逐渐接受，关系发展经历了'初遇→冲突→误解→和解→信任'五个阶段。",
  "keyEvents": "1. 图书馆初遇（冷淡回应）；2. 突然转学（关系中断）；3. 重逢（傲娇转变）；4. 表白（主动突破）；5. 冲突与和解（深度信任）",
  "characterArc": "从孤独的自我保护者，到学会信任和依赖他人。核心转变：愿意为渡边放下骄傲。"
}
```

---

#### 5.5.6 Prompt 配置

```yaml
llm:
  prompt:
    scene-splitting:
      max-chunk-length: 2000  # 单次调用最大字数
      scene-count-estimate: 5  # 每章平均场景数
    classifying:
      batch-size: 10           # 每次处理场景数
    layering:
      chapters-per-call: 100  # 每次分析章节数
      target-layers: 20       # 目标层数
    extracting:
      scenes-per-pool: 50     # 每池最大场景数
    aggregating:
      include-contradictions: true
```

---

## 6. API 设计

### 6.1 统一响应

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

### 6.2 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/novels/upload` | 上传小说（TXT） |
| `GET` | `/api/novels` | 小说列表 |
| `POST` | `/api/analysis/jobs` | 创建分析任务 |
| `GET` | `/api/analysis/jobs/{id}` | 查询任务进度 |
| `POST` | `/api/analysis/jobs/{id}/retry` | 重试失败任务 |
| `GET` | `/api/analysis/jobs/{id}/profile` | 获取角色画像 |
| `PUT` | `/api/analysis/jobs/{id}/profile` | 编辑画像 |
| `GET` | `/api/analysis/jobs/{id}/evidence` | 查询证据链 |

### 6.3 创建任务请求

```json
{
  "novelId": 1,
  "protagonistName": "渡边彻",
  "targetCharacterName": "清野凛"
}
```

### 6.4 任务进度响应

```json
{
  "success": true,
  "data": {
    "jobId": 1,
    "stage": "EXTRACTING",
    "stageStatuses": {
      "SCENE_SPLITTING": {"status": "DONE", "completed": [1, 2, 3]},
      "CLASSIFYING": {"status": "DONE"},
      "LAYERING": {"status": "DONE"},
      "EMBEDDING": {"status": "DONE"},
      "EXTRACTING": {"status": "RUNNING", "completed": 2, "total": 8}
    }
  }
}
```

---

## 7. 配置

```yaml
# application.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:data/novel_analysis.db
    driver-class-name: org.sqlite.JDBC

chroma:
  persist-dir: data/chroma

llm:
  base-url: https://api.deepseek.com
  api-key: ${LLM_API_KEY}
  model: deepseek-v4-flash

embedding:
  base-url: https://aigc.x-see.cn/v1
  api-key: ${EMBEDDING_API_KEY}
  model: bge-m3

novel:
  storage-path: data/novels
  chapters-per-layer: 50    # 每层约 50 章
  min-layers: 10            # 最少 10 层
  max-layers: 30            # 最多 30 层
```

---

## 8. MVP 验收标准

- [ ] 能上传 TXT 小说，解析章节
- [ ] 能创建分析任务，完成全流程
- [ ] 场景切分锚点定位准确
- [ ] 画像池分类有效
- [ ] 分层结果合理（10-30 个 ARC 层）
- [ ] ChromaDB 向量与 SQLite 同步
- [ ] 证据链可追溯
- [ ] 失败任务可重试

---

## 9. 扩展点（便于后续质量提升）

### 9.1 扩展分层
- 支持自定义层边界
- 微分层（MICRO）自动生成

### 9.2 扩展画像
- 版本历史记录
- 人工编辑覆盖
- 画像置信度校准

### 9.3 扩展证据
- 细粒度池过滤
- ANN 向量检索
- 证据可信度评分

### 9.4 扩展场景切分
- 多角色同时分析
- 别名自动推断
- 视角切换检测

### 9.5 架构升级
- PostgreSQL + pgvector
- Redis 缓存
- 异步任务队列
- 多 worker 并发

---

## 10. 关键设计决策

### 10.1 为什么 SQLite
- 单用户 MVP，不需要并发
- 部署简单，无需数据库服务
- 后续可迁移 PostgreSQL

### 10.2 为什么单体
- MVP 阶段简单直接，模块化是后续复杂度上来后的优化
- 避免多模块带来的构建复杂度
- 后续可按需拆分为多模块

### 10.3 为什么 ChromaDB
- 轻量向量库，易于集成
- 支持 metadata 过滤
- 后续可迁移 pgvector

### 10.4 为什么按叙事弧分层
- 长篇网络小说有明确的剧情节奏
- 分层反映叙事弧（初遇→冲突→高潮→收尾）
- 按层召回场景 → 获取阶段性画像
- 跨层聚合 → 角色发展轨迹
