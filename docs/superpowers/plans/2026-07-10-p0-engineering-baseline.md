# P0 工程基线改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入数据库迁移工具、不提交测试代码的前提下，修复认证默认配置并补齐可复现的本地开发入口。

**Architecture:** 保留 Maven 多模块、现有 `schema.sql` 和 IDE/Maven 启动方式。公共安全自动配置负责稳定提供用户 ID 提取器；Compose 只承载 MySQL、Redis Stack、RocketMQ；README 负责手工建表和启动顺序。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring Security、MySQL、Redis Stack、RocketMQ、Docker Compose、Maven。

---

## Task 1: 调整设计范围

**Files:**

- Modify: `docs/superpowers/specs/2026-07-10-p0-engineering-baseline-design.md`
- Create: `docs/superpowers/plans/2026-07-10-p0-engineering-baseline.md`

- [ ] 删除 Flyway 目标、设计、验收和提交项。
- [ ] 明确数据库继续使用三个现有 `schema.sql` 手工初始化。
- [ ] 运行 `git diff --check`，提交 `docs: 调整P0工程基线改造范围`。

## Task 2: 修复 JWT 默认配置

**Files:**

- Modify: `common/src/main/java/com/wuming/common/security/JwtResourceServerAutoConfiguration.java`
- Modify: `user/src/main/resources/application.yml`
- Modify: `novel/src/main/resources/application.yml`
- Modify: `chat/src/main/resources/application.yml`
- Test locally: `novel/src/test/java/com/wuming/novel/config/JwtResourceServerAutoConfigurationTest.java`

- [ ] 创建本地测试：`auth.jwt.enabled=false` 时仍存在一个 `JwtUserIdExtractor` Bean。
- [ ] 运行 `mvn -pl novel -am "-Dtest=JwtResourceServerAutoConfigurationTest" -Dsurefire.failIfNoSpecifiedTests=false test`，确认测试因缺少 Bean 失败。
- [ ] 将 `JwtResourceServerAutoConfiguration` 的类级认证开关移动到 `jwtDecoder()`；`currentUser()` 始终按 `@ConditionalOnMissingBean` 创建。
- [ ] 将 user、novel、chat 的 `AUTH_JWT_ENABLED` 默认值改成 `true`。
- [ ] 重新运行指定测试，确认通过；只暂存四个生产文件。
- [ ] 提交 `fix(auth): 修复默认JWT认证配置`。

## Task 3: 明确本地测试策略

**Files:**

- Modify: `.gitignore`
- Delete locally: `chat/src/test/java/com/wuming/chat/rag/SceneVectorStoreRealApiTest.java`

- [ ] 删除引用已移除 `api.scene` 和 `SceneVectorStoreService` 的本地测试。
- [ ] 将 `.gitignore` 中宽泛的 `test/` 改成 `**/src/test/`。
- [ ] 运行 `git ls-files -- "*src/test*"`，确认无测试被跟踪。
- [ ] 用一个仍存在的本地测试文件运行 `git check-ignore -v`，确认命中新规则。
- [ ] 只暂存 `.gitignore`，提交 `chore(test): 明确本地测试忽略策略`。

## Task 4: 增加本地基础设施

**Files:**

- Create: `compose.yaml`
- Create: `deploy/rocketmq/broker.conf`

- [ ] 定义 MySQL、Redis Stack、RocketMQ NameServer、RocketMQ Broker。
- [ ] MySQL 使用 `novel_dev` 和本地空 root 密码；Redis 映射宿主机 `9379` 到容器 `6379`。
- [ ] Broker 使用 `rocketmq-namesrv:9876`，对宿主机暴露 `10911`。
- [ ] 运行 `docker compose -f compose.yaml config`，确认配置可解析。
- [ ] 提交 `chore(dev): 增加本地基础设施编排`。

## Task 5: 移除硬编码开发 Profile

**Files:**

- Modify: `user/src/main/resources/application.yml`
- Modify: `novel/src/main/resources/application.yml`
- Modify: `chat/src/main/resources/application.yml`
- Modify: `rag/src/main/resources/application.yml`

- [ ] 删除四个模块的 `spring.profiles.active: dev`。
- [ ] 运行 `rg -n "active:\s*dev" user/src/main/resources novel/src/main/resources chat/src/main/resources rag/src/main/resources`，确认无硬编码激活。
- [ ] 运行 `mvn -pl user,novel,chat,rag -am -Dmaven.test.skip=true compile`。
- [ ] 提交 `chore(config): 移除硬编码开发环境`。

## Task 6: 补充本地开发指南

**Files:**

- Create: `README.md`

- [ ] 记录 Java、Maven、Docker 前置条件和服务端口。
- [ ] 记录 Compose 启动命令和三个 `schema.sql` 的手工执行顺序。
- [ ] 记录 `SPRING_PROFILES_ACTIVE=dev`、`JWT_SECRET` 和按需外部服务密钥。
- [ ] 记录 user → rag → novel → chat 的启动顺序以及注册、登录验证命令。
- [ ] 明确测试目录当前只用于本地，不提交 Git。
- [ ] 用 `rg` 验证 README 包含 Compose、schema、Profile、JWT 和测试策略，提交 `docs: 补充本地开发指南`。

## 最终验证

- [ ] `mvn -pl user,novel,chat,rag -am -Dmaven.test.skip=true compile` 退出码为 0。
- [ ] `docker compose -f compose.yaml config` 退出码为 0。
- [ ] `git ls-files -- "*src/test*"` 无输出。
- [ ] `git status --short` 无已跟踪未提交改动。
- [ ] `git log --oneline -8` 显示各项原子提交。
