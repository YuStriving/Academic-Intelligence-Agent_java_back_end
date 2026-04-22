# 学术搜索引擎 — 用户认证 REST API 文档

**Base URL**: `http://localhost:8080`  
**统一响应体**:

```json
{
  "code": 0,
  "message": "成功",
  "data": { }
}
```

- **code**: `0` 表示业务成功；非 `0` 为业务错误码（与 HTTP 状态码配合使用）。
- **message**: 提示信息。
- **data**: 业务数据；失败时可为 `null`。

**鉴权说明**

| 类型 | 说明 |
|------|------|
| 公开接口 | 无需 `Authorization` |
| 受保护接口 | 请求头 `Authorization: Bearer <AccessToken>` |

**令牌规则（固定）**

| 令牌 | 算法 | 有效期 | 用途 |
|------|------|--------|------|
| AccessToken | RSA-SHA256（JWT `RS256`） | **15 分钟**（900 秒） | 所有业务接口鉴权 |
| RefreshToken | RSA-SHA256（JWT `RS256`） | **7 天**（604800 秒） | **仅**调用「刷新」接口；服务端以 Redis 为权威存储，带 TTL |

---

## 1. 用户注册

**概述**：新用户注册；用户名、邮箱唯一；密码 BCrypt 存储。

| 项目 | 内容 |
|------|------|
| 路径 | `/api/v1/auth/register` |
| 方法 | `POST` |
| 权限 | 公开 |

**请求头**

| 头 | 必填 | 说明 |
|----|------|------|
| Content-Type | 是 | `application/json` |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 3–64 字符 |
| email | string | 是 | 合法邮箱，最大 128 |
| password | string | 是 | 8–64 字符 |

**成功响应** `HTTP 200`，`code=0`

`data` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 用户 ID |
| username | string | 用户名 |
| email | string | 邮箱 |

**业务错误码**

| code | HTTP | 说明 |
|------|------|------|
| 40901 | 409 | 用户名已存在 |
| 40902 | 409 | 邮箱已被注册 |
| 40001 | 400 | 参数校验失败 |

**请求示例**

```http
POST /api/v1/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "researcher01",
  "email": "r01@example.com",
  "password": "SecurePass8"
}
```

**成功示例**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "id": 1,
    "username": "researcher01",
    "email": "r01@example.com"
  }
}
```

---

## 2. 用户登录

**概述**：校验账号密码；签发 RSA 签名双令牌；**AccessToken** 返回给前端；**RefreshToken** 写入 Redis（权威），响应体仍返回一次 RefreshToken 供前端仅在刷新流程使用（**勿长期写入 localStorage**）。

| 项目 | 内容 |
|------|------|
| 路径 | `/api/v1/auth/login` |
| 方法 | `POST` |
| 权限 | 公开 |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 注册时的用户名 |
| password | string | 是 | 密码 |

**成功响应** `HTTP 200`，`code=0`

`data` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| accessToken | string | JWT Access |
| refreshToken | string | JWT Refresh（仅用于 `/refresh`） |
| expiresIn | number | Access 过期秒数，固定 **900** |
| refreshExpiresIn | number | Refresh 过期秒数，固定 **604800** |
| tokenType | string | 固定 `Bearer` |

**业务错误码**

| code | HTTP | 说明 |
|------|------|------|
| 40103 | 401 | 用户名或密码错误 |

**成功示例**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJSUzI1NiIs...",
    "expiresIn": 900,
    "refreshExpiresIn": 604800,
    "tokenType": "Bearer"
  }
}
```

---

## 3. 刷新访问令牌

**概述**：校验 RefreshToken 的 **RSA 签名**与 **Redis 中存在性**，通过后签发**新 AccessToken**（15 分钟），并**延长 Redis 中该 Refresh 条目的 TTL**（与 7 天策略一致）。

| 项目 | 内容 |
|------|------|
| 路径 | `/api/v1/auth/refresh` |
| 方法 | `POST` |
| 权限 | 公开（**禁止**携带 AccessToken 作为唯一凭证；使用 body 中的 refreshToken） |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refreshToken | string | 是 | 登录返回的 RefreshToken |

**成功响应** `HTTP 200`，`code=0`

`data` 与登录类似；`refreshToken` 字段为**同一字符串**（未轮换 Refresh 时）。

**业务错误码**

| code | HTTP | 说明 |
|------|------|------|
| 40104 | 401 | 刷新令牌无效或已过期 / Redis 不匹配 |

---

## 4. 登出

**概述**：根据当前 AccessToken 解析用户，**删除 Redis 中该用户全部 RefreshToken 记录**，实现该账号所有刷新凭证失效。

| 项目 | 内容 |
|------|------|
| 路径 | `/api/v1/auth/logout` |
| 方法 | `POST` |
| 权限 | **需 AccessToken** |

**请求头**

| 头 | 必填 | 说明 |
|----|------|------|
| Authorization | 是 | `Bearer <AccessToken>` |

**成功响应** `HTTP 200`，`code=0`，`data` 为 `null`。

---

## 5. 当前用户（受保护）

**概述**：验证 AccessToken 有效且未篡改（RSA 验签 + 未过期）。

| 项目 | 内容 |
|------|------|
| 路径 | `/api/v1/auth/me` 或 `/api/v1/users/me` |
| 方法 | `GET` |
| 权限 | **需 AccessToken** |

**成功响应** `data`：`id`, `username`, `email`

**错误**

| code | HTTP | 说明 |
|------|------|------|
| 40101 | 401 | 未认证或令牌无效 |
| 40102 | 401 | 访问令牌已过期 |

---

## 附录：HTTP 与业务码约定

- 成功：HTTP `200`，`code=0`。
- 业务失败：HTTP 状态与 `ErrorCode` 中 `httpStatus` 一致，`code` 为非 0 业务码。
- 全局异常由 `GlobalExceptionHandler` 统一包装为 `ApiResponse`。
