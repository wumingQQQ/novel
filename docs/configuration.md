# 配置治理说明

本文记录各模块配置的归属、环境变量约定和本地开发默认值，避免多模块配置分散后出现漏改、错改或敏感信息入库。

## 配置分层

项目当前按三层管理配置：

| 层级 | 文件或来源 | 用途 |
| --- | --- | --- |
| 模块默认配置 | `application.yml` | 端口、Dubbo、Hikari、业务默认参数等稳定配置 |
| 开发环境配置 | `application-dev.yml` | 本地数据库、Redis、RocketMQ、LLM/RAG 开发接入配置 |
| 环境变量 | 操作系统或启动参数 | 密码、密钥、云服务地址、可变外部依赖地址 |

原则：

- 密码、Token、API Key 不写死到配置文件。
- 外部服务地址必须能通过环境变量覆盖。
- 各模块可以保留独立数据源配置，后续拆分数据库时不需要再反向拆配置。
- `dev` 环境允许安全性较低的默认值，但生产环境必须通过环境变量显式注入。

## 公共环境变量

| 环境变量 | 用途 | 默认值 | 适用模块 | 备注 |
| --- | --- | --- | --- | --- |
| `MYSQL_HOST` | MySQL 主机 | `localhost` | user/chat/novel | 远程数据库必须覆盖 |
| `MYSQL_PORT` | MySQL 端口 | `3306` | user/chat/novel |  |
| `MYSQL_DATABASE` | MySQL 数据库名 | `novel_dev` | user/chat/novel | 当前三个模块共用开发库 |
| `MYSQL_USERNAME` | MySQL 用户名 | `root` | user/chat/novel | 生产环境必须覆盖 |
| `MYSQL_PASSWORD` | MySQL 密码 | 空 | user/chat/novel | 生产环境必须覆盖 |
| `REDIS_HOST` | Redis 主机 | `localhost` | chat/novel |  |
| `REDIS_PORT` | Redis 端口 | `9379` | chat/novel | 当前开发环境默认值 |
| `REDIS_PWD` | Redis 密码 | 空 | chat/novel |  |
| `ROCKETMQ_NAME_SERVER` | RocketMQ NameServer | `localhost:9876` | user/chat/novel | Docker/远程环境必须覆盖 |
| `JWT_SECRET` | JWT 签名密钥 | dev 默认值 | user/chat/novel | 生产环境必须覆盖 |

## LLM 与 RAG 环境变量

| 环境变量 | 用途 | 默认值 | 适用模块 |
| --- | --- | --- | --- |
| `DS_BASE_URL` | DeepSeek 兼容接口地址 | `https://api.deepseek.com` | chat/novel |
| `DS_API_KEY` | DeepSeek API Key | 空 | chat/novel |
| `MIMO_BASE_URL` | Mimo 接口地址 | `https://api.xiaomimimo.com` | chat/novel |
| `MIMO_API_KEY` | Mimo API Key | 空 | chat/novel |
| `AGNES_BASE_URL` | Agnes 接口地址 | `https://apihub.agnes-ai.com` | chat/novel |
| `AGNES_API_KEY` | Agnes API Key | 空 | chat/novel |
| `ALI_BASE_URL` | 通义千问兼容接口地址 | 空 | chat/novel |
| `ALI_API_KEY` | 通义千问 API Key | 空 | chat/novel |
| `EMBEDDING_BASE_URL` | 嵌入模型接口地址 | `https://api.botcf.com` | chat |
| `EMBEDDING_API_KEY` | 嵌入模型 API Key | 空 | chat |
| `RERANKER_BASE_URL` | 重排序模型接口地址 | `https://api.botcf.com` | chat |
| `RERANKER_API_KEY` | 重排序模型 API Key | 空 | chat |

## 模块配置归属

### user

`user` 模块负责用户注册、登录、远程用户校验和任务完成消息消费。

| 配置项 | 所在文件 | 说明 |
| --- | --- | --- |
| `server.port` | `application.yml` | HTTP 端口，默认 `8082` |
| `spring.datasource.hikari.*` | `application.yml` | 连接池通用参数 |
| `spring.datasource.*` | `application-dev.yml` | 开发环境数据库连接 |
| `dubbo.*` | `application.yml` | Triple 协议服务暴露，默认端口 `50052` |
| `rocketmq.name-server` | `application-dev.yml` | 任务完成消息消费 |
| `auth.jwt.*` | `application.yml` | JWT 签发和校验配置 |

### novel

`novel` 模块负责小说上传、画像构建流程、LLM 调用、任务进度和事件发布。

| 配置项 | 所在文件 | 说明 |
| --- | --- | --- |
| `spring.datasource.hikari.*` | `application.yml` | 连接池通用参数 |
| `spring.datasource.*` | `application-dev.yml` | 开发环境数据库连接 |
| `spring.data.redis.*` | `application-dev.yml` | 任务进度和阶段失败项存储 |
| `dubbo.*` | `application.yml` | Profile/Scene 远程接口暴露，默认端口 `50051` |
| `llm.*` | `application-dev.yml` | 小说处理阶段 LLM 提供商和温度配置 |
| `rocketmq.*` | `application-dev.yml` | 场景切分、任务完成事件发布 |
| `novel.*` | `application.yml` | 上传、分层、召回、画像补强等业务参数 |

### chat

`chat` 模块负责角色会话、画像上下文缓存、RAG 召回和场景索引消费。

| 配置项 | 所在文件 | 说明 |
| --- | --- | --- |
| `server.port` | `application.yml` | HTTP 端口，默认 `8081` |
| `spring.datasource.hikari.*` | `application.yml` | 连接池通用参数 |
| `spring.datasource.*` | `application-dev.yml` | 开发环境数据库连接 |
| `spring.data.redis.*` | `application-dev.yml` | 聊天历史、RPC 结果和画像提示词缓存 |
| `llm.*` | `application-dev.yml` | 角色聊天 LLM 提供商和温度配置 |
| `rag.*` | `application-dev.yml` | Embedding、Reranker、Redis Vector Store 配置 |
| `rocketmq.*` | `application-dev.yml` | 场景切分事件消费 |
| `chat.*` | `application.yml` | 历史消息数量和缓存 TTL |

## 数据库连接约定

开发环境当前使用远程 MySQL，因此 JDBC URL 包含：

- `useSSL=true`：启用 SSL 连接。
- `connectTimeout=10000`：避免连接建立无限等待。
- `socketTimeout=30000`：限制读写等待时间。
- `tcpKeepAlive=true`：启用 TCP keepalive。

如果生产环境使用严格证书校验，后续应切换为 `sslMode=VERIFY_CA` 或 `sslMode=VERIFY_IDENTITY`，并配置对应证书。

## 后续治理方向

第一阶段只做配置可见性和环境变量覆盖，不抽公共配置文件。后续可以按风险逐步推进：

1. 增加生产环境配置校验，禁止默认 JWT 密钥、空数据库密码、空 API Key 进入生产。
2. 将重复的 Hikari 参数整理为文档化标准，必要时再抽公共配置。
3. 为 LLM/RAG 配置增加启动期校验，避免运行到真实调用时才发现缺少 Key。
4. 整理 RocketMQ topic、tag、group 的命名文档，和 `MqDestinations` 保持一致。
