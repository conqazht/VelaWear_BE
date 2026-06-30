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

1. The old refresh token is marked `revoked = true`.
2. A new access token is generated.
3. A new refresh JWT is generated and stored as a hash.
4. The new token pair is returned to the client.

This keeps refresh token replay risk smaller than reusing the same long-lived token.
