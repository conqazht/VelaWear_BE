# Auth Context

## Current Scope

Auth exposes public endpoints under `/api/v1/auth`:

- `POST /login`: authenticate email/password and return access + refresh tokens.
- `POST /register`: create a storefront user and assign the default `USER` role.
- `POST /refresh`: validate and rotate refresh token, then issue a new access token.
- `POST /logout`: revoke the provided refresh token.

## Token Rules

- Access token lifetime is configured by `JWT_ACCESS_TOKEN_EXPIRATION`, default `900` seconds.
- Refresh token lifetime is configured by `JWT_REFRESH_TOKEN_EXPIRATION`, default `259200` seconds.
- Production must provide separate `JWT_ACCESS_TOKEN_SECRET_KEY` and `JWT_REFRESH_TOKEN_SECRET_KEY`
  environment variables.
- Access tokens are JWTs signed by the access-token `JwtEncoder` with HS512.
- Refresh tokens are JWTs signed by the refresh-token `JwtEncoder` with HS512.
- Refresh JWTs include `jti`, `sub`, `userId`, `type=refresh`, `iat`, and `exp`.
- Refresh tokens are stored in `refresh_tokens.token` using SHA-512 of the raw refresh JWT,
  never as raw tokens.
- Login and refresh also set the raw refresh token into an `HttpOnly` cookie.
- Logout clears the refresh token cookie.
- `/me` returns the authenticated user's profile with role summaries (`id`, `name`) loaded from `user_role`.

## Refresh Rotation

When `/refresh` receives a valid refresh token:

1. The active Redis session is loaded and its SHA-512 token hash is verified.
2. The old PostgreSQL audit row is marked `revoked = true` and a new hashed row is prepared.
3. A Redis Lua compare-and-swap atomically creates one replacement session and removes the old session.
4. A request that loses the CAS receives the existing `401` response and its audit changes roll back.
5. Only the CAS winner receives the new access and refresh tokens.

The Lua script compares the complete expected session, creates the successor with
`SET ... PX ... NX`, then deletes the old key. Do not replace it with separate Redis
commands or a process-local lock; the application may run on multiple instances.

See [`docs/RACE_CONDITION_TESTING_VI.md`](../../../../../../../../docs/RACE_CONDITION_TESTING_VI.md)
for the real Redis/PostgreSQL concurrency tests.
