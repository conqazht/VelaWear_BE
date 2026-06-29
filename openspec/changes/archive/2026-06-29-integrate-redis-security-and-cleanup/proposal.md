## Why

Our e-commerce backend needs a scalable and secure authentication and authorization mechanism. Access tokens are currently stateless JWTs, so logout only revokes the refresh token and cannot immediately reject a still-valid access token. RBAC permissions are currently loaded into an in-memory authorization cache, which works for a single instance but does not give multi-instance deployments a shared cache or invalidation path. Refresh tokens remain persisted in PostgreSQL for auditability, but expired and revoked rows need routine cleanup to prevent table bloat.

This change integrates Redis for short-lived security state: access-token blacklist entries, shared role-permission cache entries, and user-specific role update timestamps used to reject stale JWTs.

## What Changes

- Integrate Spring Data Redis and commons-pool2 for Lettuce connection pooling.
- Implement an Access Token blacklist in Redis with a TTL based on the token's remaining lifetime.
- Update logout so it can blacklist the current bearer Access Token when an `Authorization: Bearer ...` header is present, while still revoking the refresh-token cookie when present.
- Move RBAC role-permission caching behind the existing authorization path (`PermissionAuthorizationManager`) using Redis keys such as `rbac:role:{roleName}` or an equivalent Spring Cache name/key pair.
- Evict the RBAC cache whenever permissions, roles, or role-permission assignments change.
- Implement a role-change revocation checker using `auth:role-updated-at:{userId}` timestamps in Redis.
- Setup a Spring `@Scheduled` background task running daily at 02:00 to purge expired and revoked refresh tokens from the PostgreSQL `refresh_tokens` table.

## Capabilities

### New Capabilities
- `redis-security-integration-and-cleanup`: Integration of Redis for security operations and automatic background cleanup of refresh tokens.

### Modified Capabilities

## Impact

- Adds new dependencies (`spring-boot-starter-data-redis` and `commons-pool2`).
- Updates `application.yaml`, profile config, `SecurityConfig.java`, auth logout handling, and the RBAC authorization/cache path.
- Adds a new `@Scheduled` background task class `RefreshTokenCleanupJob`.
