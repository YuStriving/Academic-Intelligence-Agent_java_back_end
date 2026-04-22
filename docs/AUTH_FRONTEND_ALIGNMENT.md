# Agent Auth Frontend Alignment

This document defines the auth contract implemented in `agent` and aligned with `frontend`.

## 1) Unified response envelope

All auth APIs return:

- `code: number`
- `message: string`
- `data: object | null`
- `timestamp: number` (unix milliseconds)

Success response uses:

- HTTP status: `200`
- `code: 200`
- `message: "OK"`

Failure response uses HTTP status (`400/401/403/409/500`) and business `code` from `ErrorCode`.

## 2) Endpoint alignment with frontend

Base path: `/api/v1/auth`

### POST `/login`

Request JSON:

- `emailOrUsername: string` (required)
- `password: string` (required)
- `rememberMe?: boolean` (optional, accepted for compatibility)

Response `data`:

- `accessToken: string`
- `refreshToken: string`
- `expiresIn: number` (`900`)
- `refreshExpiresIn: number` (`604800`)
- `tokenType: string` (`Bearer`)

### POST `/refresh`

Request JSON:

- `refreshToken: string` (required)

Response `data`:

- `accessToken: string`
- `refreshToken: string` (rotated to a new token)
- `expiresIn: number`
- `refreshExpiresIn: number`
- `tokenType: string`

### POST `/logout`

Headers:

- `Authorization: Bearer <accessToken>`

Request body:

- none

Response:

- `data: null`

### GET `/me`

Headers:

- `Authorization: Bearer <accessToken>`

Response `data`:

- `id: string`
- `username: string`
- `email: string`
- `nickname?: string`
- `avatarUrl?: string`
- `role: string`
- `createdAt: string` (ISO-8601 UTC string)

## 3) User table mapping

Entity: `com.xiaoce.agent.auth.domain.po.User`

Mapped columns:

- `id` -> `Long id`
- `academic_id` -> `String academicId`
- `username` -> `String username`
- `email` -> `String email`
- `password_hash` -> `String passwordHash`
- `status` -> `Integer status` (`0=disabled`, `1=enabled`)
- `created_at` -> `Instant createdAt`
- `updated_at` -> `Instant updatedAt`

Login checks:

- user exists by `username/email` (case-insensitive)
- `BCrypt` password match
- `status != 0`

## 4) Token and redis design

Access token:

- JWT signed with RSA256 (`RS256`)
- TTL: `15 min` (`900s`)
- used for API authorization only

Refresh token:

- JWT signed with RSA256 (`RS256`)
- TTL: `7 days` (`604800s`)
- includes `jti`
- server-side authority in Redis
- rotate on each refresh (old `jti` revoked, new `jti` persisted)

Redis keys:

- `agent:rt:tok:{jti}` -> `{userId}` with TTL = 7 days
- `agent:rt:usr:{userId}` -> set of jti with TTL = 7 days

Logout:

- revoke all refresh token jti for current user

## 5) Security baseline

- password hash: `BCrypt` strength 12
- stateless security: Spring Security + JWT filter
- public routes:
  - `/api/v1/auth/login`
  - `/api/v1/auth/register`
  - `/api/v1/auth/refresh`
- all other routes require bearer token

