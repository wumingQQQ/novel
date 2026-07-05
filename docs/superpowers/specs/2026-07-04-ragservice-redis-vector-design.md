# ragservice Redis Vector RAG 服务设计

## 背景

当前 `chat` 和 `novel` 模块都直接持有 Spring AI、Redis VectorStore、embedding 与 RAG 配置。随着新角色运行时链路推进，`novel` 需要写入 `novel_passage`、`role_example`、`role_reaction_rule` 三类向量数据，`chat` 需要按 `characterId` 动态召回原作样本和情境反应规则。

为避免业务模块继续引入向量库依赖，新增独立 `ragservice` 模块统一提供 RAG 索引和检索能力。第一版只支持 Redis VectorStore，不做 ES 或多后端切换。

## 目标

- 新增 `rag-api` 契约模块，只包含 Dubbo facade、DTO 和 enum。
- 新增 `ragservice` Spring Boot 服务模块，集中持有 Spring AI Redis VectorStore、embedding、rerank 和 RAG 配置。
- `novel` 通过 Dubbo 调用 `ragservice` 动态创建 Redis Search 向量索引、写入和删除向量文档。
- `chat` 通过 Dubbo 调用 `ragservice` 检索 `role_example` 和 `role_reaction_rule`。
- 对外接口不暴露 Spring AI 类型；索引名、key prefix 和 metadata schema 由调用方通过 `createIndex` 显式声明。

## 非目标

- 不迁移到 ES。
- 不设计多向量库 provider SPI。
- 不让 `ragservice` 回查 `novel` 或 `chat` 的业务数据库。
- 不在第一版兼容旧 `scene` RAG；本设计直接面向新角色运行时链路。
- 不在 RAG API 中承载 LLM 聊天生成能力。

## 模块结构

```text
rag-api
  src/main/java/com/wuming/api/rag/
    RagIndexFacade.java
    RoleRuntimeRagFacade.java
    dto/
    enums/

ragservice
  src/main/java/com/wuming/rag/
    RagServiceApplication.java
    config/
    integration/rpc/
    index/
    search/
    vector/redis/
    rerank/
```

依赖方向：

```text
novel   -> rag-api
chat    -> rag-api
ragservice -> rag-api + common + Spring AI + Redis VectorStore + Dubbo
```

`chat` 和 `novel` 不再直接依赖 `spring-ai-redis-store`，也不再定义 Redis VectorStore bean。

## 动态索引模型

`ragservice` 不预先定义固定向量库，也不在启动时创建固定的 `VectorStore` bean。调用方必须先通过 `createIndex` 声明索引名、Redis key 前缀、向量维度和可过滤 metadata 字段。`ragservice` 内部使用 Redis Search 底层命令创建索引，并将索引定义保存到 Redis，供后续 upsert/delete/search 使用。

## Dubbo 接口

### RagIndexFacade

索引生命周期与文档写入接口，主要由 `novel` 调用。

```java
public interface RagIndexFacade {
    IndexResult createIndex(CreateIndexRequest request);

    UpsertResult upsertDocuments(UpsertDocumentsRequest request);

    DeleteResult deleteDocuments(DeleteDocumentsRequest request);
}
```

`createIndex` 必须幂等：

| 场景 | 返回状态 |
| --- | --- |
| 索引不存在，创建成功 | `CREATED` |
| 索引已存在 | `EXISTS` |
| 请求非法 | `VALIDATION_ERROR` |
| Redis VectorStore 创建失败 | `FAILED` |

### RoleRuntimeRagFacade

角色运行时检索接口，主要由 `chat` 调用，也可由 `novel` 构建 reaction rule 时调用。

```java
public interface RoleRuntimeRagFacade {
    SearchResult searchPassages(PassageSearchRequest request);

    SearchResult searchRoleExamples(RoleExampleSearchRequest request);

    SearchResult searchReactionRules(ReactionRuleSearchRequest request);
}
```

三类检索职责：

| 接口 | 使用方 | 过滤字段 | 默认数量 | 目的 |
| --- | --- | --- | --- | --- |
| `searchPassages` | `novel` | `novelId` | 5 | 情境探针召回原文 passage |
| `searchRoleExamples` | `chat` | `characterId` | 3 | 每轮对话召回 few-shot 原作样本 |
| `searchReactionRules` | `chat` | `characterId` | 1 | 每轮对话召回情境反应规则 |

