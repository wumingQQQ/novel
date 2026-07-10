# P0 工程基线改造设计

## 背景

当前业务主链路已经迁移到原作样本驱动的角色构建方案，但默认认证配置、数据库初始化、本地基础设施和开发文档没有同步形成可重复的工程基线。本次改造只解决 P0 可用性问题，不扩展业务能力，也不进行无关重构。

## 目标

1. 默认配置下认证相关 Bean 能完整创建，`user`、`novel`、`chat` 的认证行为一致。
2. 测试代码继续保留为本地资产，不提交到 Git；删除已经引用废弃接口的本地测试。
3. 使用 Flyway 管理 `user`、`novel`、`chat` 数据库结构，并兼容开发环境三个模块共用一个数据库。
4. 提供 MySQL、Redis Stack、RocketMQ 的本地基础设施编排。
5. 提供从环境准备到四个服务启动、登录和最小链路验证的开发文档。

## 非目标

- 不提交任何 `src/test` 文件。
- 不把四个应用服务容器化。
- 不调整 Job 与 Role 的完成语义。
- 不重构 RAG、Pipeline、缓存或可观测性实现。
- 不引入新的生产部署方案。

## 方案选择

采用“修复默认配置 + Flyway + 仅编排基础设施”的方案。

相比只补文档，该方案能消除数据库初始化不可重复的问题；相比把所有服务都容器化，它保留当前 IDE/Maven 开发方式，改动更小，也更容易逐项验证。

## 设计

### 1. 认证基线

- `JwtUserIdExtractor` 作为通用组件始终注册，不再随资源服务器开关联动消失。
- `JwtDecoder` 仍只在 `auth.jwt.enabled=true` 时创建。
- `user`、`novel`、`chat` 默认启用 JWT；`user` 的注册和登录接口继续匿名开放。
- 三个模块继续从同一个 `JWT_SECRET` 环境变量读取签名密钥。
- 不增加匿名用户或请求头模拟用户的生产代码。

验证使用本地忽略的测试和 Maven 模块构建完成，测试文件不进入提交。

### 2. 本地测试策略

- `.gitignore` 使用明确的 `**/src/test/` 规则表达“模块测试目录不提交”，避免含义宽泛的 `test/`。
- 删除本地 `chat` 中仍依赖旧 Scene API 的测试。
- 保留仍与当前代码一致的本地测试，后续是否纳管由单独决策处理。

由于被删除测试从未被 Git 跟踪，本项提交记录测试忽略策略的明确化；不会伪造空提交，也不会暂存测试文件。

### 3. 数据库迁移

- `user`、`novel`、`chat` 引入 Flyway 和 MySQL 数据库支持。
- 每个模块使用独立迁移目录和独立历史表：
  - `flyway_schema_history_user`
  - `flyway_schema_history_novel`
  - `flyway_schema_history_chat`
- 初始 schema 转换为各模块的 `V1__init_*.sql`。
- 开发环境仍允许三个模块共用 `novel_dev` 数据库，不会因相同版本号互相覆盖迁移记录。
- 移除 Spring SQL Init 的旧 schema 配置，数据库结构只由 Flyway 管理。

### 4. 本地基础设施

- 新增 Compose 文件，只启动：
  - MySQL
  - Redis Stack（包含 Redis Search）
  - RocketMQ NameServer 与 Broker
- Redis 对宿主机暴露现有开发默认端口 `9379`，避免同步修改所有模块默认配置。
- 配置使用开发默认值，并允许通过环境变量覆盖。
- 不在仓库中写入 LLM、Reranker、邮件或 COS 密钥。

### 5. 开发文档与 Profile

- 删除各模块 `application.yml` 中硬编码的 `spring.profiles.active: dev`。
- README 明确要求本地启动时显式设置 `SPRING_PROFILES_ACTIVE=dev`。
- README 记录：依赖版本、端口、环境变量、基础设施启动、Flyway 行为、四服务启动顺序、注册登录和最小 API 验证。
- 文档明确真实 LLM、Embedding、Reranker、邮件和 COS 能力需要额外密钥，默认验证不调用这些外部服务。

## 提交拆分

按仓库现有 Conventional Commits 风格生成以下原子提交：

1. `fix(auth): 修复默认JWT认证配置`
2. `chore(test): 明确本地测试忽略策略`
3. `feat(database): 引入Flyway数据库迁移`
4. `chore(dev): 增加本地基础设施编排`
5. `docs: 补充本地开发指南`

每个提交前检查暂存差异、敏感信息和对应验证结果。后续提交失败时停留在最近一个已验证提交，不把多个事项混入同一提交。

## 验收标准

- 三个需要认证的模块不再因 `JwtUserIdExtractor` 条件注册而缺少 Bean。
- Git 中仍不存在任何 `src/test` 文件。
- 过时的本地 Scene RAG 测试已删除。
- 三个业务模块的迁移能在同一个空数据库中顺序执行。
- Compose 配置能够解析，并包含 MySQL、Redis Stack、RocketMQ NameServer/Broker。
- README 能指导新开发者从空环境启动基础设施和四个服务。
- 每个事项均有独立、可回滚的提交。
