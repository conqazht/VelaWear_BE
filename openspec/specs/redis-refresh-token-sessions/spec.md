# redis-refresh-token-sessions Specification

## Purpose
TBD - created by archiving change redis-authoritative-refresh-token-flow. Update Purpose after archive.

## Requirements
### Requirement: Redis-authoritative Refresh Token sessions
The system MUST treat Redis as the runtime authority for active Refresh Token sessions. A Refresh Token MUST be accepted only when its JWT is valid and Redis contains an active session entry for its `jti`.

#### Scenario: Refresh Token accepted with Redis hit
- **WHEN** a client calls the refresh endpoint with a valid Refresh Token JWT whose `jti` exists in Redis
- **THEN** the system issues a new Access Token according to the refresh flow

#### Scenario: Refresh Token rejected with Redis miss
- **WHEN** a client calls the refresh endpoint with a valid Refresh Token JWT whose `jti` does not exist in Redis
- **THEN** the system returns 401 Unauthorized and MUST NOT use PostgreSQL to allow the refresh

#### Scenario: Refresh Token unavailable with Redis error
- **WHEN** Redis cannot be reached or returns a client/timeout error while validating a Refresh Token session
- **THEN** the system returns 503 Service Unavailable and MUST NOT mark the PostgreSQL token row revoked because the session state is unknown

### Requirement: Refresh Token session creation on login
The system MUST create a Redis active Refresh Token session whenever a login issues a Refresh Token. The Redis session MUST be keyed by the Refresh JWT `jti` and MUST expire no later than the Refresh Token expiration time.

#### Scenario: Login stores active Refresh Token session
- **WHEN** user login succeeds and the system issues a Refresh Token
- **THEN** the system stores `auth:refresh:active:{jti}` in Redis with a TTL matching the remaining Refresh Token lifetime

#### Scenario: Login writes PostgreSQL audit row
- **WHEN** user login succeeds and the system issues a Refresh Token
- **THEN** the system stores the hashed Refresh Token in PostgreSQL `refresh_tokens` for audit/history

### Requirement: Refresh Token rotation
The system MUST rotate Refresh Tokens on successful refresh. The old Refresh Token session MUST stop being active in Redis, and a new Refresh Token session MUST be created in Redis before the new Refresh Token is returned to the client.

#### Scenario: Successful refresh rotates Refresh Token
- **WHEN** a client refreshes with a valid active Refresh Token
- **THEN** the system deletes or invalidates the old Redis session, creates a new Redis session for a new Refresh Token `jti`, marks the old PostgreSQL row revoked, writes a new PostgreSQL audit row, and returns the new Refresh Token

#### Scenario: Reuse of rotated Refresh Token is rejected
- **WHEN** a client tries to refresh with a Refresh Token whose old Redis session was removed by rotation
- **THEN** the system returns 401 Unauthorized

### Requirement: Logout invalidates Refresh Token Redis session
The system MUST invalidate the active Refresh Token session in Redis during logout when a Refresh Token is provided. PostgreSQL MUST be updated for audit using existing columns only.

#### Scenario: Logout removes Refresh Token Redis session
- **WHEN** user logs out with a valid Refresh Token cookie
- **THEN** the system removes `auth:refresh:active:{jti}` from Redis and marks the corresponding PostgreSQL token row revoked

#### Scenario: Logout still blacklists Access Token
- **WHEN** user logs out with a valid bearer Access Token
- **THEN** the system continues to blacklist the Access Token in Redis for its remaining lifetime

### Requirement: PostgreSQL remains audit-only for Refresh Token runtime validation
The system MUST keep PostgreSQL `refresh_tokens` for audit/history and scheduled cleanup, but MUST NOT use PostgreSQL as fallback authorization when Redis rejects or cannot verify a Refresh Token session.

#### Scenario: DB row exists but Redis session missing
- **WHEN** a Refresh Token has a PostgreSQL audit row but its Redis active session key is missing
- **THEN** the system returns 401 Unauthorized and may update existing PostgreSQL revoked fields for audit, without adding or requiring a revoke reason column

#### Scenario: Scheduled cleanup remains active
- **WHEN** the daily refresh token cleanup job runs
- **THEN** the system deletes expired or revoked PostgreSQL `refresh_tokens` audit rows as before

### Requirement: Redis local infrastructure
The system MUST provide local Docker Compose configuration for Redis and environment variables for Redis connection settings.

#### Scenario: Local Redis starts through Docker Compose
- **WHEN** developer starts the local Docker Compose stack
- **THEN** Redis is available on the configured Redis port for the Spring application

#### Scenario: Application connects through configured Redis settings
- **WHEN** the Spring application starts locally
- **THEN** it uses `REDIS_HOST`, `REDIS_PORT`, and optional `REDIS_PASSWORD` to connect through Spring Data Redis/Lettuce
