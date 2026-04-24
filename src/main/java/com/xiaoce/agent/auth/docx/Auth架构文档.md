# Auth 架构文档

## 0. 文档范围与审计基线
- 模块范围：`com.xiaoce.agent.auth` 全包（controller/service/security/config/common/domain/mapper）。
- 审计时间：2026-04-24。
- 代码基线：当前工作区源码（含 `RefreshTokenStoreImpl` 的 ZSET + Lua + 增量清理实现）。
- 审计结论：当前实现整体可用，但存在若干确定性缺陷（接口暴露不完整、状态失效链路不闭环、密码重置验证码未真正校验、参数约束不一致）。

---

## 1. 架构设计说明

### 1.1 模块整体架构（结构示意）
```text
[Client]
   |
   v
[AuthController]
   |  (DTO校验 + 路由)
   v
[AuthServiceImpl] <-------> [UsersMapper + MySQL(users)]
   |                            ^
   |                            |
   +-------> [JwtTokenService] -- (RSA签发/解析JWT)
   |
   +-------> [IRefreshTokenStore/RefreshTokenStoreImpl] -- [Redis]
   |                         |
   |                         +-- Lua(原子校验/写入/删除/清理)
   |                         +-- Pipeline(批量删全设备token)
   |
   +-------> [token version in Redis]

[Spring Security]
   |
   +-- SecurityConfig (路由放行/认证策略)
   +-- JwtAuthenticationConverter (access token -> principal)
   +-- RestAuthenticationEntryPoint (401统一返回)

[RefreshTokenCleanupTask]
   |
   +-- 分布式锁 + 批量增量清理过期refresh token
```

### 1.2 核心组件职责
- `AuthController`：HTTP 接口入口，参数校验与响应包装。路径：`auth/controller/AuthController.java`。
- `AuthServiceImpl`：认证核心编排（注册、登录、刷新、登出、全端登出、重置密码）。路径：`auth/service/impl/AuthServiceImpl.java`。
- `JwtTokenService`：JWT 签发、解析、声明校验、token version 断言。路径：`auth/service/impl/JwtTokenService.java`。
- `RefreshTokenStoreImpl`：refresh token 服务端状态存储，使用 Redis + Lua + Pipeline。路径：`auth/service/impl/RefreshTokenStoreImpl.java`。
- `RefreshTokenCleanupTask`：过期 refresh token 的后台增量清理（带 Redis 锁）。路径：`auth/service/impl/RefreshTokenCleanupTask.java`。
- `SecurityConfig` + `JwtAuthenticationConverter`：资源保护与 access token 认证转换。路径：`auth/config/SecurityConfig.java`、`auth/security/JwtAuthenticationConverter.java`。

### 1.3 模块在项目中的位置与交互
- 向外提供 `/api/v1/auth/*` 鉴权接口。
- 向内依赖：
  - MySQL `users` 表（账号主数据）。
  - Redis（refresh token 状态、token version、清理锁）。
  - Spring Security OAuth2 Resource Server（JWT 访问鉴权）。

### 1.4 为什么采用当前架构
结论：`Access Token 无状态 + Refresh Token 有状态` 是本项目最优解。

- 方案A：双 token 都纯 JWT 无状态
  - 优点：实现简单、无 Redis 依赖。
  - 缺点：无法服务端主动吊销 refresh token，难以实现“踢下线/风控失效”。
- 方案B：Access/Refresh 都走服务端会话
  - 优点：撤销控制强。
  - 缺点：每次请求都查状态，吞吐差、成本高。
- 当前方案（已落地）
  - Access Token：JWT 本地验签，低延迟。
  - Refresh Token：Redis 持久状态 + 原子校验，支持可撤销、可轮换、可批量失效。
  - 配套 token version：实现“全设备立即失效”。

---

## 2. 代码逻辑详解（含缺陷审计）

### 2.1 Refresh Token 存储模型与 Redis 结构选型

当前代码真实使用的数据结构：
- `STRING auth:refresh:jti:expire:{jti}`：value=`userId`，TTL=refresh过期时间（单 token 主键）。
- `ZSET auth:refresh:user:{userId}:tokens`：member=`jti`，score=`expireAtEpochSeconds`（用户索引）。
- `ZSET auth:refresh:tokens:expiry`：member=`userId|jti`，score=`expireAtEpochSeconds`（全局过期索引）。

选型原因：
- `STRING`：
  - `GET` O(1) 校验 owner，天然支持 key 级 TTL。
  - 解决“Hash field 无法单独 TTL”的 Redis 限制。
