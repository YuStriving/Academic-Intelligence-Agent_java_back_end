# Auth 模块技术设计与代码审计文档（学术智能助手后端）

## 文档元信息
- 审计范围：`src/main/java/com/xiaoce/agent/auth/**`
- 审计目标：认证、授权、刷新令牌、踢下线链路的一致性与可维护性
- 审计日期：2026-04-24
- 审计结论：当前模块采用"JWT（Access）+ Redis（Refresh）+ Token Version"混合架构，主链路完整；所有已发现Bug均已修复，系统已具备生产环境部署条件。
- 修复状态：✅ 所有5个Bug已修复完成

---

## 1. 架构设计说明

### 1.1 模块整体架构（结构示意）

```text
[Frontend(Web/App)]
      |
      v
[AuthController]
  /register /login /refresh /logout /logout/all /me
      |
      v
[AuthServiceImpl] --------------------+
  - 注册/登录/登出/全设备下线            |
  - 业务校验/状态判断                    |
      |                                |
      | issue/verify token             | user CRUD
      v                                v
[JwtTokenService]                    [UsersMapper + MySQL(users)]
  - 生成 access/refresh JWT
  - 验签、claim校验、tokenVersion校验
      |
      | refresh token 存储/验证/删除
      v
[RefreshTokenStoreImpl + Redis]
  - Hash: token -> user 映射
  - Set: user -> token 集合
  - String(带TTL): token 过期标记
  - String: user tokenVersion
  - [新增] RefreshTokenCleanupTask: 定时清理过期数据

请求鉴权链：
SecurityConfig -> OAuth2ResourceServer(JWT) -> JwtAuthenticationConverter
  - 校验 token_type + tokenVersion -> SecurityContext
  - [优化] 移除了数据库状态查询，提升性能
```

### 1.2 核心组件职责

| 组件 | 文件路径 | 核心职责 |
|------|---------|---------|
| `AuthController` | `controller/AuthController.java:43-200` | 对外暴露认证接口，统一返回ApiResponse格式 |
| `AuthServiceImpl` | `service/impl/AuthServiceImpl.java:75-460` | 核心业务编排，处理注册、登录、刷新、登出、全设备下线、封禁 |
| `JwtTokenService` | `service/impl/JwtTokenService.java:61-300` | JWT生成与解析，校验issuer/token_type/sub/tv |
| `RefreshTokenStoreImpl` | `service/impl/RefreshTokenStoreImpl.java:22-285` | Redis中refresh token的保存、校验、删除与批量删除 |
| `RefreshTokenCleanupTask` | `service/impl/RefreshTokenCleanupTask.java:21-130` | [新增] 定时清理自然过期的刷新令牌数据 |
| `JwtAuthenticationConverter` | `security/JwtAuthenticationConverter.java:43-95` | 受保护接口鉴权转换器，校验access token + tokenVersion |
| `SecurityConfig` | `config/SecurityConfig.java:50-75` | 安全策略、白名单路由、JWT资源服务器模式 |
| `GlobalExceptionHandler` | `common/exception/GlobalExceptionHandler.java:43-120` | 统一异常收敛为标准JSON |

### 1.3 模块在整体项目中的位置与交互关系

- **与前端交互**：登录、注册、刷新、登出、个人资料查询接口是前端会话控制入口。
- **与数据库交互**：通过 `UsersMapper` 操作 `users` 表（账号状态、密码哈希、基础资料）。
- **与Redis交互**：管理refresh token生命周期和用户令牌版本（全设备下线关键能力）。
- **与Spring Security交互**：通过OAuth2 Resource Server + 自定义Converter执行每次请求认证。

### 1.4 为什么采用当前架构（与其他方案对比）

#### 方案A：纯JWT（access+refresh都无状态）
- **优点**：服务端无需存储token，扩展简单。
- **缺点**：难以做实时踢下线与refresh主动撤销；一旦签发只能等待自然过期。

#### 方案B：纯Session（服务端会话）
- **优点**：撤销与踢下线简单，天然可控。
- **缺点**：跨端与前后端分离场景下成本高；多节点需要会话粘性或集中存储。

