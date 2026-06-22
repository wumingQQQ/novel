# EduAgentX 可借鉴方向临时报告

生成时间：2026-06-22

本文记录对 `D:\git-repo\EduAgentX-BackEnd` 的只读阅读结论，并评估哪些方向适合迁移到当前 `novel-latest` 小说画像项目。本文是临时报告，不作为当前项目正式文档维护。

## 1. 阅读范围

已重点阅读 EduAgentX 的以下部分：

- `README.md`：整体架构、技术栈、单体/微服务双模式说明。
- `workflow/`：AI 课程大纲和题目生成工作流。
- `workflow/context/`：工作流上下文对象。
- `workflow/GenerateQuestionNode/`：RAG 检索、题目生成、解析检查节点。
- `workflow/designPatterns/`：AI 生成门面、题型生成、解析检查与存储模板。
- `rag/`：PDF/DOCX 文档读取、metadata 构建、向量库写入和检索。
- `common/` 与 `exception/`：统一响应、错误码、业务异常、异常处理。
- `utils/SseUtils.java`：SSE 流式输出。
- `aspect/PerformanceMonitorAspect.java`：性能监控切面。

当前小说项目对照阅读的重点包括：

- `PipelineService`：任务阶段串行推进。
- `SceneService`、`LayerService`、`EvidenceService`、`AggregationService`：核心 LLM 调用和阶段处理。
- `ApiResonse`、`GlobalExceptionHandler`：统一响应与异常处理现状。
- `PromptConfig` 与 `domain/llmresponse/`：提示词和 LLM 响应 DTO。

## 2. 可借鉴方向

### 2.1 AI 工作流上下文与节点化阶段推进

EduAgentX 的 AI 任务不是简单 service 串联，而是通过 `QuestionGenerateContext` / `WorkflowContext` 传递状态，并由多个节点依次读写上下文。节点执行后可以根据质检结果决定继续、重试或结束。

当前小说项目的 `PipelineService` 仍是 switch fall-through 形式。它适合早期原型，但后续会难以表达：

- 某阶段部分成功。
- 某批次失败但可从失败点恢复。
- LLM 输出解析失败后局部重试。
- 阶段进度和失败原因展示。
- 人工检查或调试入口。

建议迁移方式：不要直接引入 LangGraph4j，先做轻量版。

- 新增 `PipelineContext`：携带 `jobId`、`novelId`、当前阶段、主角名、目标名、阶段结果、错误信息。
- 新增 `PipelineStep` 接口：统一定义阶段执行入口。
- 新增 `PipelineStepResult`：表达 `SUCCESS`、`RETRYABLE_FAILURE`、`FATAL_FAILURE`、`SKIPPED`。
- 将现有阶段逐步包成 step：章节切分、剧情分层、场景切分、场景分池、证据提取、画像聚合。

收益：

- 阶段控制逻辑从 `PipelineService` 下沉到统一 step 接口。
- 后续可以更自然支持进度、重试和局部恢复。
- 测试可以围绕 step 输入输出，而不是只能跑完整流程。

### 2.2 LLM 输出生成、解析、校验、存储分层

EduAgentX 的 `ParseCheckStorageExecutor` 和各类 `StorageTemplate` 把题目处理拆成了生成、质检、存储。虽然实现质量不完全适合照搬，但这个方向非常适合当前项目。

当前小说项目的典型问题是：

- LLM 返回非法 JSON。
- 锚点不是原文逐字复制。
- evidence 的 quote 和 sceneId 不一致。
- 聚合画像字段可能超长或语义漂移。
- service 中同时做 prompt、解析、校验、entity 转换、保存。

建议迁移方式：

- 为每个 LLM 阶段增加独立 checker/parser：
  - `SceneSplitResponseChecker`
  - `LayerSplitResponseChecker`
  - `EvidenceExtractResponseChecker`
  - `AggregationResponseChecker`
- 每个 checker 只负责业务合法性，不负责数据库保存。
- service 主流程改成：调用 LLM -> parse DTO -> checker 校验 -> mapper/entity 转换 -> 保存。
- 对证据提取增加强校验：`supportingQuotes.size == sceneIds.size`、sceneId 必须来自输入场景、quote 必须能在对应 scene content 中找到。
- 对场景切分增加强校验：sequence 连续、首锚点能匹配、相邻锚点顺序正确。

收益：

- 当前最常见的 LLM 问题可以被集中处理。
- 日志和异常会更明确，不再只有“解析失败”或“等待重试”。
- 后续可以在 checker 层加修复或重试策略，而不是污染业务 service。

### 2.3 SSE / 事件式进度输出

EduAgentX 通过 SSE 把 AI 生成过程推给前端。当前小说项目跑全本小说时耗时长，未来同样需要进度可见性。

建议迁移方向：

- 不照搬 `ThreadLocal<SseEmitter>`。
- 先新增 `PipelineEventPublisher`，当前实现只写结构化日志。
- 事件类型可以先覆盖：
  - `PIPELINE_STARTED`
  - `STAGE_STARTED`
  - `STAGE_COMPLETED`
  - `STAGE_RETRY_WAIT`
  - `LLM_CALL_STARTED`
  - `LLM_CALL_FAILED`
  - `BATCH_COMPLETED`
  - `PIPELINE_COMPLETED`
- 未来需要前端时，再增加 SSE adapter，不改核心 pipeline 逻辑。

收益：

- 现在可以改进日志和调试体验。
- 未来接 UI 进度条时不需要重写任务流程。
- 事件接口天然适合测试和排障。

