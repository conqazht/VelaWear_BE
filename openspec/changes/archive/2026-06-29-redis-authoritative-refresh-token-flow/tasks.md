## 1. Redis Infrastructure

- [x] 1.1 Add a Redis service, healthcheck, and optional volume to `docker-compose.yml`
- [x] 1.2 Add `REDIS_HOST`, `REDIS_PORT`, and optional `REDIS_PASSWORD` to `.env` and `.env.example`
- [x] 1.3 Verify Spring Data Redis/Lettuce configuration still uses the configured Redis host, port, password, and pool settings

## 2. Refresh Token Session Model

- [x] 2.1 Add a Redis Refresh Token session DTO/value containing `jti`, `userId`, `tokenHash`, `deviceInfo`, `ipAddress`, `issuedAt`, and `expiresAt`
- [x] 2.2 Add a Refresh Token Redis session service for create, get, delete, and rotate operations using key `auth:refresh:active:{jti}`
- [x] 2.3 Store Redis sessions with TTL equal to the remaining Refresh Token lifetime
- [x] 2.4 Map Redis miss to an auth invalid result and Redis client/timeout/connection errors to a service-unavailable result

## 3. Login Flow

- [x] 3.1 Ensure generated Refresh JWTs include a unique `jti`
- [x] 3.2 On successful login, write the active Refresh Token session to Redis before returning the token pair
- [x] 3.3 Continue inserting the hashed Refresh Token into PostgreSQL `refresh_tokens` as audit/history
- [x] 3.4 If Redis session creation fails during login, fail the login response instead of returning a Refresh Token that Redis cannot validate

## 4. Refresh Flow

- [x] 4.1 Verify Refresh JWT signature and `type=refresh` with the refresh-token decoder
- [x] 4.2 Extract `jti` and require a Redis hit for `auth:refresh:active:{jti}`
- [x] 4.3 Return 401 when Redis reports a missing Refresh Token session and do not use PostgreSQL to allow the refresh
- [x] 4.4 Return 503 when Redis cannot be checked due to Redis connection, timeout, or client errors
- [x] 4.5 Rotate Refresh Tokens on successful refresh by invalidating the old Redis session and creating a new Redis session
- [x] 4.6 Mark the old PostgreSQL audit row revoked and insert a new PostgreSQL audit row during successful rotation
- [x] 4.7 Return a new Refresh Token to the client after successful rotation

## 5. Logout Flow

- [x] 5.1 On logout with a Refresh Token cookie, verify the Refresh JWT and extract `jti`
- [x] 5.2 Delete the Redis active session for the Refresh Token `jti`
- [x] 5.3 Mark the corresponding PostgreSQL Refresh Token row revoked using existing columns only
- [x] 5.4 Keep Access Token blacklist behavior unchanged for bearer-token logout
- [x] 5.5 Clear the Refresh Token cookie on successful logout and on 401 Refresh Token missing-session responses

## 6. PostgreSQL Audit and Cleanup

- [x] 6.1 Keep `refresh_tokens` as audit/history and do not add `revokeReason` or `revoke_reason`
- [x] 6.2 Preserve the daily 02:00 scheduled cleanup job for expired or revoked PostgreSQL Refresh Token rows
- [x] 6.3 On Redis miss for an otherwise valid Refresh JWT, optionally mark the existing PostgreSQL row revoked for audit without using it to authorize refresh

## 7. Testing

- [x] 7.1 Add tests that login writes both Redis active session and PostgreSQL audit row
- [x] 7.2 Add tests that refresh succeeds only when Redis session exists
- [x] 7.3 Add tests that Redis miss returns 401 and does not fallback to PostgreSQL
- [x] 7.4 Add tests that Redis error returns 503 and does not mark the PostgreSQL row revoked
- [x] 7.5 Add tests that successful refresh rotates Redis sessions and returns a new Refresh Token
- [x] 7.6 Add tests that reused old Refresh Tokens after rotation return 401
- [x] 7.7 Add tests that logout deletes Redis Refresh Token session, marks PostgreSQL row revoked, and still blacklists Access Token
- [x] 7.8 Run focused auth/Redis integration tests and the full Maven test suite
