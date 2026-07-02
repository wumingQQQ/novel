# 数据库性能优化记录

本文记录数据库优化前后的可观测数据，避免在没有基线的情况下直接调整连接池、索引或缓存。

## 背景

当前开发环境使用远程 MySQL，数据库位于 Oracle 免费实例，地域在美国西部。本地访问数据库存在较高网络延迟，因此接口首请求会明显受到连接池初始化和首次建连影响。

## 测量方式

- 日志来源：`logs/user/application.log`、`logs/chat/application.log`、`logs/novel/application.log`
- 测量时间段：`2026-07-02 12:00` 到 `2026-07-02 12:06`
- 读取范围：仅查看各日志文件尾部，未全量读取历史日志
- 当前 SQL 可观测性：`novel` 模块已有 `SqlSummaryInterceptor`，`user` 和 `chat` 模块暂未输出 SQL 摘要

## 优化前基线

### User 模块

| 场景 | 时间 | 观测结果 |
| --- | --- | --- |
| 首次触发连接池 | 12:02:22 - 12:02:33 | Hikari 从 `Starting` 到 `Start completed` 约 11.4s |
| 首次注册 | 12:02:35 | `用户注册成功，userId: 6, costMs: 13266` |
| 第二次注册 | 12:03:05 | `用户注册成功，userId: 7, costMs: 2063` |
| 登录 | 12:03:25 | `用户登录完成，account: test3, costMs: 725` |
| 远程用户校验 | 12:04:02 | `远程用户校验完成，costMs: 903` |
| 远程用户校验 | 12:04:41 | `远程用户校验完成，costMs: 512` |

结论：首次注册耗时主要被 Hikari 首次建池和远程 MySQL 建连放大。连接池启动后，同类数据库操作下降到 0.7s - 2.1s 左右。

### Novel 模块

| 场景 | 时间 | 观测结果 |
| --- | --- | --- |
| 首次触发连接池 | 12:04:02 - 12:04:13 | Hikari 从 `Starting` 到 `Start completed` 约 11.1s |
| 小说上传 SQL | 12:04:13 | `NovelMapper.insert costMs=11628` |
| 小说上传接口 | 12:04:13 | `小说上传完成，costMs: 13162` |
| 创建任务 SQL | 12:04:41 | `JobMapper.insert costMs=668` |
| 创建任务接口 | 12:04:41 | `画像构建任务创建完成，costMs: 1635` |
| 查询任务进度 | 12:04:49 | `任务进度查询完成，state: PENDING` |
| 角色上下文查询 | 12:05:13 | `角色上下文暂不可用，costMs: 445` |

结论：小说上传的 `NovelMapper.insert costMs=11628` 与 Hikari 首次建池时间高度重叠，因此该 SQL 摘要不能简单理解为 insert 本身慢。连接池启动后，`JobMapper.insert` 降到 668ms。

### Chat 模块

| 场景 | 时间 | 观测结果 |
| --- | --- | --- |
| 首次触发连接池 | 12:05:00 - 12:05:11 | Hikari 从 `Starting` 到 `Start completed` 约 11.3s |
| 创建未完成 job 的会话 | 12:05:13 | `聊天会话创建失败，costMs: 1334` |
| 创建已完成 job 的会话 | 12:05:22 | `聊天会话创建完成，sessionId: 17, costMs: 672` |
| 用户校验缓存 | 12:05:12 / 12:05:21 | `用户远程校验结果缓存命中` |
| 画像上下文缓存 | 12:05:12 / 12:05:21 | job 5 未命中，job 3 命中 |

结论：Chat 首次访问同样触发约 11s 的 Hikari 建池成本。命中用户校验缓存和画像上下文缓存后，会话创建耗时约 672ms。

## 异常与噪声

- `HikariPool-1 - Failed to validate connection ... No operations allowed after connection closed` 在历史日志中出现过，说明远程 MySQL 空闲连接容易被服务端或网络断开。
- `Dubbo ObjectMapperCodec serialize/deserialize error` 仍然存在，但属于 Dubbo 安全上下文序列化问题，不归入本轮数据库优化。
- `Dubbo Fail to connect to null` 出现在应用关机后，属于关闭阶段连接清理噪声，不作为接口基线。
- `user` 和 `chat` 暂时没有 SQL 摘要日志，无法精确区分 SQL 执行耗时与接口内其他逻辑耗时。

## 初步判断

本轮日志显示，当前最大问题是远程 MySQL 首次建连和连接池惰性初始化：

- 三个模块第一次触发数据库访问时，Hikari 初始化均约 11s。
- 首次业务接口耗时基本被连接池启动成本放大。
- 连接池启动后，常规远程查询或写入大多在数百毫秒到 2s 左右。

因此第一阶段优化应优先解决：

1. 启动期数据库预热，避免首个真实用户请求承担 Hikari 初始化成本。
2. 调整 dev 环境 Hikari 参数，降低远程空闲连接失效带来的失败概率。
3. 将 SQL 摘要拦截器下沉到 common，使 `user`、`chat`、`novel` 都能输出统一 SQL 耗时。

## 待优化项

### 1. 数据库预热

在应用启动完成后主动借出连接并执行：

```sql
SELECT 1
```

建议行为：

- 预热成功输出 INFO 日志。
- 预热失败输出 WARN 日志，但不阻止应用启动。
- 放在 common 中自动配置，三个模块复用。

### 2. Dev 环境 Hikari 参数

远程 MySQL 开发环境建议降低空闲连接数量，并缩短连接生命周期：

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 1
      maximum-pool-size: 5
      connection-timeout: 30000
      validation-timeout: 3000
      idle-timeout: 300000
      max-lifetime: 600000
      keepalive-time: 120000
```

### 3. JDBC URL 超时参数

建议在 dev 配置中增加：

```text
connectTimeout=10000&socketTimeout=30000&tcpKeepAlive=true
```

### 4. 统一 SQL 摘要

将 `novel` 中现有的 `SqlSummaryInterceptor` 迁移到 common，并通过自动配置注入。这样后续优化前后可以比较所有模块的 mapper 耗时。

## 复测指标

完成第一阶段优化后，使用相同接口顺序复测：

| 指标 | 优化前 | 优化后 |
| --- | --- | --- |
| user 首次注册 | 13266ms | 待测 |
| user 第二次注册 | 2063ms | 待测 |
| user 登录 | 725ms | 待测 |
| novel 首次上传 | 13162ms | 待测 |
| novel 创建任务 | 1635ms | 待测 |
| chat 首次建池 | 约 11.3s | 待测 |
| chat 创建会话 | 672ms | 待测 |
| Hikari stale connection WARN | 存在历史记录 | 待测 |
