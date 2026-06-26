# 小说角色分析系统 - 设计文档

## 1. 项目概述

**目标**：上传小说 TXT，输入主角和目标角色名，系统自动分析目标角色的角色画像与互动画像。

**核心流程**：

```
上传小说 → 创建任务 → 章节切分 → 剧情分层 → 场景切分 → 场景分池 → 证据提取 → 画像聚合 → 画像细节增强
```

**不做**：别名推断、联网搜索、多用户权限、集群部署、复杂可视化。

---

## 2. 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | Spring Boot 3.5.x |
| ORM | MyBatis Plus |
| 数据库 | MySQL |
| 缓存/任务状态 | Redis |
| LLM | DeepSeek API（通过 Spring AI OpenAI starter 对接） |
| Java | 17 |

---

## 3. 项目结构

```
com.wuming.novel
├── config/
│   ├── AsyncConfig.java             # 流程提交线程池 + 阶段内部并发线程池
│   ├── FileUploadProperties.java
│   ├── llm/                         # 多 LLM provider 配置与 ChatClient 工厂
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
│   │   ├── JobStage.java            # 9 个阶段，@EnumValue int code
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
├── llm/
│   ├── checker/                     # LLM 输出业务校验
│   └── parser/                      # LLM JSON 解析与简单格式修复
├── mapper/
├── pipeline/
│   ├── PipelineService.java         # 同步核心流水线，按 PipelineStep 顺序推进
│   ├── PipelineStep.java            # 阶段执行接口
│   ├── *Step.java                   # 各阶段适配器
│   ├── StageRetryExecutor.java      # 阶段失败项重试
│   ├── RedisStageFailureStore.java  # 阶段失败项 Redis 列表
│   └── run/
│       ├── PipelineJobRunner.java   # /process 与 /redo 的异步提交入口
│       ├── JobRunLock.java          # Redis 运行锁，防止重复提交
│       └── JobSubmitStatus.java     # started/running/not_restartable
├── sse/
│   ├── JobProgress.java             # 任务进度聚合对象
│   ├── StageProgress.java           # 单阶段进度
│   ├── JobProgressService.java      # 本地缓存、Redis 持久化、SSE 推送协调
│   └── JobProgressStore.java        # Redis JSON 持久化
├── service/
└── serviceImpl/
    ├── JobService.java              # createJob / advanceStage
    ├── NovelService.java
    ├── ChapterService.java
    ├── LayerService.java
    ├── SceneService.java
    ├── ScenePoolService.java
    ├── RecallService.java           # 按层按池召回已分类场景
    ├── EvidenceService.java
    ├── AggregationService.java
    └── ProfileDetailEnhanceService.java
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
| 7 | PROFILE_DETAIL_ENHANCE | 画像细节增强完成 |
| 8 | COMPLETE | 全部完成 |

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

`handleNovel(jobId)` 是同步核心流程。它读取所有 `PipelineStep`，按 `JobStage.code` 排序后顺序执行：

```
PENDING → CHAPTER_SPLIT → LAYER_SPLIT → SCENE_SPLIT
        → POOL_CLASSIFY → EVIDENCE_EXTRACT → PROFILE_AGGREGATION
        → PROFILE_DETAIL_ENHANCE → COMPLETE
```

- 每个阶段成功后调用 `jobService.advanceStage()` 推进 stage
- 如果 `step.stage().code <= job.stage.code`，说明该阶段已经完成，只同步补齐进度状态并跳过
- 阶段执行由 `StageRetryExecutor` 包装，存在失败项时按阶段重试
- 任意阶段抛出异常时，`JobProgressService.failJob()` 标记任务失败，并向上抛出异常

### 5.2 幂等设计

| 服务 | 策略 |
|------|------|
| ChapterService | 清除旧数据重跑（cheap，全量操作） |
| LayerService | 清除旧数据重跑（cheap，单次 LLM） |
| SceneService | 粒度幂等：失败章节记录在 Redis，重试时优先消费失败项 |
| ScenePoolService | 粒度幂等：失败 scene 记录在 Redis，重试时优先消费失败项 |
| EvidenceService | 粒度幂等：失败的 layer-pool 任务记录在 Redis，重试时优先消费失败项 |
| AggregationService | 清除旧画像重跑（clean by jobId） |
| ProfileDetailEnhanceService | 基于聚合画像和高分场景做细节补充 |

### 5.3 流程提交与运行锁

`/process/{jobId}` 不直接阻塞执行流水线，而是交给 `PipelineJobRunner` 异步提交：

1. `JobRunLock` 使用 Redis `SET NX EX` 获取 `job:{jobId}:running` 锁。
2. 获取锁成功，提交到 `pipelineExecutor` 后立即返回 `started`。
3. 获取锁失败，说明同一 job 已在运行，返回 `running`。
4. 后台执行 `PipelineService.handleNovel(jobId)`，finally 中按 `runId` 比对释放锁，避免误删后续任务的锁。

`/redo/{jobId}` 先读取 `JobProgress` 状态：

| 状态 | 行为 |
|------|------|
| `FAILED` | 尝试获取运行锁并异步重启，成功返回 `started` |
| `RUNNING` | 不重复提交，返回 `running` |
| 其他或无进度 | 不重启，返回 `not_restartable` |

### 5.4 任务进度与 SSE

任务进度由 `JobProgressService` 统一维护：

- 本地 `ConcurrentHashMap` 保存当前实例内的实时进度对象
- 每次进度更新后写入 Redis：`job:{jobId}:progress`，当前 TTL 为 3 天
- 查询进度时优先读本地缓存，本地没有时从 Redis JSON 恢复
- SSE 订阅仍为单实例内存订阅；新订阅会替换同 job 的旧订阅
- 任务完成或失败时关闭对应 SSE 连接

当前没有引入 Redis Pub/Sub，因此多实例部署时只能恢复进度数据，不能跨实例推送 SSE 事件。

### 5.5 阶段内部异步设计

阶段内部的并发任务使用显式 `Executor` 和 `CompletableFuture` 编排，阶段仍然需要等待内部子任务完成后才能推进到下一阶段。

```
pipelineExecutor:      core=2, max=4,  queue=100
sceneSplitExecutor:     core=5, max=10, queue=1000
poolClassifyExecutor:   core=5, max=10, queue=5000
evidenceExtractExecutor: core=5, max=10, queue=500
```

阶段内部线程池拒绝策略为 `CallerRunsPolicy`，流程提交线程池 `pipelineExecutor` 使用 `AbortPolicy`，提交失败时释放运行锁并向上抛出异常。

### 5.6 各阶段详细设计

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
novel.layer.max-layer-size: 30
```

