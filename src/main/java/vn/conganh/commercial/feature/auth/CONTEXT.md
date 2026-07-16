# Auth Context

## Current Scope

Auth exposes public endpoints under `/api/v1/auth`:

- `POST /login`: authenticate email/password and return access + refresh tokens.
- `POST /register`: consume a `REGISTER` proof, create a storefront user and assign
  the default `USER` role.
- `POST /refresh`: validate and rotate refresh token, then issue a new access token.
- `POST /logout`: revoke the provided refresh token.
- `POST /otp/request`: create a challenge for `REGISTER`, `FORGOT_PASSWORD`, or
  authenticated `CHANGE_EMAIL`.
- `POST /otp/verify`: verify `{challengeId, code}` and return a five-minute,
  single-use proof token.
- `POST /forgot-password/reset`, `PUT /me/email`, and `PUT /me/password`: sensitive
  changes that revoke every session and require a new login.

## Token Rules

- Access token lifetime is configured by `JWT_ACCESS_TOKEN_EXPIRATION`, default `900` seconds.
- Refresh token lifetime is configured by `JWT_REFRESH_TOKEN_EXPIRATION`, default `259200` seconds.
- Production must provide separate `JWT_ACCESS_TOKEN_SECRET_KEY` and `JWT_REFRESH_TOKEN_SECRET_KEY`
  environment variables.
- Access tokens are JWTs signed by the access-token `JwtEncoder` with HS512.
- Refresh tokens are JWTs signed by the refresh-token `JwtEncoder` with HS512.
- Access and refresh JWTs include `userId` and `securityVersion`. Refresh JWTs also
  include `jti`, `sub`, `type=refresh`, `iat`, and `exp`.
- Every authenticated JWT request compares its `securityVersion` with PostgreSQL.
  A missing/stale claim returns `401 SESSION_REVOKED`.
- Refresh tokens are stored in `refresh_tokens.token` using SHA-512 of the raw refresh JWT,
  never as raw tokens.
- Login and refresh also set the raw refresh token into an `HttpOnly` cookie.
- Cookie `Secure` depends only on `request.isSecure()` after the trusted-proxy policy;
  application code never trusts `X-Forwarded-Proto` directly.
- Logout clears the refresh token cookie and revokes only the current session.
- Password reset/change, email change and role change atomically increment
  `users.security_version`, revoke all refresh audit rows, then clean Redis sessions
  after commit.
- `/me` returns the authenticated user's profile with role summaries (`id`, `name`) loaded from `user_role`.

## Refresh Rotation

When `/refresh` receives a valid refresh token:

1. The active Redis session is loaded and its SHA-512 token hash is verified.
2. The old PostgreSQL audit row is marked `revoked = true` and a new hashed row is prepared.
3. A Redis Lua compare-and-swap atomically creates one replacement session and removes the old session.
4. A request that loses the CAS receives the existing `401` response and its audit changes roll back.
5. Only the CAS winner receives the new access and refresh tokens.

The Lua script compares the complete expected session, including `securityVersion`,
creates the successor with `SET ... PX ... NX`, updates the
`auth:refresh:user:{userId}` ZSET index, then deletes the old key. Do not replace it
with separate Redis commands or a process-local lock; the application may run on
multiple instances.

## OTP Proof and Abuse Controls

- OTP Redis state uses `auth:otp:v2:*`. Raw OTP/proof/email is not used as a Redis
  key; domain-separated HMAC-SHA256 is keyed by `SECURITY_HMAC_SECRET`.
- Request returns a random `challengeId`; verify returns a random proof token. The
  final endpoint derives its required purpose and consumes the proof atomically.
- `CHANGE_EMAIL` binds challenge/proof to both the new email and authenticated
  `userId`. Forgot-password for an unknown email returns a decoy challenge without
  sending mail.
- Four Redis Lua scripts reserve cooldown, publish challenge, verify/issue proof and
  consume proof without check-then-act races. Resend success replaces the previous
  challenge; delivery failure keeps it and still consumes cooldown.
- `AuthRateLimitFilter` applies global/IP buckets before body parsing; services add
  normalized email, account, user and refresh JTI dimensions. Redis failure is
  fail-closed.
- `ClientIpResolver` reads only `request.getRemoteAddr()`. Direct deployments use
  `server.forward-headers-strategy=NONE`; `NATIVE` requires an explicit non-wildcard
  Tomcat trusted-proxy regex.
- Actuator metrics are in-memory. Health/info are public; metrics require
  `ROLE_ADMIN`. Production security logs are structured and must not contain OTP,
  proof, token, password, email or raw IP.

## Google OAuth2 Authorization State

- The browser cookie `oauth2_auth_request` contains only a random 32-byte Base64URL
  nonce. It never contains a Java-serialized `OAuth2AuthorizationRequest`.
- The request is stored as an explicit, bounded JSON DTO in Redis under
  `auth:oauth2:v2:request:{hmac(nonce)}`. Its default TTL and cookie `Max-Age` are
  180 seconds and are configured by `OAUTH2_AUTHORIZATION_REQUEST_TTL_SECONDS`.
- Starting another Google login deletes the previous request. The callback uses
  Redis `GETDEL`, so state/OIDC nonce/PKCE data can be consumed only once even when
  callbacks race or are replayed.
- Missing, expired and legacy serialized cookies are rejected. Invalid JSON is
  consumed before rejection. Redis failures fail closed; there is no fallback to a
  client-side Java object or HTTP session.
- All backend instances must share Redis and the same `SECURITY_HMAC_SECRET`. Never
  log the browser nonce, OAuth state, raw OIDC nonce, PKCE verifier or Redis payload.

See [`docs/RACE_CONDITION_TESTING_VI.md`](../../../../../../../../docs/RACE_CONDITION_TESTING_VI.md)
for the real Redis/PostgreSQL concurrency tests.

The complete API flows, Redis keys, limiter thresholds, trusted-proxy setup,
rollout rules and troubleshooting are documented in
[`docs/OTP_SECURITY_FLOW_VI.md`](../../../../../../../../docs/OTP_SECURITY_FLOW_VI.md).