- 用户 `ZSET`：
  - 支持按用户枚举所有 token（全设备登出）。
  - 支持按 score 范围查过期/有效 token。
- 全局 `ZSET`：
  - 支持后台增量清理，不需要 `KEYS` 全量扫描。

关于 Hash/Set/独立过期Key：
- 代码中保留了 `MAIN_HASH_KEY_PREFIX` 常量（`RefreshToken.java:15`），但当前实现已不再使用 Hash。
- 早期常见设计是 `Hash + Set + 独立过期Key`；当前已升级为 `String + ZSet + 全局ZSet`，原因是清理路径和批量运维更稳定。

### 2.2 Lua 与 Pipeline 的职责边界
- Lua（原子）：
  - `saveRefreshToken`：单脚本内 SET + 双 ZADD + TTL 对齐。
  - `validateRefreshToken`：原子校验 owner，不一致即失败。
  - `removeRefreshToken`：原子删除单 token 三处索引。
  - `cleanupExpiredTokens`：批量从全局索引拉取并删除过期项。
- Pipeline（批量）：
  - `removeUserAllRefreshToken`：全设备登出场景下批量删除大量 key / zset member，减少 RTT。

结论：
- 需要一致性就用 Lua。
- 需要吞吐批量操作就用 Pipeline。
- 两者不是替代关系，是分工关系。

### 2.3 审计发现的确定性 Bug（带证据）

#### Bug-1：`/logout/all` 在安全配置中放行，但控制器未暴露接口
- 证据：
  - 放行配置存在：`SecurityConfig.java:67` 包含 `"/api/v1/auth/logout/all"`。
  - 控制器无对应 `@PostMapping("/logout/all")`：`AuthController.java` 全文件仅到 `"/logout"` 与 `"/reset/password"`。
- 影响：
  - 文档/配置/实现不一致，客户端按约定调用会直接 404。
- 修复方案（示例）：
```java
@PostMapping("/logout/all")
public ApiResponse<Void> logoutAll(@Valid @RequestBody LogoutRequest request) {
    authService.logoutAll(request.refreshToken());
    return ApiResponse.ok();
}
```

#### Bug-2：`resetPassword` 的验证码仅做格式校验，未做真实性校验
- 证据：`AuthServiceImpl.java:338-342` 只检查长度和数字，不查 Redis/短信/邮件验证码存储。
- 影响：
  - 任何满足格式的 code 都可通过该层校验，验证码机制形同虚设。
- 修复方案（示例）：
```java
String codeKey = "auth:verify:reset:" + normalizeEmailOrUsername(identity);
String expected = redisTemplate.opsForValue().get(codeKey);
if (!code.equals(expected)) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid verification code");
}
redisTemplate.delete(codeKey);
```

#### Bug-3：封禁用户后未立即使已签发 token 失效
- 证据：
  - `banUserPermanent` 只改 DB 状态：`AuthServiceImpl.java:317-326`。
  - 注释中也标记 `TODO` 未完成失效链路：`AuthServiceImpl.java:326`。
- 影响：
  - 用户被封禁后，已持有 access token 仍可在过期前继续访问。
- 修复方案（示例）：
```java
// ban 成功后立即全端失效
bumpTokenVersion(userId);
refreshTokenRedisStore.removeUserAllRefreshToken(userId);
```

#### Bug-4：资源访问鉴权未校验用户当前状态（ENABLE/DISABLE）
- 证据：
  - `JwtAuthenticationConverter` 状态校验代码被注释：`JwtAuthenticationConverter.java:70-74`。
- 影响：
  - 禁用用户在 access token 未过期窗口内仍可访问受保护接口。
- 修复方案（示例）：
```java
User user = usersMapper.selectById(userId);
if (user == null || user.getStatus() == null || user.getStatus() != 1) {
    throw new BadCredentialsException("User is not available");
}
```

#### Bug-5：注册参数校验规则在 DTO 与 Service 层不一致
- 证据：
  - DTO：用户名 `3-64`，邮箱 max `128`（`RegisterRequest.java:12,16`）。
  - Service：用户名 `3-20`，邮箱 max `254`（`AuthServiceImpl.java:426-427,413-414`）。
- 影响：
  - 同一请求在不同层校验规则冲突，导致错误码与报错信息不可预测。
- 修复方案：
  - 统一校验边界来源到 `AuthProperties`（单一配置源），DTO 与 Service 共用。

---

## 3. 业务场景与技术选型（STAR）