## DTO 设计

### 索引请求

```java
public class CreateIndexRequest {
    private String indexName;
    private String keyPrefix;
    private Integer vectorDimension;
    private List<IndexMetadataFieldDto> metadataFields;
}

public class RagDocumentDto {
    private String documentId;
    private String text;
    private Map<String, Object> metadata;
}

public class UpsertDocumentsRequest {
    private String indexName;
    private List<RagDocumentDto> documents;
}

public class DeleteDocumentsRequest {
    private String indexName;
    private List<String> documentIds;
}
```

`indexName` 是对外索引标识，也是 Redis Search 物理索引名。`keyPrefix` 只在创建索引时传入，后续请求通过 `indexName` 加载已保存的索引定义。`documentId` 由调用方按业务 ID 稳定生成，`ragservice` 不生成业务 ID。

### 检索请求

```java
public class PassageSearchRequest {
    private String indexName;
    private Long novelId;
    private String query;
    private Integer topK;
}

public class RoleExampleSearchRequest {
    private String indexName;
    private Long characterId;
    private String query;
    private Integer topK;
}

public class ReactionRuleSearchRequest {
    private String indexName;
    private Long characterId;
    private String query;
    private Integer topK;
}
```

`topK` 为空时由 `ragservice` 使用配置默认值。

### 返回结构

```java
public class SearchResult {
    private boolean success;
    private String code;
    private String message;
    private List<RagHitDto> hits;
}

public class RagHitDto {
    private String documentId;
    private double score;
    private String text;
    private Map<String, Object> metadata;
}
```

返回 `text` 是向量文档文本，便于 `chat` 直接拼 prompt。业务方如果需要完整实体，以 `documentId` 或 metadata 中的业务 ID 回查自己的服务。

## 推荐索引约定

`ragservice` 不硬编码以下三类索引，但新角色链路推荐调用方按这些约定创建索引。

### novel passage

```text
indexName  = idx:rag:novel-passage
keyPrefix  = rag:novel-passage:
documentId = novel_passage:{passageId}
text       = passage.content
metadata  = novelId, passageId, chapterId, chapterSequence, passageSequence
```

用途：角色构建阶段按情境探针召回原文 passage，用于归纳 `role_reaction_rule`。

### role example

```text
indexName  = idx:rag:role-example
keyPrefix  = rag:role-example:
documentId = role_example:{exampleId}
text       = roleExample.sampleText
metadata  = characterId, novelId, exampleId, passageId, sampleType
```

用途：聊天运行时按 `characterId + userInput` 召回角色原作表现样本。

### role reaction rule

```text
indexName  = idx:rag:role-reaction-rule
keyPrefix  = rag:role-reaction-rule:
documentId = role_reaction_rule:{ruleId}
text       = situation + "\n" + rule
metadata  = characterId, ruleId, situation
```

用途：聊天运行时按 `characterId + userInput` 召回最相关的情境反应规则。

## Metadata Schema

`ragservice` 按 `createIndex.metadataFields` 保存 metadata schema，并在写入时校验 metadata 只能包含已声明字段。字段类型支持：

| 类型 | Redis Search 字段 |
| --- | --- |
| `NUMERIC` | numeric |
| `TEXT` | text |
| `TAG` | tag |

不允许 metadata 写入大文本字段、数组对象或未声明字段。大文本只进入 `text` 字段。

## ragservice 内部组件

```text
RagIndexFacadeImpl
  -> RagIndexApplicationService
     -> RedisIndexDefinitionStore
     -> RedisVectorIndexService

RoleRuntimeRagFacadeImpl
  -> RoleRuntimeSearchService
     -> RedisVectorIndexService
     -> RerankService
```

### RedisIndexDefinitionStore

职责：

- 保存 `createIndex` 传入的 `indexName`、`keyPrefix`、`vectorDimension` 和 metadata schema。
- 后续 upsert/delete/search 只需要传 `indexName`，服务从 Redis 加载索引定义。
- 存储 key 建议为 `rag:index-definition:{indexName}`。

### RedisVectorIndexService

职责：

- 通过 Redis Search 底层命令创建索引。
- 批量 upsert 文档。
- 删除文档。
- 按 `indexName`、query、filter、topK 执行 similarity search。

这是唯一直接依赖 Redis Search 命令和 Spring AI embedding 的业务组件。

### RerankService