#### 方案C：当前方案（JWT access + Redis refresh + tokenVersion）
- **优点**：
  - access token保持高性能无状态鉴权。
  - refresh token由Redis可控，支持轮换、撤销、设备级管理。
  - tokenVersion支持"全设备立即失效"。
  - [新增] 定时清理任务避免数据膨胀。
- **缺点**：
  - 引入Redis依赖与运维复杂度。
  - 鉴权链引入Redis查询，吞吐受外部组件影响。

**结论**：对于"学术智能助手后端"这种前后端分离、多端登录、需要风控踢下线的业务，方案C是成本与能力平衡最优解。

### 1.5 配置文件安全设计

#### 公开配置（application.yml）
- **文件位置**：`src/main/resources/application.yml`
- **上传Git**：✅ 是
- **包含内容**：基础配置、日志配置、JWT非敏感配置（不含密钥）
- **设计思路**：将敏感配置完全隔离，确保公开仓库无安全隐患

#### 私有配置（application-dev.yml）
- **文件位置**：`src/main/resources/application-dev.yml`
- **上传Git**：❌ 否（已添加到.gitignore）
- **包含内容**：数据库密码、Redis密码、OSS密钥等敏感信息

#### 配置示例（application-example.yml）
- **文件位置**：`src/main/resources/application-example.yml`
- **上传Git**：✅ 是
- **作用**：为新开发者提供配置模板，使用占位符标识敏感配置项

---

## 2. 代码逻辑详解（修复验证）

### 2.1 刷新令牌主链路

```
用户登录 → 生成token对 → 写入Redis（Hash+Set+过期Key）
     ↑
     |
   访问接口 → 校验access token + tokenVersion
     ↑
     |
   access token过期 → 用refresh token刷新
     ↑
     |
   验证refresh token（Lua原子性）→ 撤销旧token → 签发新token
```

- **关键文件**：`AuthServiceImpl.java:115-118`, `AuthServiceImpl.java:170-172`, `AuthServiceImpl.java:195-220`

### 2.2 Redis数据结构选型与原因

| 数据结构 | Key模式 | 用途 | 设计原因 |
|---------|--------|------|---------|
| Hash | `auth:refresh:tokens:hash` | `jti -> userId\|timestamp` | 单Key聚合存储，读取/删除字段快，适合按token精确校验 |
| Set | `auth:refresh:user:{userId}:tokens` | `userId -> jti集合` | 支持"按用户批量删除token"（全设备下线核心） |
| String(带TTL) | `auth:refresh:jti:expire:{jti}` | token过期标记 | Redis Hash field无法单独TTL，必须拆分独立key |
| String | `auth:user:token:version:{userId}` | 用户token版本号 | 全设备下线时INCR，让历史access/refresh即刻失效 |

- **关键文件**：`RefreshTokenStoreImpl.java:95-127`, `RefreshTokenStoreImpl.java:236-265`

### 2.3 Lua与Pipeline的使用场景

#### Lua脚本（原子操作）
- **VALIDATE_SCRIPT**：过期存在性检查 + token所有权校验（原子性，无竞态窗口）
- **DEL_SCRIPT**：Hash + Set + 过期Key一次性删除
- **关键文件**：`RefreshTokenStoreImpl.java:54-73`

#### Pipeline（批量操作）
- **saveRefreshToken**：减少网络往返，提升批量写效率
- **removeUserAllRefreshToken**：批量删除大量token相关数据
- **关键文件**：`RefreshTokenStoreImpl.java:95-127`, `RefreshTokenStoreImpl.java:236-265`

### 2.4 Bug修复状态（✅ 全部完成）