### 3.1 用户登录 / 登出
- S：用户需要低延迟登录并支持主动登出。
- T：既要性能高，又要能撤销 refresh token。
- A：
  - 登录：校验账号密码后签发 access/refresh（`AuthServiceImpl.login`）。
  - refresh token 持久化到 Redis（`saveRefreshToken`）。
  - 登出：按 jti 原子删除 token 状态（`removeRefreshToken`）。
- R：
  - access 请求走本地验签，吞吐高。
  - refresh token 可撤销，登出后不可刷新会话。

### 3.2 刷新令牌生成与验证
- S：access token 短期有效，前端需无感续期。
- T：并发刷新时要避免旧 token 复用与状态脏写。
- A：
  - refresh 时先 `validateRefreshToken` 原子校验 owner。
  - 再做 token version 断言。
  - 删除旧 refresh，再签发新 refresh 并入 Redis。
- R：
  - 刷新链路具备可撤销性与一致性保障。

### 3.3 单设备踢下线
- S：用户主动退出某个设备。
- T：只失效当前 refresh token，不影响其他设备。
- A：`removeRefreshToken(userId, jti)` 精确删 key + user zset + global zset。
- R：最小影响面，仅当前设备失效。

### 3.4 全设备踢下线（改密/风控）
- S：改密、风控封禁时要立即切断所有会话。
- T：需要“立刻生效”，而不是等 token 自然过期。
- A：
  - `bumpTokenVersion(userId)` 使旧 access token 即刻逻辑失效。
  - `removeUserAllRefreshToken(userId)` 清空 refresh 状态。
- R：
  - 新请求立即因 version mismatch 失败。
  - refresh 路径被彻底切断。

### 3.5 并发场景下的令牌一致性
- S：多端并发刷新、登出、全端下线并存。
- T：避免“先校验后删除”竞态导致的脏状态。
- A：
  - 用 Lua 包裹关键读写路径（校验/删除/清理）。
  - 批量删除使用 Pipeline 降 RTT。
  - 清理任务采用 Redis 分布式锁避免多实例重复执行。
- R：
  - 并发一致性提升，清理任务对在线流量影响可控。

---

## 4. 代码级解释（详细）

### 4.1 `saveRefreshToken`（核心入库）
代码片段（`RefreshTokenStoreImpl.java`）：
```java
Long saved = redisTemplate.execute(
    SAVE_SCRIPT,
    Arrays.asList(jtiTokenKey(jti), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
    String.valueOf(userId),
    String.valueOf(expireAtEpochSeconds),
    String.valueOf(ttlSeconds),
    jti,
    globalMember(userId, jti)
);
```

逐段解释：
- `KEYS[1]=auth:refresh:jti:expire:{jti}`：主键，`SET EX` 保存 owner + TTL。
- `KEYS[2]=auth:refresh:user:{uid}:tokens`：用户维度索引，`ZADD score=exp`。
- `KEYS[3]=auth:refresh:tokens:expiry`：全局过期索引，后台清理按 score 拉取。
- 脚本内 `TTL` 比较 + `EXPIRE`：确保用户索引过期时间至少覆盖 token 生存期。

为什么用 Lua：
- 避免 “主键写成功，但索引写失败” 的部分成功问题。
- 单次 EVAL 内完成多 key 写入，具备原子性。

### 4.2 `validateRefreshToken`（原子校验）
代码片段：
```java
Long result = redisTemplate.execute(
    VALIDATE_SCRIPT,
    Arrays.asList(jtiTokenKey(jti), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
    String.valueOf(userId), jti, globalMember(userId, jti)
);
```

脚本行为：
- `GET tokenKey` 若不存在：
  - 反向清理 `user zset` 与 `global zset` 中的陈旧 member。
  - 返回 0。
- 若存在但 owner 不匹配：返回 0。
- 匹配：返回 1。

为什么不是“先 GET 再 ZREM”：
- 两次往返有竞态窗口；Lua 在 Redis 单线程里原子执行，避免竞态。

### 4.3 `removeUserAllRefreshToken`（批量全端失效）
代码片段：
```java
Set<String> allTokens = redisTemplate.opsForZSet().range(userZsetKey, 0, -1);
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (String jti : allTokens) {
        connection.keyCommands().del(jtiTokenKey(jti).getBytes(StandardCharsets.UTF_8));
        connection.zSetCommands().zRem(globalKeyBytes, globalMember(userId, jti).getBytes(StandardCharsets.UTF_8));
    }
    connection.keyCommands().del(userKeyBytes);
    return null;
});
```

