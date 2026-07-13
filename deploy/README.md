# 服务器部署

`deploy/` 是服务器部署目录。服务器只接收该目录内容、四个 JAR、前端构建产物和 `.env`，不需要项目源码、Maven 或 Node.js。

Compose 运行 Nginx、user、rag、novel、chat 与 RocketMQ；MySQL、Redis Stack、LLM、Embedding 和 Reranker 为外部服务。`rocketmq-init` 会自动创建 `novel` Topic。

## 准备产物

```powershell
mvn -pl user,rag,novel,chat -am "-Dmaven.test.skip=true" package

Copy-Item user/target/user-0.0.1-SNAPSHOT.jar deploy/artifacts/user.jar
Copy-Item rag/target/rag-0.0.1-SNAPSHOT.jar deploy/artifacts/rag.jar
Copy-Item novel/target/novel-0.0.1-SNAPSHOT.jar deploy/artifacts/novel.jar
Copy-Item chat/target/chat-0.0.1-SNAPSHOT.jar deploy/artifacts/chat.jar

Set-Location web
npm run build
Copy-Item dist/* ../deploy/artifacts/web -Recurse -Force
```

`deploy/artifacts/` 和 `deploy/.env` 不提交 Git。

## 启动

将 `deploy/` 内容上传到服务器后，复制 `.env.example` 为 `.env`，填写外部 MySQL、Redis 和模型密钥：

```bash
cp .env.example .env
chmod 600 .env
docker compose --env-file .env config
docker compose --env-file .env up -d --build
docker compose ps
```

默认通过 `http://服务器IP` 访问。开放服务器和云安全组的 TCP `80` 端口。

## 更新

覆盖服务器对应 JAR 或 `artifacts/web/` 后，仅替换目标服务：

```bash
docker compose build novel
docker compose up -d --no-deps novel
```

更新前端时将服务名替换为 `nginx`。修改 `api` 或 `common` 后，需要重新打包并替换所有依赖它们的应用服务。