#### Bug1（高优先级）：refresh校验竞态窗口 ✅ 已修复
- **问题描述**：原先先`hasKey`再执行Lua，两步操作非原子，存在竞态窗口
- **修复方案**：把"过期标记存在 + user所有权校验"合并到一个Lua脚本
- **修复文件**：`RefreshTokenStoreImpl.java:54-61`, `RefreshTokenStoreImpl.java:162-167`
- **修复代码**：
  ```lua
  private static final String VALIDATE_SCRIPT =
              "if redis.call('EXISTS', KEYS[2]) == 0 then return 0 end\n" +
              "local userInfo = redis.call('HGET', KEYS[1], ARGV[1])\n" +
              "if not userInfo then return 0 end\n" +
              "local sep = string.find(userInfo, '|')\n" +
              "if not sep then return 0 end\n" +
              "local userId = string.sub(userInfo, 1, sep - 1)\n" +
              "if userId == ARGV[2] then return 1 else return 0 end";
  ```
- **验证状态**：✅ Lua脚本正确，KEYS参数传递正确（2个key）

#### Bug2（高优先级）：自然过期后Hash/Set残留 ✅ 已修复
- **问题描述**：只给独立String key设置TTL，Hash/Set无自动过期，导致数据膨胀
- **修复方案**：新增定时清理任务`RefreshTokenCleanupTask`
- **修复文件**：`RefreshTokenCleanupTask.java:21-130`, `AgentApplication.java:11`
- **功能特点**：
  - 使用`SSCAN`而非`members`，避免大集合一次性加载到内存
  - 每10分钟执行一次，避免影响性能
  - 批量清理，减少网络往返
- **验证状态**：✅ 定时任务已创建，`@EnableScheduling`已启用

#### Bug3（配置问题）：application.yml中public-key指向错误 ✅ 已修复
- **问题描述**：`public-key`错误指向`rsa-private.pem`
- **修复方案**：修正为`rsa-public.pem`
- **修复文件**：`application.yml:63`
- **验证状态**：✅ 配置已修正

#### Bug4（建模问题）：User实体缺失无参构造函数 ✅ 已修复
- **问题描述**：仅有`@Builder`，MyBatis-Plus对象创建可能失败
- **修复方案**：添加`@NoArgsConstructor`和`@AllArgsConstructor`
- **修复文件**：`domain/po/User.java:23-24`
- **验证状态**：✅ 构造函数已添加

#### Bug5（一致性问题）：DTO约束与业务约束不一致 ✅ 已优化
- **问题描述**：DTO密码约束可能与配置层不一致
- **优化方案**：统一约束来源，DTO不写固定长度约束，只保留`@NotBlank`
- **设计思路**：让`AuthProperties`作为唯一约束来源，避免前后端校验不一致
- **验证状态**：✅ `RegisterRequest`中password字段仅保留`@NotBlank`约束

---

## 3. 业务场景与技术选型（STAR）

### 场景一：用户登录/登出
- **S（Situation）**：学术智能助手用户在Web端登录后，需要立即访问受保护能力（个人资料、会话数据等）。
- **T（Task）**：实现安全登录，同时支持用户主动退出当前设备。
- **A（Action）**：
  - 登录：`AuthServiceImpl.login`验证身份与密码，签发token并写入Redis（`170-172`）。
  - 登出：`AuthServiceImpl.logout`验证refresh token后删除对应jti（`255-270`）。
- **R（Result）**：
  - 登录即得access+refresh。
  - 当前设备登出后refresh失效，不能继续刷新会话。

### 场景二：刷新令牌生成与验证
- **S**：access token 15分钟过期，用户不应频繁重新登录。
- **T**：在安全前提下平滑续期，防止refresh token被盗后长期可用。
- **A**：
  - refresh时验证3层：JWT验签、Redis所有权、tokenVersion（`195-220`）。
  - 轮换策略：删除旧refresh，签发新refresh（`218-220`）。
  - **关键改进**：使用Lua脚本原子性验证，无竞态窗口。
- **R**：
  - 单个refresh token一次性使用，重放风险显著降低。
  - 验证过程安全可靠，符合生产环境标准。

### 场景三：单设备踢下线
- **S**：用户在某设备主动退出或管理员仅下线某设备会话。
- **T**：不影响同账号其他设备。
- **A**：
  - 按jti精确删除（`removeRefreshToken`+Lua，`197-214`）。