#### 场景切分（SCENE_SPLIT）

逐章异步调 LLM，返回锚点（原文首句），后端用 `indexOf` 定位截取场景内容。
- 锚点定位失败时抛异常，该章节标记失败。

#### 场景分池（POOL_CLASSIFY）

每场景调一次 LLM，返回池分类结果（poolType, confidence），写入 `scene_pool`。
- `protagonistName` / `targetName` 在异步方法内从 Job 实体读取（线程安全）。

#### 证据提取（EVIDENCE_EXTRACT）

召回链路（RecallService）：
```
layer 范围 → chapter.sequence → scenes → scene_pool 过滤 → 按 confidence 降序
```

每批（`batch-size` 条场景）异步调一次 LLM，产出 `Evidence[]` 写库。

#### 画像聚合（PROFILE_AGGREGATION）

清除旧画像 → 按 layer × poolType 遍历证据 → 分批调 LLM → 累积更新画像 DTO → 写入 `character_profiles` + `interaction_profiles`。

LLM 每批接收：当前累积画像（JSON） + 新一批证据（格式化文本），返回更新后的完整画像。

#### 画像细节增强（PROFILE_DETAIL_ENHANCE）

在画像聚合完成后，再按池召回部分高分完整场景，对已有画像做细节补充。该阶段不是重新生成画像，而是在已有画像基础上补齐更具体的行为、语言、互动和关键事件细节。

---

## 6. API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/novel` | 上传 TXT 小说，返回 novelId |
| `POST` | `/novel/createJob` | 创建分析任务，返回 jobId |
| `POST` | `/novel/process/{jobId}` | 异步提交执行/续跑流水线，返回 `started` 或 `running` |
| `POST` | `/novel/redo/{jobId}` | 仅失败任务可异步重启，返回 `started`、`running` 或 `not_restartable` |
| `GET` | `/novel/progress/{jobId}` | 查询任务进度，本地没有时从 Redis 恢复 |
| `GET` | `/novel/progress/{jobId}/stream` | SSE 订阅任务进度 |

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
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PWD}

llm:
  default-provider: deepseek
  temperature: 0.0
  task-temperature:
    scene-split: 0.0
    scene-pool: 0.0
    layer-split: 0.3
    evidence-extract: 0.3
    aggregation: 0.4
    profile-detail-enhance: 0.4
  providers:
    deepseek:
      base-url: https://api.deepseek.com
      api-key: ${DS_API_KEY}
      model: deepseek-v4-flash

novel:
  upload:
    max-file-size: 10MB
    save-path: data/novel
  layer:
    min-chapter-per-layer: 15
    max-chapter-per-layer: 35
    max-layer-size: 30
  analysis:
    batch-size: 10
  profile-enhance:
    threshold: 0.7
    default-top-k: 10
    batch-size: 5
```

---

## 8. 关键设计决策

**LLM 只返回锚点不返回全文**：场景切分阶段节省大量 token，后端本地 substring 更精确。

**按模式提取证据而非单句**：性格/说话风格/互动模式是跨场景的重复特征，多场景引用比单句更有说服力。

**FullPortraitDto 与 Entity 分离**：DTO 不含 id/createTime，`@JsonIgnore` 防止这些字段注入给 LLM，避免干扰生成结果；MyBatis Plus 读写 Entity 不受影响。

**异步线程池 + Redis 运行锁，而非消息队列**：当前只需要避免 HTTP 长时间阻塞和重复提交，使用线程池成本低；Redis 锁解决同 job 重复运行问题。后续如果需要跨进程调度、失败恢复和消费确认，再迁移消息队列。

**PipelineStep 顺序执行实现续跑**：`PipelineService` 以数据库中的 `job.stage` 为准，跳过已完成阶段，从未完成阶段继续执行。

**进度状态与数据库阶段分离**：`jobs.stage` 表示持久化业务阶段；`JobProgress` 表示前端展示用运行状态、阶段状态和子任务计数。两者职责不同，进度可以从 Redis 恢复，但业务续跑仍以数据库阶段为准。

---

## 9. 扩展点

- 多角色同时分析（当前每个 Job 只分析一个 targetName）
- 别名自动推断
- 跨层证据对比 → 角色弧光（成长轨迹）
- 多实例 SSE 推送（Redis Pub/Sub 或消息总线）
- 任务调度迁移到消息队列
- 邮件/通知提醒任务完成
