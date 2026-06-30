## 1. Configuration & Setup

- [x] 1.1 Add spring-boot-starter-data-redis and commons-pool2 dependencies to pom.xml
- [x] 1.2 Add Redis configuration properties (host, port, lettuce pool) to application.yaml and profile-specific config as needed

## 2. Access Token Blacklisting

- [x] 2.1 Add a token blacklist service that stores Access Tokens in Redis with TTL equal to their remaining JWT lifetime
- [x] 2.2 Update logout handling to blacklist the current bearer Access Token when an Authorization header is present while preserving refresh-token cookie revocation
- [x] 2.3 Create a security filter in SecurityConfig to reject requests whose bearer Access Token is present in the Redis blacklist

## 3. Caching Role-Permissions

- [x] 3.1 Enable Spring Cache using @EnableCaching in the config package
- [x] 3.2 Move PermissionAuthorizationManager role-permission lookup/cache storage to Redis-backed cache entries keyed by role name
- [x] 3.3 Evict affected role-permission cache entries after role, permission, or role-permission assignment changes
- [x] 3.4 Add a startup or fallback path that reloads role-permission mappings from PostgreSQL when Redis cache entries are missing

## 4. Real-time Role Update Check

- [x] 4.1 Store role update timestamp in Redis when user's roles are updated
- [x] 4.2 Use a role update timestamp TTL at least equal to jwt.access-token-expiration
- [x] 4.3 Validate token issued-at time against the role-update timestamp in the security filter

## 5. Background Scheduled Cleanup Job

- [x] 5.1 Enable Spring Scheduling using @EnableScheduling
- [x] 5.2 Add a RefreshTokenRepository delete query for tokens where expiresAt is in the past or revoked = true
- [x] 5.3 Implement RefreshTokenCleanupJob running daily at 02:00 to purge expired/revoked tokens from PostgreSQL refresh_tokens

## 6. Verification & Testing

- [x] 6.1 Write integration tests for token blacklisting on logout
- [x] 6.2 Write integration tests for rejecting blacklisted Access Tokens on protected APIs
- [x] 6.3 Write integration tests for role updates forcing token refresh
- [x] 6.4 Write tests for Redis-backed role-permission cache hit and eviction behavior
- [x] 6.5 Write unit/integration tests for the cleanup job scheduler