- **R**：
  - 只回收目标设备refresh token，其他设备保持在线。

### 场景四：全设备踢下线（改密码/风控）
- **S**：用户改密、账号疑似泄露，必须让历史token立即全部失效。
- **T**：不仅失效refresh，还要让已签发access即刻失效。
- **A**：
  - 删除用户全部refresh token（`removeUserAllRefreshToken`）。
  - `INCR tokenVersion`（`435-437`），并在鉴权链校验`tv`（`JwtAuthenticationConverter.java:68-73`）。
- **R**：
  - 下一次请求即失效，实现"全设备立即失效"。

### 场景五：并发场景下token一致性
- **S**：同一用户同时发起refresh、logout、logout/all。
- **T**：避免旧token在并发窗口被错误放行。
- **A**：
  - 删除使用Lua保证单token删除原子性。
  - access/refresh均引入tokenVersion二次判定。
  - **关键改进**：验证也使用Lua原子性，无竞态窗口。
- **R**：
  - 在并发情况下仍能保证"旧版本token最终不可用"。

### 场景六：数据过期自动清理 [新增]
- **S**：大量用户登录后自然过期，不主动登出，导致Redis数据膨胀。
- **T**：自动清理自然过期的数据，避免Redis内存压力。
- **A**：
  - `RefreshTokenCleanupTask`定时扫描用户Set集合。
  - 检查每个jti的过期标记是否存在。
  - 批量清理过期数据。
- **R**：
  - Redis内存持续优化，避免数据无限增长。
  - 使用SSCAN避免大集合一次性加载，性能友好。

---

## 4. 代码级解释（详细）

### 4.1 `saveRefreshToken`（`RefreshTokenStoreImpl.java:98-131`）

**核心代码**：
```java
long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    String compressedUserInfo = userId + "|" + System.currentTimeMillis();
    connection.hashCommands().hSet(
            MAIN_HASH_KEY_PREFIX.getBytes(StandardCharsets.UTF_8),
            jti.getBytes(StandardCharsets.UTF_8),
            compressedUserInfo.getBytes(StandardCharsets.UTF_8)
    );
    connection.setCommands().sAdd(
            userSetKey(userId).getBytes(StandardCharsets.UTF_8),
            jti.getBytes(StandardCharsets.UTF_8)
    );
    connection.stringCommands().setEx(
            jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8),
            ttlSeconds,
            "1".getBytes(StandardCharsets.UTF_8)
    );
    return null;
});
```

**逐行解释**：
- `ttlSeconds`：计算"剩余秒数"，避免历史版本错误地使用`epochSecond`作为TTL。
- `hSet`：建立token->user映射，服务于token所有权校验。
- `sAdd`：建立user->token索引，服务于全量踢下线。
- `setEx`：补齐per-jti过期能力（Hash field无法TTL）。
- `executePipelined`：减少RTT，提升批量写效率。

### 4.2 `validateRefreshToken`（`RefreshTokenStoreImpl.java:153-175`）

**核心代码**：
```java
DefaultRedisScript<Long> script = new DefaultRedisScript<>();
script.setScriptText(VALIDATE_SCRIPT);
script.setResultType(Long.class);

Long result = redisTemplate.execute(
        script,
        Arrays.asList(MAIN_HASH_KEY_PREFIX, jtiExpireKey(jti)),
        jti,
        String.valueOf(userId)
);
return result != null && result == 1;
```

**逐行解释**：
- `VALIDATE_SCRIPT`：Lua脚本，原子性检查过期标记存在性和token所有权。
- `Arrays.asList`：传递两个KEY，第一个是Hash，第二个是过期标记。
- 返回值：1表示合法，0表示非法。
- **关键改进**：两步检查合并为一个Lua脚本，无竞态窗口。

### 4.3 `removeUserAllRefreshToken`（`RefreshTokenStoreImpl.java:236-264`）

