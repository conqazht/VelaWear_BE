## ADDED Requirements

### Requirement: Access Token Blacklisting on Logout
The system MUST store logged-out Access Tokens in Redis with a TTL matching their remaining lifespan. The system MUST reject any request containing a blacklisted Access Token.

#### Scenario: Successful Token Blacklisting on Logout
- **WHEN** user calls the logout API with a valid `Authorization: Bearer <accessToken>` header
- **THEN** system saves the Access Token in the Redis blacklist with a TTL equal to its remaining validity duration

#### Scenario: Logout Still Revokes Refresh Token
- **WHEN** user calls the logout API with a valid `refresh_token` cookie
- **THEN** system revokes the corresponding Refresh Token in PostgreSQL and clears the cookie

#### Scenario: Deny Request with Blacklisted Token
- **WHEN** client sends a request with an Access Token present in the Redis blacklist
- **THEN** system returns 401 Unauthorized

### Requirement: Caching Role and Permission mappings in Redis
The system MUST cache role-to-permission mappings in Redis for the existing authorization flow. The system MUST evict role-to-permission cache when permissions of that role are updated.

#### Scenario: Cache Hit on Permission Check
- **WHEN** client requests a protected API and the role-permission mapping exists in Redis
- **THEN** system grants or denies access based on the cached mapping without querying PostgreSQL

#### Scenario: Cache Eviction on Permission Update
- **WHEN** administrator updates the permission mapping for a Role
- **THEN** system evicts the corresponding Role cache key from Redis

### Requirement: Access Token Revocation on Role Update
The system MUST store the role update timestamp of a user in Redis when their role assignments are modified. The system MUST reject any Access Token issued before this timestamp. The Redis timestamp key MUST live for at least the configured Access Token lifetime.

#### Scenario: Force Token Refresh on Role Change
- **WHEN** user requests a protected API with an Access Token issued before the role update timestamp stored in Redis
- **THEN** system rejects the request and prompts the client to refresh the token

### Requirement: Scheduled Refresh Token Purge
The system MUST run a scheduled background task daily at 02:00 to purge expired and revoked refresh tokens from the database.

#### Scenario: Purge Expired and Revoked Refresh Tokens
- **WHEN** the cleanup scheduler runs daily at 02:00
- **THEN** system deletes all Refresh Tokens from the PostgreSQL `refresh_tokens` table where expiresAt is in the past or revoked = true