### 2.4 统一业务异常、错误码和响应封装

EduAgentX 的 `BusinessException`、`ErrorCode`、`ThrowUtils`、`BaseResponse`、`ResultUtils` 比当前项目更完整。

当前小说项目已有 `ApiResonse` 和 `GlobalExceptionHandler`，但存在：

- `ApiResonse` 命名拼写错误。
- 业务异常和系统异常区分不清。
- 多处直接抛 `RuntimeException` 或 `IllegalArgumentException`。
- 全局异常处理直接把异常 message 返回给客户端，可能暴露内部细节。

建议迁移方式：

- 新增 `BusinessException` 和 `ErrorCode`。
- 新增 `ThrowUtils.throwIf(...)`。
- 保留现有 `ApiResonse` 一段时间，避免一次性改接口。
- 后续单独提交把 `ApiResonse` 迁移为 `ApiResponse`。
- `GlobalExceptionHandler` 区分业务异常、文件异常、LLM 异常、未知异常。

收益：

- controller 返回更稳定。
- LLM 阶段失败更容易归类。
- 日志可区分用户输入错误、上游模型错误、本地系统错误。

### 2.5 RAG / 向量召回作为后续增强方向

EduAgentX 的 RAG 模块包含文档读取、切片、metadata、向量存储和语义检索。当前小说项目暂时不建议立即引入 Redis VectorStore，但可以作为后续召回增强方案。

当前项目的召回主要依赖：

- 场景分池结果。
- SQL 根据 layer、pool_type、confidence 召回。

后续如果画像质量不稳定，可以考虑：

- 为 scene 生成 embedding。
- 使用 `pool_type + layer + embedding similarity` 混合召回。
- 对 evidence quote 做更细粒度索引。
- 为目标角色相关查询构造语义检索条件。

建议优先级低于 LLM 校验层和 pipeline 结构调整。

## 3. 不建议迁移内容

### 3.1 微服务拆分

EduAgentX 提供单体和微服务双模式，但当前小说项目仍处于单体迭代期。现在拆微服务会增加部署、配置、事务和调试成本，没有足够收益。

### 3.2 Redis 抢课、HeavyKeeper、RocketMQ

这些是 EduAgentX 抢课业务的高并发组件，和当前小说画像流程不匹配。当前项目更需要可靠的长任务状态、LLM 重试和结果校验，不需要热点 key 检测或抢课队列。

### 3.3 `SpringContextUtil.getBean()`

EduAgentX 的部分工作流节点通过 `SpringContextUtil.getBean()` 获取依赖。当前项目不建议采用这种方式，因为它会隐藏依赖关系，降低测试可读性。

建议仍使用构造器注入，或者在轻量工作流中把 step 作为 Spring Bean 注入列表。

### 3.4 `ThreadLocal<SseEmitter>`

EduAgentX 用 `ThreadLocal<SseEmitter>` 在工作流节点中获取 SSE。这个方案在异步线程和复杂流程中容易出现生命周期不清晰、清理遗漏、串线等问题。

当前项目更适合使用显式的 `PipelineEventPublisher`，由具体 adapter 决定写日志、SSE 或其他输出。

### 3.5 为设计模式而设计模式

EduAgentX 中有一些包名和类名偏设计模式展示，例如 `designPatterns`。当前项目应优先保持朴素，只有在重复逻辑真实出现时再抽象。

## 4. 推荐实施路线

### 短期：优先做 LLM 输出校验层

目标：直接缓解当前频繁出现的 JSON、锚点、证据引用问题。

建议顺序：

1. 为场景切分增加 `SceneSplitResponseChecker`。
2. 为证据提取增加 `EvidenceExtractResponseChecker`。
3. 为分层增加 `LayerSplitResponseChecker`，把当前 `LayerService` 中的校验移出 service。
4. 为聚合增加 `AggregationResponseChecker`，检查关键字段为空、字段长度、列表字段合理性。

### 中期：改造 Pipeline 阶段模型

目标：让任务推进、失败恢复、重试和进度展示更清晰。

建议顺序：

1. 引入 `PipelineContext` 和 `PipelineStepResult`。
2. 先只改 `PipelineService` 内部结构，不改 controller 接口。
3. 每个阶段返回明确状态和失败原因。
4. 保持现有 `JobStage` 表结构不变，避免数据库迁移。

### 中期：新增 Pipeline 事件发布接口

目标：把关键节点日志结构化，为后续 SSE 做准备。

建议顺序：

1. 定义 `PipelineEvent` 和 `PipelineEventPublisher`。
2. 默认实现为 log publisher。
3. 在阶段开始、阶段结束、批次失败、LLM 调用失败处发布事件。
4. 后续再加 SSE adapter。

### 后期：考虑 RAG / 向量召回

目标：提高 evidence 召回质量。

建议前置条件：

- 当前 SQL 召回和分池已经稳定。
- LLM 输出校验层已经能保证 evidence 质量。
- 有明确案例证明 SQL 召回漏掉重要场景。

## 5. 结论

最值得优先应用的是：LLM 输出生成、解析、校验、存储分层。

原因：

- 它直接针对当前项目最常见的问题。
- 改动范围可控，不需要引入新中间件。
- 可以逐阶段落地，先从 `SceneService` 和 `EvidenceService` 做起。
- 会显著提升后续全本小说真实运行的稳定性和可诊断性。

推荐第一步：

新增 `SceneSplitResponseChecker` 和 `EvidenceExtractResponseChecker`，把现有散落在 service 中的校验逻辑集中起来，并为失败原因提供结构化异常和日志。
