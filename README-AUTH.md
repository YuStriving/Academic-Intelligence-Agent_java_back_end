# 学术智能代理 — 用户认证模块

## 快速索引

| 内容 | 路径 |
|------|------|
| Docker Compose（MySQL 8 + Redis 7） | `docker-compose.yml` |
| 数据库初始化 SQL | `docker/mysql/init/01-schema.sql` |
| 应用配置 | `src/main/resources/application.yml` |
| RSA 密钥生成 | `scripts/GenKey.java`（`javac scripts/GenKey.java && java -cp scripts GenKey`） |
| REST 接口文档 | `docs/API.md` |
| 部署 / 测试 / 回滚 | `docs/DEPLOY.md` |

## 核心约定

- **AccessToken**：`RS256`，**15 分钟**；请求头 `Authorization: Bearer ...`
- **RefreshToken**：`RS256`，**7 天**；服务端 **Redis** 存 jti 权威记录；登录响应返回一次供刷新接口使用
- **密码**：BCrypt（强度 12）

## 启动

1. `docker compose up -d`
2. `./mvnw spring-boot:run`

（与中间件同 Docker 网络时加 `-Dspring-boot.run.profiles=docker`。）
