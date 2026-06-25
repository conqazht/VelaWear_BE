# Auth Login Spec

## Scenario 1: Successful login
**Given** a user exists with email "user@example.com" and password "Password123!"
**When** client sends `POST /api/v1/auth/login` with `{"email":"user@example.com","password":"Password123!"}`
**Then** response status is 200
**And** `data.accessToken` is a non-empty JWT string
**And** `data.refreshToken` is a non-empty UUID string
**And** `data.expiresIn` is 900 (seconds)
**And** refresh token is persisted in `refresh_tokens` table with `revoked=false`

## Scenario 2: Invalid password
**Given** a user exists with email "user@example.com" and password "Password123!"
**When** client sends `POST /api/v1/auth/login` with `{"email":"user@example.com","password":"wrongpassword"}`
**Then** response status is 401
**And** `message` is "Invalid email or password"

## Scenario 3: Unknown email
**Given** no user exists with email "unknown@example.com"
**When** client sends `POST /api/v1/auth/login` with `{"email":"unknown@example.com","password":"Password123!"}`
**Then** response status is 401
**And** `message` is "Invalid email or password"

## Scenario 4: Soft-deleted user cannot login
**Given** a user exists with email "deleted@example.com" but `deleted_at` is not null
**When** client sends `POST /api/v1/auth/login` with `{"email":"deleted@example.com","password":"Password123!"}`
**Then** response status is 401
**And** `message` is "Invalid email or password"

## Scenario 5: Missing email
**When** client sends `POST /api/v1/auth/login` with `{"password":"Password123!"}`
**Then** response status is 400
**And** `message` is "Validation failed"

## Scenario 6: Missing password
**When** client sends `POST /api/v1/auth/login` with `{"email":"user@example.com"}`
**Then** response status is 400
**And** `message` is "Validation failed"

## Scenario 7: Invalid email format
**When** client sends `POST /api/v1/auth/login` with `{"email":"not-an-email","password":"Password123!"}`
**Then** response status is 400
**And** `message` is "Validation failed"

## Scenario 8: JWT claims contain correct data
**Given** a user exists with email "user@example.com", id=1, and role "USER"
**When** client sends valid login request
**Then** decode the `accessToken` JWT and verify:
- `sub` = "user@example.com"
- `userId` = 1
- `roles` contains "USER"
- `exp` is approximately 15 minutes from now
