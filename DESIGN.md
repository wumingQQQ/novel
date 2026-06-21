# 小说角色分析系统 - 设计文档

## 1. 项目概述

**目标**：上传小说 TXT，输入主角和目标角色名，系统自动分析目标角色的角色画像与互动画像。

**核心流程**：

```
上传小说 → 创建任务 → 章节切分 → 剧情分层 → 场景切分 → 场景分池 → 证据提取 → 画像聚合
```

**不做**：别名推断、联网搜索、多用户权限、集群部署、复杂可视化。

---

## 2. 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | Spring Boot 3.5.x |
| ORM | MyBatis Plus |
| 数据库 | MySQL |
| LLM | DeepSeek API（通过 Spring AI OpenAI starter 对接） |
| Java | 17 |

---

## 3. 项目结构

```
com.wuming.novel
├── config/
│   ├── AsyncConfig.java             # 三个线程池：sceneSplit / poolClassify / evidenceExtract
│   ├── FileUploadProperties.java
│   ├── MybatisPlusConfig.java
│   └── PromptConfig.java            # 所有 LLM Prompt 模板
├── controller/
│   └── NovelController.java         # POST /novel, /createJob, /process/{jobId}, /redo/{jobId}
├── domain/
│   ├── dto/
│   │   ├── ApiResonse.java
│   │   ├── CreateJobRequest.java
│   │   ├── FullPortraitDto.java      # LLM 聚合用 DTO（与 Entity 解耦，不含 id/时间字段）
│   │   ├── CharacterProfileDto.java
│   │   └── InteractionProfileDto.java
│   ├── entity/
│   │   ├── Novel.java
│   │   ├── Chapter.java
│   │   ├── Layer.java
│   │   ├── Scene.java
│   │   ├── Job.java                 # 任务（novelId, protagonistName, targetName, stage）
│   │   ├── Evidence.java            # 证据（jobId, layerId, poolType, conclusion, ...）
│   │   ├── CharacterProfile.java    # 角色画像（含 BasicSetting / SpeechStyle 内部类）
│   │   ├── InteractionProfile.java
│   │   └── rel/ScenePool.java       # 场景-池关联（sceneId, jobId, poolType, confidence）
│   ├── enums/
│   │   ├── JobStage.java            # 8 个阶段，@EnumValue int code
│   │   └── PoolType.java
│   └── llmresponse/                 # LLM 返回 record
│       ├── SceneSplitResponse.java
│       ├── LayerSplitResponse.java
│       ├── ScenePoolResponse.java
│       ├── EvidenceExtractResponse.java
│       └── AggregationResponse.java
├── exception/
│   ├── LLMResponseEmptyException.java
│   └── GlobalExceptionHandler.java
├── mapper/
├── service/
└── serviceImpl/
    ├── PipelineService.java         # 流水线总调度（switch fall-through 驱动阶段推进）
    ├── JobService.java              # createJob / advanceStage
    ├── NovelService.java
    ├── ChapterService.java
    ├── LayerService.java
    ├── SceneService.java
    ├── ScenePoolService.java
    ├── RecallService.java           # 按层按池召回已分类场景
    ├── EvidenceService.java
    └── AggregationService.java
```

---

## 4. 数据库设计

见 `resources/db/schema.sql`，以下为关键说明。

### 4.1 表清单

| 表名 | 说明 |
|------|------|
| `novels` | 小说元信息，`file_path` 存储本地路径 |
| `chapters` | 正则切分结果，`unique(novel_id, sequence)` |
| `jobs` | 分析任务，`stage tinyint` 对应 `JobStage.code` |
| `layers` | 叙事分层结果，`unique(novel_id, layer_index)` |
| `scenes` | 语义场景，`unique(chapter_id, sequence)` |
| `scene_pool` | 场景-池关联，`unique(job_id, scene_id, pool_type)` |
| `evidences` | 证据条目，`(job_id, layer_id, pool_type)` 复合索引 |
| `character_profiles` | 角色画像，`unique(job_id)` |
| `interaction_profiles` | 互动画像，`unique(job_id)` |

### 4.2 JobStage 枚举

| code | 名称 | 含义 |
|------|------|------|
| 0 | PENDING | 初始状态 |
| 1 | CHAPTER_SPLIT | 章节切分完成 |
| 2 | LAYER_SPLIT | 剧情分层完成 |
| 3 | SCENE_SPLIT | 场景切分完成 |
| 4 | POOL_CLASSIFY | 场景分池完成 |
| 5 | EVIDENCE_EXTRACT | 证据提取完成 |
| 6 | PROFILE_AGGREGATION | 画像聚合完成 |
| 7 | COMPLETE | 全部完成 |

### 4.3 PoolType 定义

| 池 | 对应画像字段 |
|----|-------------|
| `SETTING` | CharacterProfile.basicSetting |
| `PERSONALITY` | CharacterProfile.personality |
| `SPEECH` | CharacterProfile.speechStyle |
| `INTERACTION` | InteractionProfile（互动方式） |
| `KEY_EVENT` | InteractionProfile.keyEvents |

---

## 5. 流水线设计

### 5.1 PipelineService 调度机制

`handleNovel(jobId)` 使用 switch fall-through，从 job 当前 stage 开始顺序执行：

