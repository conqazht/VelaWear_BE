## Context

The application already uses Redis for access-token blacklist, role-update markers, and RBAC permission cache. Refresh Tokens are currently JWTs signed with the refresh secret, hashed with SHA-512, and persisted in PostgreSQL `refresh_tokens`. Runtime Refresh Token validation currently depends on PostgreSQL state.

This change introduces Redis-backed active Refresh Token sessions. PostgreSQL remains useful for audit/history, device/IP visibility, and scheduled cleanup, but it no longer decides whether a Refresh Token is currently active.

## Goals / Non-Goals

**Goals:**
- Make Redis the runtime authority for active Refresh Token sessions.
- Keep PostgreSQL `refresh_tokens` as audit/history storage and continue scheduled cleanup.
- Add `jti`-keyed Redis session entries for Refresh Tokens.
- Rotate Refresh Tokens on refresh: old session becomes invalid, new session is created.
- Return distinct outcomes for Redis miss and Redis error.
- Add Redis service configuration for local Docker Compose development.
- Do not add or require a `revokeReason` or `revoke_reason` column.

**Non-Goals:**
- Do not fallback to PostgreSQL to validate Refresh Tokens when Redis misses or errors.
- Do not remove the `refresh_tokens` table.
- Do not make every protected API request depend on Refresh Token Redis session checks.
- Do not introduce a new database column for revoke reason in this change.

## Decisions

### 1. Redis-authoritative runtime model
- **Decision:** A Refresh Token is runtime-valid only when its JWT is valid and Redis contains `auth:refresh:active:{jti}`.
- **Rationale:** This makes Redis the active session store and gives logout, rotation, and admin revocation immediate runtime effect.
- **Alternatives considered:**
  - PostgreSQL fallback on Redis miss/error: better availability, but PostgreSQL becomes a second authority and can revive sessions Redis has invalidated.
  - PostgreSQL-only validation: simpler, but does not teach Redis session-store behavior.

### 2. PostgreSQL as audit/history only
- **Decision:** Continue writing issued Refresh Tokens to PostgreSQL and mark rows revoked during logout/rotation/missing-session handling, but never use PostgreSQL to allow a Refresh Token after Redis rejects or cannot verify it.
- **Rationale:** The DB remains useful for learning persistence, audit inspection, cleanup, and history without weakening Redis authority.
- **Constraint:** The current DB schema has no revoke reason column, so audit updates are limited to existing fields such as `revoked` and timestamps if available.

### 3. Refresh Token Redis key model
- **Decision:** Store active sessions at `auth:refresh:active:{jti}` with TTL equal to the remaining Refresh Token lifetime.
- **Value:** Store enough data to validate and inspect the Redis session, such as `jti`, `userId`, `tokenHash`, `deviceInfo`, `ipAddress`, `issuedAt`, and `expiresAt`.
- **Rationale:** `jti` is stable, unique per Refresh Token, and enables clean rotation/replay detection.

### 4. Refresh Token rotation
- **Decision:** Successful `/auth/refresh` invalidates the old Redis session and creates a new Refresh Token, new `jti`, new Redis session, and new PostgreSQL audit row.
- **Rationale:** Rotation prevents a previously used Refresh Token from being replayed.
- **Ordering:** Redis rotation must succeed before returning the new Refresh Token to the client. PostgreSQL audit writes should happen in the same service flow, but DB audit must not be used to validate a Redis miss.

### 5. Redis miss versus Redis error
- **Decision:** Redis miss returns `401 Unauthorized`; Redis connection/timeout/client errors return `503 Service Unavailable`.
- **Rationale:** A miss means Redis answered that the session is absent. An error means the authority cannot be checked, so the system should not claim the token is invalid.
- **Client behavior expectation:** Clients clear auth state on `401`, but keep auth state and retry later on `503`.

### 6. Docker Compose Redis service
- **Decision:** Add a Redis service to `docker-compose.yml` and expose Redis configuration through `.env` / `.env.example`.
- **Rationale:** The code already uses Spring Data Redis/Lettuce; local development needs a reproducible Redis server.

## Risks / Trade-offs

- **[Risk] Redis data loss logs users out.** Redis is the authority, so missing session keys invalidate Refresh Tokens even if DB audit rows still exist. Mitigation: use Docker volume locally, and use Redis persistence/HA/noeviction in production-like deployments.
- **[Risk] Redis outage blocks refresh.** Refresh endpoint must return `503` on Redis errors. Mitigation: frontend should retry later and avoid clearing cookies on `503`.
- **[Risk] PostgreSQL audit can look confusing.** DB rows can exist even when Redis has invalidated the runtime session. Mitigation: mark `revoked=true` on logout/rotation and optionally on Redis miss; do not interpret DB rows as active sessions.
- **[Risk] Rotation race conditions.** Parallel refresh calls can race. Mitigation: rotate by checking old Redis key and deleting/setting session state atomically where practical.
- **[Risk] No revoke reason column.** Audit cannot distinguish LOGOUT, ROTATED, and REDIS_SESSION_MISSING in the current schema. Mitigation: keep scope aligned with the existing table and use logs/tests for reason-specific behavior.

## Migration Plan

1. Add Redis Docker Compose service and env variables.
2. Add Refresh Token session model/service for Redis.
3. Add `jti` extraction/validation from Refresh JWT.
4. Update login to write both Redis active session and PostgreSQL audit row.
5. Update refresh to require Redis hit and rotate the Refresh Token.
6. Update logout to delete Redis session and mark DB row revoked.
7. Keep the scheduled PostgreSQL cleanup job unchanged.
8. Add tests for Redis hit, miss, error, rotation, logout, and DB audit writes.

## Open Questions

- Should login revoke older sessions for the same user/device, or allow multiple active sessions?
- Should Redis session rotation use a Lua script for atomic check/delete/set in the first implementation or a simpler service flow with tests?
- Should a future change add DB fields like `jti`, `revokedAt`, or `revokeReason` for richer audit, or keep audit minimal for now?