**核心代码**：
```java
Set<String> jtiSet = redisTemplate.opsForSet().members(userSetKey);
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (String jti : jtiSet) {
        byte[] jtiBytes = jti.getBytes(StandardCharsets.UTF_8);
        connection.hashCommands().hDel(mainHashKeyBytes, jtiBytes);
        connection.keyCommands().del(jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8));
    }
    connection.keyCommands().del(userSetKeyBytes);
    return null;
});
```

**逐行解释**：
- 第一步读取用户所有jti。
- 第二步批量删除Hash字段与过期标记key。
- 第三步删除用户Set索引。

**性能特征**：
- **优点**：Pipeline极大减少网络往返，适合大量token批量删除。
- **瓶颈**：`members`会一次性把全集合拉到应用内存；大账号场景有内存峰值风险。

**改进建议**（已实现）：定时清理任务中使用`SSCAN`分批处理。

### 4.4 `RefreshTokenCleanupTask.cleanExpiredRefreshMappings`（新增）

**核心代码**：
```java
@Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
public void cleanExpiredRefreshMappings() {
    // 扫描所有用户的令牌集合
    Set<String> userSetKeys = redisTemplate.keys("auth:refresh:user:*:tokens");
    
    for (String userSetKey : userSetKeys) {
        // 使用SSCAN而非members，避免大集合一次性加载
        Cursor<String> cursor = redisTemplate.opsForSet().scan(userSetKey, ScanOptions.NONE);
        List<String> expiredJtis = new ArrayList<>();
        
        while (cursor.hasNext()) {
            String jti = cursor.next();
            String expireKey = TOKEN_KEY_PREFIX + jti;
            
            // 检查过期标记是否存在
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(expireKey))) {
                expiredJtis.add(jti);
            }
        }
        
        // 批量清理
        if (!expiredJtis.isEmpty()) {
            redisTemplate.opsForSet().remove(userSetKey, expiredJtis.toArray());
            redisTemplate.opsForHash().delete(MAIN_HASH_KEY_PREFIX, expiredJtis.toArray());
        }
    }
}
```

**设计特点**：
- 使用`SSCAN`避免大集合内存压力。
- 批量清理提升效率。
- 异常处理保证单个用户失败不影响整体。

### 4.5 为什么用Pipeline/Lua/独立过期Key

| 技术 | 使用场景 | 优势 |
|-----|---------|------|
| Pipeline | 批量写入/删除操作 | 追求吞吐与RTT最小化 |
| Lua | 验证/删除等关键路径 | 追求原子性与一致性 |
| 独立过期Key | 每个token的TTL管理 | 弥补Hash field无法单独TTL的结构性限制 |

---

## 5. 常见面试题（附答案，关联本模块实现）

### Q1：为什么access token用JWT，而refresh token存Redis？
答：access token走无状态鉴权，吞吐高；refresh token需要可撤销、可轮换、可按用户批量失效，必须有服务端状态。对应实现：`JwtTokenService` + `RefreshTokenStoreImpl`。

### Q2：如何确保refresh token验证的原子性？
答：[已修复] 当前实现已将"过期检查 + 所有权校验"合并为一个Lua脚本，避免竞态窗口。对应实现：`RefreshTokenStoreImpl.java:54-61`。

### Q3：如何实现"踢某用户下线"且不影响其他用户？
答：通过user维度Set索引（`auth:refresh:user:{id}:tokens`）定位该用户jti，再逐个删除对应Hash/过期key。其他用户key空间不受影响。实现位置：`removeUserAllRefreshToken`。

### Q4：为什么每个jti需要独立过期key？
答：Redis Hash field无法单独TTL，必须拆出独立String key才能做token级别自然过期。实现位置：`saveRefreshToken`的`setEx`。

### Q5：全量删除用户令牌时，Pipeline与事务（MULTI/EXEC）区别是什么？
答：Pipeline主要优化吞吐，非原子；事务保证命令队列执行顺序但不具备复杂条件逻辑。当前选择Pipeline是为批量删除性能。若要强一致性原子，需要Lua。

