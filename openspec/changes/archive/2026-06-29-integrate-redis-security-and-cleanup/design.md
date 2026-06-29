## Context

Currently, the application has several security-state gaps:
1. Logout revokes refresh tokens but does not immediately invalidate still-valid Access Tokens.
2. `PermissionAuthorizationManager` keeps role-permission mappings in a JVM-local in-memory cache, which does not provide a shared multi-instance cache or coordinated invalidation.
3. Refresh tokens stay in PostgreSQL after expiration or revocation, leading to table bloat over time.

This design introduces Redis to handle high-frequency/low-latency tasks (blacklist, caching roles/permissions, stale token checking) and a background scheduler to keep the PostgreSQL refresh token table clean.

## Goals / Non-Goals

**Goals:**
- Implement an Access Token blacklist in Redis.
- Cache role-permission mapping in Redis behind the existing authorization path.
- Force Access Token revocation on user role changes using timestamps in Redis.
- Setup a background Scheduled task to clean up PostgreSQL refresh tokens.

**Non-Goals:**
- Moving the primary source of truth of Refresh Tokens from PostgreSQL to Redis. PostgreSQL will remain the persistent record of refresh tokens.

## Decisions

### 1. Redis Connection Pool
- **Decision:** Use Lettuce with connection pooling (`commons-pool2`).
- **Rationale:** Lettuce is thread-safe and more performant than Jedis for concurrent requests.

### 2. Spring Cache for Role-Permission
- **Decision:** Keep `PermissionAuthorizationManager` as the protected-endpoint authorization entry point, but replace or back its local `rolePermissionsCache` with Redis-backed cache entries keyed by role name, for example `role_permissions::ROLE_ADMIN` or `rbac:role:ROLE_ADMIN`.
- **Rationale:** The current authorization flow does not call `RoleRepository` per request; caching repository methods alone would miss the runtime permission check path. Caching at the authorization manager boundary preserves the existing security model while making the cache shared across instances.

### 3. Programmatic RedisTemplate for Access Token Blacklist
- **Decision:** Inject `StringRedisTemplate` into a dedicated token blacklist service used by logout handling and the security filter.
- **Rationale:** Requires dynamic TTL (remaining lifetime of JWT) which cannot be easily handled by standard method-level caching.

### 4. Timestamp-based Role Update Check
- **Decision:** Store `auth:role-updated-at:{userId}` with a timestamp in Redis when a user's role assignments change. Check whether `token.iat < role-updated-at` in the security filter after JWT decoding.
- **Rationale:** Handles multi-device token revocation gracefully.
- **TTL:** The timestamp key TTL MUST be at least `jwt.access-token-expiration` seconds, not a fixed 15 minutes, so stale Access Tokens cannot become valid again after the revocation marker expires.

### 5. Scheduled Cleanup Job
- **Decision:** Add `@Scheduled(cron = "0 0 2 * * *")` (daily at 02:00) to delete expired or revoked refresh tokens from PostgreSQL.
- **Rationale:** Keeps DB table size bounded.

## Risks / Trade-offs

- **[Risk]** Redis goes down and breaks authentication.
  - *Mitigation*: Configure connection timeouts and fallbacks. For security critical functions like the blacklist, fail-secure and throw a `503 Service Unavailable` or `500 Internal Server Error`.
- **[Risk]** Redis memory leaks.
  - *Mitigation*: Ensure blacklist keys use the Access Token's remaining lifetime and role-update keys use at least the configured Access Token lifetime.
