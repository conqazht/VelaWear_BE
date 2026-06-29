## Why

Refresh Tokens are currently validated from PostgreSQL state only, while Redis is already used for access-token blacklist, role-change markers, and RBAC cache. Adding Redis-authoritative Refresh Token sessions lets the project model a production-style session store while still keeping PostgreSQL rows for learning, audit, and cleanup.

## What Changes

- Add Redis-backed active Refresh Token sessions keyed by Refresh JWT `jti`.
- Treat Redis as the runtime authority for Refresh Token validity: Redis hit means active, Redis miss means invalid, and Redis error means the refresh session store is unavailable.
- Keep PostgreSQL `refresh_tokens` as audit/history storage only: insert issued Refresh Tokens, mark revoked on logout/rotation/missing-session handling, and keep the existing scheduled cleanup job.
- Do not fallback to PostgreSQL to validate Refresh Tokens when Redis misses or errors.
- Add Redis infrastructure for local development through Docker Compose and environment variables.
- Add Refresh Token rotation so each successful refresh invalidates the old Redis session and issues a new Refresh Token session.
- Do not add or rely on a `revokeReason`/`revoke_reason` database column.

## Capabilities

### New Capabilities
- `redis-refresh-token-sessions`: Runtime Refresh Token sessions are stored and validated through Redis, while PostgreSQL remains audit/history storage.

### Modified Capabilities

## Impact

- Affected auth flow: login, refresh, logout, refresh token creation/revocation, and cookie clearing behavior.
- Affected persistence: `refresh_tokens` remains in PostgreSQL for audit/history and scheduled cleanup.
- Affected Redis usage: new Refresh Token session keys and optional user-to-session index keys.
- Affected infrastructure: `docker-compose.yml`, `.env`, `.env.example`, and Redis connection settings.
- Affected tests: auth controller/service tests and Redis integration tests must cover hit, miss, error, logout, and rotation behavior.