```
PENDING → CHAPTER_SPLIT → LAYER_SPLIT → SCENE_SPLIT
        → POOL_CLASSIFY → EVIDENCE_EXTRACT → PROFILE_AGGREGATION → COMPLETE
```

- 每个阶段成功后调用 `jobService.advanceStage()` 推进 stage
- 任意阶段返回 false 则立即 return false，不推进 stage
- `/process/{jobId}` 和 `/redo/{jobId}` 均调用同一方法，由 stage guard 决定从哪里续跑

### 5.2 幂等设计

| 服务 | 策略 |
|------|------|
| ChapterService | 清除旧数据重跑（cheap，全量操作） |
| LayerService | 清除旧数据重跑（cheap，单次 LLM） |
| SceneService | 粒度幂等：查已处理 chapter_id，跳过已完成章节 |
| ScenePoolService | 粒度幂等：查 `(novel_id, job_id)` 已完成 scene_id |
| EvidenceService | 粒度幂等：查 `(layer_id, job_id, pool_type)` count > 0 则跳过 |
| AggregationService | 清除旧画像重跑（clean by jobId） |

### 5.3 异步设计

所有异步方法通过 `@Lazy @Autowired private XxxService self` 自注入，确保经过 Spring AOP 代理（否则 `@Async` / `@Transactional` 失效）。

```
sceneSplitExecutor:     core=5, max=10, queue=1000
poolClassifyExecutor:   core=5, max=10, queue=5000
evidenceExtractExecutor: core=5, max=10, queue=500
```

拒绝策略均为 `CallerRunsPolicy`（天然背压）。

### 5.4 各阶段详细设计

#### 章节切分（CHAPTER_SPLIT）

正则匹配，非 LLM：
```
^第[一二三四五六七八九十百千\d]+章[^。！？\n]*[。！？]?\s*$
```
使用 `juniversalchardet` 自动检测文件编码。

#### 剧情分层（LAYER_SPLIT）

将所有章节标题一次性发给 LLM，按叙事弧划分。约束参数可配置：
```yaml
novel.layer.min-chapter-per-layer: 15
novel.layer.max-chapter-per-layer: 35
novel.layer.min-layer-size: 8
novel.layer.max-layer-size: 30
```

#### 场景切分（SCENE_SPLIT）

逐章异步调 LLM，返回锚点（原文首句），后端用 `indexOf` 定位截取场景内容。
- 锚点定位失败时抛异常，该章节标记失败。

#### 场景分池（POOL_CLASSIFY）

每场景调一次 LLM，返回 `ScenePoolResponse[]`（code, confidence），写入 `scene_pool`。
- `protagonistName` / `targetName` 在异步方法内从 Job 实体读取（线程安全）。

#### 证据提取（EVIDENCE_EXTRACT）

召回链路（RecallService）：
```
layer 范围 → chapter.sequence → scenes → scene_pool 过滤 → 按 confidence 降序
```

每批（`batch-size` 条场景）异步调一次 LLM，产出 `Evidence[]` 写库。

#### 画像聚合（PROFILE_AGGREGATION）

清除旧画像 → 按 layer × poolType 遍历证据 → 每 20 条一批调 LLM → 累积更新 `FullPortraitDto` → 写入 `character_profiles` + `interaction_profiles`。

LLM 每批接收：当前累积画像（JSON） + 新一批证据（格式化文本），返回更新后的完整画像。

---

## 6. API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/novel` | 上传 TXT 小说，返回 novelId |
| `POST` | `/novel/createJob` | 创建分析任务，返回 jobId |
| `POST` | `/novel/process/{jobId}` | 执行/续跑流水线 |
| `POST` | `/novel/redo/{jobId}` | 重跑（同 process，stage guard 决定续点） |

统一响应：
```json
{ "code": 200, "data": {}, "message": null }
```

---

## 7. 配置项

```yaml
spring:
  ai:
    openai:
      api-key: ${DS_API_KEY}
      base-url: https://api.deepseek.com
      chat.options.model: deepseek-v3

novel:
  upload:
    max-file-size: 10MB
    save-path: data/novel
  layer:
    min-chapter-per-layer: 15
    max-chapter-per-layer: 35
    min-layer-size: 8
    max-layer-size: 30
  analysis:
    batch-size: 20   # 证据提取每批场景数
```

---

## 8. 关键设计决策

**LLM 只返回锚点不返回全文**：场景切分阶段节省大量 token，后端本地 substring 更精确。

**按模式提取证据而非单句**：性格/说话风格/互动模式是跨场景的重复特征，多场景引用比单句更有说服力。

**FullPortraitDto 与 Entity 分离**：DTO 不含 id/createTime，`@JsonIgnore` 防止这些字段注入给 LLM，避免干扰生成结果；MyBatis Plus 读写 Entity 不受影响。

**异步线程池而非消息队列**：MVP 阶段简单直接，`CallerRunsPolicy` 天然背压，后续可按需迁移。

**stage guard 实现幂等续跑**：每个服务入口检查 `job.stage.code >= 本阶段.code`，已完成则直接返回 true，不重复执行。

---

## 9. 扩展点

- 多角色同时分析（当前每个 Job 只分析一个 targetName）
- 别名自动推断
- 跨层证据对比 → 角色弧光（成长轨迹）
- 任务进度查询接口
- 邮件/通知提醒任务完成