逐段解释：
- 先拉出用户所有 `jti`。
- pipeline 内批量：
  - 删每个 token 主键。
  - 删全局索引 member。
  - 最后删用户索引 key。

为什么用 Pipeline：
- 全设备登出是批量操作，瓶颈在 RTT。
- Pipeline 把 N 次网络往返压成 1 次提交，显著降低延迟。

### 4.4 后台清理 `cleanupExpiredTokens`
核心脚本：
- `ZRANGEBYSCORE globalZset -inf now LIMIT 0 batch`
- 解析 `userId|jti`
- `DEL tokenKey` + `ZREM userZset jti` + `ZREM globalZset member`

性能瓶颈与建议：
- 瓶颈1：batch 过大时 EVAL 执行时间增长，影响 Redis 单线程。
  - 建议：batch 200~500，结合 p99 监控动态调参。
- 瓶颈2：分布式锁 TTL 固定 55s，长任务可能锁过期并发执行。
  - 建议：改用可续约锁（看门狗）或按轮续期。
- 瓶颈3：`removeUserAllRefreshToken` 大用户下内存瞬时放大。
  - 建议：改 `ZSCAN` 分段 + pipeline 分批删。

---

## 5. 常见面试题（附答案）

### Q1：为什么 access token 用 JWT，refresh token 还要放 Redis？
答：access token 追求高吞吐、低延迟，JWT 本地验签最合适；refresh token 需要可撤销、可轮换、可批量失效，必须有服务端状态。该模块用 `RefreshTokenStoreImpl` 实现了 refresh 的服务端权威。

### Q2：如何保证 refresh token 验证的原子性？
答：使用 Lua（`VALIDATE_SCRIPT`）把“读取 owner + 清理陈旧索引 + 返回结果”放在一次 EVAL 内执行，避免多次命令的竞态窗口。

### Q3：如何实现“踢某个设备下线”且不影响其他设备？
答：按 `jti` 精确删除：`removeRefreshToken(userId, jti)`，只移除目标 token 的主键与索引，不触碰同用户其他 jti。

### Q4：为什么给每个 jti 用独立 key，而不是放 Hash field？
答：Redis Hash field 不能单独设置 TTL。每个 jti 独立 key 可以天然过期，避免大 Hash 里的僵尸 field 无法自动清理。

### Q5：全量删除用户 token 时，Pipeline 和事务有什么区别？为什么选 Pipeline？
答：事务（MULTI/EXEC）保证顺序提交但不减少命令数量；Pipeline 重点是降低网络 RTT。全设备登出是高批量删除，核心瓶颈在网络，故选 Pipeline。

### Q6：Redis 集群下 Lua 脚本有什么限制？
答：EVAL 的所有 key 必须落在同一 hash slot。当前脚本涉及多个 key，若上 Redis Cluster，需做 hash tag（如 `{rt}:...`）保证同槽，或拆分脚本。

### Q7：如何防止 token 在删除过程中被新请求误用？
答：本模块用两道线：
- token version（`tv`）做逻辑失效；
- refresh 状态删除做物理失效。
先 bump version 再删 refresh，可确保即刻阻断访问。

### Q8：禁用用户为什么还可能继续访问一段时间？
答：当前 `JwtAuthenticationConverter` 未校验用户状态（代码被注释），且 `banUserPermanent` 未触发 token 版本升级。已签发 access token 在过期前仍可用，这是当前缺陷。

### Q9：如何优化当前清理任务的稳定性？
答：把固定 TTL 锁改为可续约锁；加指标（每轮清理数、脚本耗时、锁竞争失败数）；遇到 Redis 抖动时降 batch，防止阻塞主线程。

### Q10：当前模块有哪些“文档与实现不一致”风险？
答：`/logout/all` 在安全配置中放行但控制器没实现；注册参数边界 DTO 与 Service 不一致；接口文档容易与真实行为偏移，需要 contract test 固化。

---

## 6. 结论与落地建议
- 结论：认证主链路可运行，refresh token 方案已升级为更可运维的 ZSET 架构；但“接口完整性、状态即时失效、验证码真实性校验、规则一致性”仍需修复。
- 建议落地优先级：
  1. P0：补齐 `/logout/all` 接口；修复 `banUserPermanent` 失效链路；启用用户状态校验。
  2. P1：重置密码接入真实验证码校验（Redis + 限流 + 一次性消费）。
  3. P1：统一 DTO/Service 校验边界到配置中心。
  4. P2：清理任务锁改可续约，补监控与告警。