第一版可沿用现有 `chat` 模块中的 rerank HTTP 封装，迁移到 `ragservice`。检索流程：

```text
Redis VectorStore topK 召回
-> 可选 rerank
-> 阈值过滤
-> 返回 topN
```

如果 rerank 服务不可用，第一版允许降级为向量召回原始排序，并记录 warn 日志。

## 数据流

### 角色资产构建写入

```text
novel 生成 passage / example / reaction_rule
  -> RagIndexFacade.createIndex(indexName, keyPrefix, metadataFields)
  -> RagIndexFacade.upsertDocuments(indexName, documents)
  -> novel 更新本地 vectorStatus = INDEXED / FAILED
```

`vectorStatus` 仍保存在 `novel` 业务表中。`ragservice` 不保存业务写入状态。

### 聊天运行时召回

```text
chat 加载 session(characterId)
  -> RoleRuntimeRagFacade.searchRoleExamples(characterId, userInput, 3)
  -> RoleRuntimeRagFacade.searchReactionRules(characterId, userInput, 1)
  -> 拼接 profile + examples + reactionRules + history
  -> 调用聊天 LLM
```

## 错误语义

所有 Dubbo 返回对象包含 `success`、`code`、`message`。常见错误码：

| code | 含义 |
| --- | --- |
| `OK` | 成功 |
| `VALIDATION_ERROR` | 请求字段缺失、topK 非法或 metadata 不符合白名单 |
| `INDEX_NOT_FOUND` | 未创建对应索引 |
| `EMBEDDING_FAILED` | embedding 调用失败 |
| `VECTOR_STORE_FAILED` | Redis VectorStore 操作失败 |
| `RERANK_FAILED` | rerank 失败，若已降级则不作为失败返回 |
| `SYSTEM_ERROR` | 未分类系统错误 |

参数错误直接返回失败，不做隐式修正。rerank 失败可降级，不影响主召回链路。

## 迁移影响

### novel

- 新增 `rag-api` 依赖。
- 移除与新角色链路相关的 Redis VectorStore 直接配置。
- 角色构建阶段通过 `RagIndexFacade` 写入三类向量文档。
- 保留业务表和 `vectorStatus`，用于重试和状态展示。

### chat

- 新增 `rag-api` 依赖。
- 后续新链路下移除 scene RAG 直接依赖。
- 每轮聊天通过 `RoleRuntimeRagFacade` 召回 examples 和 reaction rules。
- system prompt 仍由角色 profile 构建，不由 `ragservice` 负责。

### ragservice

- 新增独立启动类和配置文件。
- 独立配置 embedding、Redis、Dubbo、rerank。
- 对外只暴露 Dubbo，不提供业务数据库接口。

## 测试策略

### 单元测试

- `RedisIndexDefinitionStore` 能按 `indexName` 保存并恢复索引定义。
- Redis index definition 持久化和读取正确。
- metadata schema 校验覆盖未知字段、大文本字段。
- `documentId`、filter expression、topK 默认值转换正确。
- search 请求为空 query、空 characterId、非法 topK 时返回 `VALIDATION_ERROR`。

### 集成测试

- 使用 mock `RedisVectorIndexService` 验证 Dubbo facade 入参校验和返回结构。
- 使用测试 Redis 时验证 `createIndex -> upsert -> search -> delete` 最小闭环。
- rerank 失败时验证降级为原始向量排序。

### 回归测试

- `chat` 不再依赖 `spring-ai-redis-store` 后仍可编译。
- `novel` 新角色链路只依赖 `rag-api` 即可提交索引写入请求。

## 实施顺序

1. 新增 `rag-api` 模块，定义 enum、DTO、两个 Dubbo facade。
2. 新增 `ragservice` 模块和启动类，接入 Dubbo、Redis、Spring AI embedding。
3. 实现 `RedisIndexDefinitionStore` 和 metadata schema 校验。
4. 实现基于 Redis 底层命令的 `RedisVectorIndexService`。
5. 实现 `RagIndexFacadeImpl`。
6. 实现 `RoleRuntimeRagFacadeImpl` 和 rerank 降级逻辑。
7. 将新角色链路的向量写入从 `novel` 改为调用 `rag-api`。
8. 将 `chat` 新运行时召回改为调用 `RoleRuntimeRagFacade`。
9. 移除 `chat` / `novel` 中不再需要的 RAG 配置和依赖。