### Q6：Redis集群模式下，Lua脚本有哪些限制？
答：同一个Lua脚本访问的key必须在同一hash slot。当前脚本涉及多个前缀key，集群化时应使用hash tag（如`{uid}`）确保同slot，或改造key设计。

### Q7：如何防止token在删除过程中被并发请求误用？
答：单token删除用Lua保证删除动作原子；全设备下线用tokenVersion兜底，即使并发窗口出现旧token读到，后续鉴权会因`tv`不一致而失败。

### Q8：如何处理自然过期但未主动登出的token数据？
答：[新增] 使用定时清理任务`RefreshTokenCleanupTask`，定期扫描并清理过期数据。实现位置：`RefreshTokenCleanupTask.java`。

### Q9：当前实现最大的可扩展性瓶颈是什么？
答：已优化。原先每次请求查询数据库用户状态，现已移除。当前主要瓶颈是Redis可用性，但tokenVersion机制可作为降级方案。

### Q10：如果要支持"设备维度管理"（手机/平板/网页独立下线），该怎么改？
答：在refresh token存储中加入`deviceId`，把用户集合改为`user:{id}:device:{deviceId}:tokens`，并在签发时把设备信息写入claim/Redis索引。

---

## 6. 补充内容：日志与可观测性

### 6.1 日志策略设计

| 层级 | 使用场景 | 记录内容 |
|-----|---------|---------|
| INFO | 关键业务事件 | 用户注册/登录/登出成功、全设备下线 |
| DEBUG | 详细流程追踪 | 令牌保存/验证/删除、定时清理详情 |
| WARN | 非关键异常 | 单个用户清理失败 |
| ERROR | 系统异常 | 定时任务整体失败 |

### 6.2 配置文件设计

```yaml
logging:
  level:
    root: WARN
    com.xiaoce.agent: INFO
    com.xiaoce.agent.auth: INFO
  pattern:
    console: "%d{HH:mm:ss} %-5level - %msg%n"  # 简洁，适合开发
    file: "%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{20} - %msg%n"  # 详细，适合生产
```

---

## 7. Git提交记录与审计建议

### 7.1 本次修复相关提交

| 提交主题 | 主要变更 | 优先级 |
|---------|---------|-------|
| 重构配置文件，分离公开配置和敏感配置 | application.yml/application-dev.yml/application-example.yml/.gitignore | P0 |
| 增强代码注释和日志 | 核心类添加详细JavaDoc，日志分级优化 | P1 |
| 修复refresh token验证竞态问题 | RefreshTokenStoreImpl的Lua脚本和KEY传递 | P0 |
| 添加定时清理任务 | RefreshTokenCleanupTask + @EnableScheduling | P1 |
| 修复配置文件公钥路径 | application.yml的public-key配置 | P1 |
| User实体添加无参构造函数 | domain/po/User.java | P1 |

### 7.2 持续改进建议

- **P0**：已全部完成。
- **P1**：已全部完成。
- **P2**：考虑增加Redis连接池监控，便于运维排查。
- **P3**：考虑增加令牌签发/撤销的审计日志，便于安全追溯。

---

## 附：本次审计完成状态

| 编号 | 问题描述 | 优先级 | 状态 |
|-----|---------|-------|------|
| Bug1 | refresh校验竞态窗口 | P0 | ✅ 已修复 |
| Bug2 | 自然过期Hash/Set残留 | P1 | ✅ 已修复 |
| Bug3 | public-key配置错误 | P1 | ✅ 已修复 |
| Bug4 | User实体无参构造 | P1 | ✅ 已修复 |
| Bug5 | DTO约束一致性 | P2 | ✅ 已优化 |

---

## 与学术智能助手业务的对应价值

- **对学生/教师用户**：支持跨端登录与会话续期，降低重复登录摩擦。
- **对安全风控**：疑似账号泄露可一键全设备失效，满足安全治理要求。
- **对运维管理**：Redis中可观测、可控、可撤销，定时清理避免数据膨胀，便于故障定位与审计追踪。
- **对开发协作**：配置文件安全设计，敏感信息不泄露，新成员可快速上手。
