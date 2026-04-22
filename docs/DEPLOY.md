# 部署、测试与回滚

## 一、端口与默认账号（docker-compose）

| 服务 | 宿主机端口 | 默认账号 / 说明 |
|------|------------|-------------------|
| MySQL 8.0 | **3306** | 库名 `agent_db`；应用使用 **`root` / `123456`**（与 `MYSQL_ROOT_PASSWORD` 一致） |
| Redis 7 | **6380→6379** | 密码 **`123456`**（宿主机端口 **6380**，避免与本机 6379 冲突） |

**与后端对齐**：`application.yml` 中 `username: root`、`password: 123456`，Redis `password: 123456`、`port: 6380`。

**自定义**：复制 `.env.example` 为 `.env` 并修改变量；修改密码后需同步 `application.yml` 并重建数据卷或执行 `ALTER USER`。

---

## 二、一键启动中间件

```bash
cd <项目根目录>
docker compose up -d
docker compose ps
```

- 数据卷：`agent_mysql_data`、`agent_redis_data`，`docker compose down` **不会**删除卷；删除卷加 `-v`（数据清空）。

初始化 SQL：`docker/mysql/init/01-schema.sql` 在 MySQL **首次**初始化时自动执行。

---

## 三、RSA 密钥（JWT RS256）

首次或轮换密钥时，在项目根目录执行：

```bash
javac scripts/GenKey.java
java -cp scripts GenKey
```

生成 `src/main/resources/keys/rsa-private.pem` 与 `rsa-public.pem`。

**生产环境**：使用 KMS/密钥库保管私钥，禁止提交真实生产私钥到仓库。

---

## 四、启动后端

前置条件：本机已安装 **JDK 21**、**Maven**（或使用项目 `mvnw`）。

1. 确认 `application.yml` 中 MySQL、Redis 与 docker-compose 一致。
2. 启动：

```bash
./mvnw spring-boot:run
# Windows: mvnw.cmd spring-boot:run
```

3. 若后端与 MySQL/Redis 同 Docker 网络部署，可增加：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

并确保 `application-docker.yml` 中主机名为 `mysql` / `redis`。

---

## 五、连通性自检

```bash
# MySQL（需客户端）
mysql -h 127.0.0.1 -P 3306 -u agent -pAgentUser_2026! -e "USE agent_db; SHOW TABLES;"

# Redis
docker exec -it agent-redis redis-cli -a AgentRedis_2026! PING
```

**接口探测**（注册 → 登录 → 带 Token 访问）见 `docs/API.md`。

---

## 六、全场景测试清单（手工）

| 场景 | 预期 |
|------|------|
| 注册新用户 | 200，`code=0` |
| 重复用户名/邮箱 | 409，对应业务码 |
| 登录成功 | 返回 `accessToken`、`refreshToken`，Redis 存在 `agent:rt:tok:{jti}` |
| `GET /api/v1/users/me` 带 AccessToken | 200 |
| 无 Token 访问 `/me` | 401 |
| 篡改 AccessToken 任意字符 | 401 |
| Access 过期后用 Refresh 调 `/refresh` | 200，新 Access |
| 无效 Refresh | 401，`40104` |
| 登出 | 200；之后用旧 Refresh 刷新失败 |
| Redis TTL | 约 7 天后 key 自动删除（与 JWT exp 一致） |

---

## 七、安全风险防护（摘要）

- **密码**：BCrypt（强度 12），不明文、不记录日志。
- **JWT**：仅 **RS256** 私钥签发；网关/资源服务用公钥验签，防篡改、防伪造。
- **RefreshToken**：服务端 Redis 为权威；登出删除用户维度全部 jti 键。
- **防重放**：短期 Access + Refresh 单次用途场景可结合业务幂等（本模块未实现业务级 nonce，可在网关层扩展）。
- **CORS**：`application.yml` 配置允许的前端源，生产收紧域名。

---

## 八、一键回滚

1. **代码**：`git checkout -- <文件或提交>`，恢复变更前版本。
2. **中间件数据**：保留卷则数据仍在；若需清空：`docker compose down -v` 后重新 `up` 并重新建表/造数。
3. **密钥**：回滚 `src/main/resources/keys/*.pem` 到备份；所有已签发令牌立即按新密钥集失效。

---

## 九、变更文件清单（本认证体系）

- `docker-compose.yml`、`docker/mysql/init/01-schema.sql`
- `pom.xml`
- `src/main/resources/application.yml`、`application-docker.yml`
- `src/main/resources/keys/*.pem`（由脚本生成）
- `src/main/java/com/xiaoce/agent/**`（config / common / jwt / security / user / auth）
- `docs/API.md`、`docs/DEPLOY.md`
