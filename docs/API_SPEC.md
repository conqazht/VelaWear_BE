# API Specification

> All endpoints return `ApiResponse<T>` wrapper.
> Update this file whenever endpoints change.
> Sale Campaign design, state machine và race-condition notes: [SALE_CAMPAIGN_BACKEND.md](./SALE_CAMPAIGN_BACKEND.md).
> Storefront Catalog, effective pricing và verified review: [STOREFRONT_CATALOG_UX_BACKEND_VI.md](./STOREFRONT_CATALOG_UX_BACKEND_VI.md).
> OTP/Auth hardening, Redis invariants và trusted-proxy notes: [OTP_SECURITY_FLOW_VI.md](./OTP_SECURITY_FLOW_VI.md).

---

## Base URL

Current controllers use Spring Boot 4 request mapping versioning:

```text
Development: http://localhost:8080/api
Production:  https://api.example.com/api
API version: 1
```

When API version negotiation is configured, clients must send version `1` according to the selected project strategy.

---

## Authentication

All business endpoints require JWT in:

```text
Authorization: Bearer <accessToken>
```

Public endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Login and receive access/refresh tokens |
| POST | `/api/v1/auth/register` | Register customer/user account (requires REGISTER OTP) |
| POST | `/api/v1/auth/refresh` | Rotate refresh token and issue new access token |
| POST | `/api/v1/auth/logout` | Revoke refresh token |
| POST | `/api/v1/auth/otp/request` | Request OTP challenge; `CHANGE_EMAIL` requires JWT |
| POST | `/api/v1/auth/otp/verify` | Verify challenge and receive single-use proof |
| POST | `/api/v1/auth/forgot-password/reset` | Reset password using OTP proof |
| GET | `/oauth2/authorization/google` | Start Google OAuth2 Login |
| GET | `/login/oauth2/code/google` | Google OAuth2 callback managed by Spring Security |
| GET | `/api/v1/sales` | List published, non-ended STANDARD/FLASH campaigns; phase is returned per row |
| GET | `/api/v1/sales/{code}` | Public campaign detail and pricing |
| GET | `/api/v1/storefront/products` | Storefront search, facets, effective-price sort and one-based pagination |
| GET | `/api/v1/reviews/product/{productId}` | Public product reviews; no private order/user IDs |
| GET | `/api/v1/reviews/product/{productId}/summary` | Public review summary and star distribution |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Build/application information |
| GET | `/v3/api-docs/**` | OpenAPI docs |
| GET | `/swagger-ui/**` | Swagger UI |

> `PUT /api/v1/auth/me/email`, `PUT /api/v1/auth/me/password`, `GET /api/v1/auth/me`
> and `/actuator/metrics/**` require JWT; metrics additionally require `ROLE_ADMIN`.
> Auth endpoints are implemented. Access tokens and refresh tokens are signed with HS512.
> Raw refresh JWTs are returned to clients, while the database stores only SHA-512 hashes for revoke/rotate.
> Both JWT types carry `securityVersion`; a missing/stale version returns `401 SESSION_REVOKED`.

Auth token configuration:

| `JWT_ACCESS_TOKEN_SECRET_KEY` | dev fallback only | HMAC signing secret for Access tokens. Required in production. |
| `JWT_REFRESH_TOKEN_SECRET_KEY` | dev fallback only | HMAC signing secret for Refresh tokens. Required in production. |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900` | Access token lifetime in seconds, 15 minutes by default. |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `259200` | Refresh token lifetime in seconds, 3 days by default. |
| `SECURITY_HMAC_SECRET` | required in `prod`, minimum 32 bytes | Independent HMAC key for OTP/proof, limiter subjects and blacklist keys; never reuse a JWT key. |

Token algorithm:

| Token | Algorithm |
|-------|-----------|
| Access token | `HS512` |
| Refresh token | `HS512` |
| Refresh token storage | `SHA-512` hash of raw refresh JWT |

---

## Response Format

All successful single-resource responses follow:

```json
{
  "statusCode": 200,
  "data": {},
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

Collection endpoints that return `ResultPaginationDTO` use the paginated
response contract below.

**Query parameters for paginated list endpoints:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | integer | `1` | 1-based page number |
| `size` | integer | `10` | Number of records per page |
| `sort` | string | Spring default | Spring `Pageable` sort expression, for example `createdAt,desc` |

**Paginated Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": {
      "page": 1,
      "pageSize": 10,
      "pages": 5,
      "total": 50
    },
    "result": []
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

Created responses:

```json
{
  "statusCode": 201,
  "data": {},
  "message": "Created",
  "timestamp": "2026-06-14T21:00:00"
}
```

Validation errors include field details:

```json
{
  "statusCode": 400,
  "data": {
    "email": "Invalid email format",
    "password": "Password must be 8-100 characters"
  },
  "message": "Validation failed",
  "timestamp": "2026-06-14T21:00:00"
}
```

Business errors include a stable machine-readable `code`. Frontend must branch on
`code`, not parse the localized `message`. Successful responses may omit or set
`code = null`.

```json
{
  "statusCode": 409,
  "code": "FLASH_SALE_SOLD_OUT",
  "data": {
    "variantId": 41,
    "requestedQuantity": 2,
    "remainingQuantity": 1
  },
  "message": "Số lượng Flash Sale còn lại không đủ",
  "timestamp": "2026-07-15T13:20:10Z"
}
```

Common application errors:

| HTTP status | Meaning |
|-------------|---------|
| `400 Bad Request` | Validation or malformed request |
| `401 Unauthorized` | Missing, expired, or invalid token |
| `403 Forbidden` | Authenticated user lacks RBAC permission |
| `404 Not Found` | Resource does not exist or was soft-deleted |
| `409 Conflict` | Duplicate or invalid business state |
| `429 Too Many Requests` | Auth/OTP quota exceeded; always includes `Retry-After` and `data.retryAfterSeconds` |
| `503 Service Unavailable` | Security state/email provider unavailable; Auth/OTP fails closed |
| `500 Internal Server Error` | Unexpected server error |

Stable Auth/OTP codes are `OTP_INVALID_OR_EXPIRED`,
`OTP_ATTEMPTS_EXHAUSTED`, `OTP_RATE_LIMITED`,
`OTP_PROOF_INVALID_OR_EXPIRED`, `AUTH_RATE_LIMITED`, `SESSION_REVOKED`,
`OTP_SERVICE_UNAVAILABLE`, and `OTP_DELIVERY_UNAVAILABLE`. A `429` response never
exposes the internal policy/dimension or remaining attempts.

```json
{
  "statusCode": 404,
  "data": null,
  "message": "User not found with id: 1",
  "timestamp": "2026-06-14T21:00:00"
}
```


## Dynamic List Filters

All existing paginated `GET` list endpoints accept feature-specific query
parameters in addition to `page`, `size`, and `sort`.

Filter behavior:

- Null or blank query values are ignored.
- String filters use trimmed case-insensitive contains matching.
- IDs, enums, and booleans use exact matching.
- Numeric and date ranges are inclusive.
- Invalid enum values, malformed dates, and `from > to` ranges return `400 Bad Request`.
- Soft-deleted rows are always excluded for users, brands, categories, products, and product variants.
- Path-scoped list endpoints enforce the path id; a conflicting query id returns `400 Bad Request`.
- Sorting của các list API quản trị được delegated cho Spring `Pageable`; riêng storefront catalog và public review dùng enum whitelist được mô tả bên dưới.

Supported filters:

| Endpoint | Query filters |
|----------|---------------|
| `GET /brands` | `name`, `slug`, `status`, `createdFrom`, `createdTo` |
| `GET /categories` | `parentId`, `name`, `slug`, `status`, `createdFrom`, `createdTo` |
| `GET /colors` | `name`, `hexCode` |
| `GET /sizes` | `name` |
| `GET /products` | `categoryId`, `brandId`, `name`, `slug`, `status`, `createdFrom`, `createdTo` |
| `GET /product-variants` | `productId`, `colorId`, `sizeId`, `sku`, `status`, `priceFrom`, `priceTo`, `stockFrom`, `stockTo`, `createdFrom`, `createdTo` |
| `GET /sale-campaigns` | `search` (name/code), `type`, `status`, `phase` |
| `GET /coupons` | `code`, `type`, `status`, `valueFrom`, `valueTo`, `minOrderAmountFrom`, `minOrderAmountTo`, `maxDiscountFrom`, `maxDiscountTo`, `usageLimitFrom`, `usageLimitTo`, `usedCountFrom`, `usedCountTo`, `startFrom`, `startTo`, `endFrom`, `endTo` |
| `GET /users` | `fullName`, `email`, `gender`, `birthDateFrom`, `birthDateTo`, `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo` |
| `GET /roles` | `name`, `description`, `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo` |
| `GET /permissions` | `name`, `apiPath`, `method`, `module`, `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo` |
| `GET /user-addresses` | `userId`, `receiverName`, `phone`, `province`, `district`, `ward`, `isDefault` |
| `GET /carts` | `userId`, `createdFrom`, `createdTo` |
| `GET /orders` | `userId`, `orderCode`, `status`, `paymentMethod`, `paymentStatus`, `receiverName`, `receiverPhone`, `finalAmountFrom`, `finalAmountTo`, `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo` |
| `GET /orders/user/{userId}` | Same as `/orders`, but `userId` is enforced from the path |
| `GET /orders/{id}/status-histories` | `fromStatus`, `toStatus`, `changedBy`, `reason`, `createdFrom`, `createdTo`; `orderId` is enforced from the path |
| `GET /payments` | `orderId`, `provider`, `transactionCode`, `status`, `amountFrom`, `amountTo`, `paidFrom`, `paidTo`, `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo` |
| `GET /reviews` | `userId`, `orderId`, `orderItemId`, `ratingFrom`, `ratingTo`, `comment`, `createdFrom`, `createdTo` |
| `GET /reviews/user/{userId}` | Same as `/reviews`, but `userId` is enforced from the path |
| `GET /reviews/order/{orderId}` | Same as `/reviews`, but `orderId` is enforced from the path |
| `GET /reviews/order-item/{orderItemId}` | Same as `/reviews`, but `orderItemId` is enforced from the path |
| `GET /reviews/product/{productId}` | `rating` (1–5), `sort` (`newest`, `oldest`, `rating-high`, `rating-low`); `page` 1-based, default `size=10` |
| `GET /reviews/me` | `orderId` tùy chọn; luôn scope theo JWT principal |
| `GET /storefront/products` | `q`, `categorySlugs`, `colorIds`, `sizeIds`, `minPrice`, `maxPrice`, `sort`, `page`, `size`, `locale` |
| `GET /wishlists` | `userId`, `productId`, `createdFrom`, `createdTo` |

---

## 1. Auth Implemented

### POST /api/v1/auth/login Public

Login and receive JWT tokens.

**Request Body:**

```json
{
  "email": "admin@example.com",
  "password": "password123"
}
```

**Success Response (200):**

Headers:

```http
Set-Cookie: refresh_token=<refreshToken>; Max-Age=259200; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Lax
```

```json
{
  "statusCode": 200,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Missing email or password |
| 401 | Invalid credentials |
| 401 | Soft-deleted user |

---

### GET /oauth2/authorization/google Public

Starts Google OAuth2 Login. Spring Security redirects to Google after the backend
stores an explicit authorization-request JSON document in Redis. The browser cookie
`oauth2_auth_request` contains only a random 43-character nonce; it never contains a
Java-serialized object.

Redis state expires after `OAUTH2_AUTHORIZATION_REQUEST_TTL_SECONDS` (default 180
seconds). Starting another Google Login invalidates the previous flow for the same
browser.

### GET /login/oauth2/code/google Public callback

Spring Security callback. The backend atomically consumes the Redis state with
`GETDEL`, validates OAuth2 state/OIDC nonce/PKCE, and then redirects to the configured
frontend success or failure URL. Concurrent callbacks and replay cannot reuse the
same state. Missing/expired/legacy cookies and Redis failures fail closed.

These routes are browser navigation endpoints, so they do not use the standard JSON
response envelope. Frontend request/response contracts are unchanged by the
server-side state hardening.

---

### POST /api/v1/auth/register Public

Register a storefront customer account.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "password": "password123",
  "fullName": "Nguyen Van A",
  "birthDate": "2000-01-01",
  "avatar": null,
  "gender": "MALE",
  "otpProofToken": "zQ8M5gHlKcJw3tP2vYn0dSbArExUiFoN6R9W1aD7C4k"
}
```

**Success Response (201):**

```json
{
  "statusCode": 201,
  "data": {
    "id": 1,
    "email": "customer@example.com",
    "fullName": "Nguyen Van A",
    "birthDate": "2000-01-01",
    "avatar": null,
    "gender": "MALE",
    "createdAt": "2026-06-14T14:00:00Z",
    "updatedAt": "2026-06-14T14:00:00Z"
  },
  "message": "Created",
  "timestamp": "2026-06-14T21:00:00"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 `OTP_PROOF_INVALID_OR_EXPIRED` | Proof missing, expired, reused or not bound to `REGISTER + email` |
| 409 | Email already exists |
| 429 `AUTH_RATE_LIMITED` | Register IP/global quota exceeded |

---

### POST /api/v1/auth/refresh Public

Rotate a valid refresh token and return a new access token plus a new refresh token.

The refresh token can be supplied in either:

- `HttpOnly` cookie named `refresh_token`, preferred for browser clients.
- JSON request body, useful for mobile clients, API clients, and manual testing.

**Request Body:**

```json
{
  "refreshToken": "eyJ..."
}
```

**Success Response (200):**

Headers:

```http
Set-Cookie: refresh_token=<newRefreshToken>; Max-Age=259200; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Lax
```

```json
{
  "statusCode": 200,
  "data": {
    "accessToken": "eyJ...(new)",
    "refreshToken": "eyJ...(new)",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:15:00"
}
```

**Errors:**

| Status | When |
|--------|------|
| 401 | No refresh token provided |
| 401 | Refresh token expired or revoked |
| 401 `SESSION_REVOKED` | JWT/session `securityVersion` is missing or stale |
| 429 `AUTH_RATE_LIMITED` | Refresh JTI/IP/global quota exceeded |

---

### POST /api/v1/auth/logout Public

Invalidate refresh token.

The refresh token can be supplied in either the `refresh_token` cookie or request body.

**Request Body:**

```json
{
  "refreshToken": "eyJ..."
}
```

**Success Response (200):**

Headers:

```http
Set-Cookie: refresh_token=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Lax
```

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Success",
  "timestamp": "2026-06-14T21:30:00"
}
```

---

### GET /api/v1/auth/me Bearer Implemented

Get current authenticated user.

**Error Responses:**

- `401 Unauthorized` when access token is missing, expired, or invalid.
- `401 SESSION_REVOKED` when the token has no current `securityVersion`.
- `429 AUTH_RATE_LIMITED` when the user/IP/global auth-session quota is exceeded.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "id": 1,
    "fullName": "System Admin",
    "email": "admin@example.com",
    "birthDate": "1990-01-01",
    "avatar": null,
    "gender": "OTHER",
    "createdAt": "2026-06-14T14:00:00Z",
    "updatedAt": "2026-06-14T14:00:00Z",
    "roles": [
      {
        "id": 1,
        "name": "SUPER_ADMIN"
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### POST /api/v1/auth/otp/request Public

Request an OTP code via email and receive an opaque challenge. `CHANGE_EMAIL`
requires Bearer JWT and binds the challenge to the current `userId`; the other two
purposes are public.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "purpose": "REGISTER"
}
```

Supported purpose values: `REGISTER`, `FORGOT_PASSWORD`, `CHANGE_EMAIL`.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "challengeId": "x7P5v6cWn0L9R3K1t2A8e4FqBzJmYuSdHiGoNcVXQ_k",
    "expiresInSeconds": 300,
    "cooldownSeconds": 60
  },
  "message": "Verification code sent successfully",
  "timestamp": "2026-07-16T10:00:00"
}
```

For `FORGOT_PASSWORD`, an unknown email receives the same status/schema with a
decoy challenge and no delivery, preventing account enumeration.

**Errors:**

| Status/code | When |
|---|---|
| 401 | `CHANGE_EMAIL` has no valid JWT |
| 429 `OTP_RATE_LIMITED` | Cooldown or any configured OTP dimension is exceeded |
| 503 `OTP_SERVICE_UNAVAILABLE` | Redis OTP/limiter state is unavailable; fail closed |
| 503 `OTP_DELIVERY_UNAVAILABLE` | Resend did not accept the email; old challenge is retained |

---

### POST /api/v1/auth/otp/verify Public

Verify an OTP challenge and receive a short-lived, single-use proof token. Email and
purpose are intentionally not accepted again; both come from the Redis challenge
binding.

**Request Body:**

```json
{
  "challengeId": "x7P5v6cWn0L9R3K1t2A8e4FqBzJmYuSdHiGoNcVXQ_k",
  "code": "123456"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "proofToken": "zQ8M5gHlKcJw3tP2vYn0dSbArExUiFoN6R9W1aD7C4k",
    "expiresInSeconds": 300
  },
  "message": "Verification code verified successfully",
  "timestamp": "2026-07-16T10:01:00"
}
```

**Errors:**

| Status/code | When |
|---|---|
| 400 `OTP_INVALID_OR_EXPIRED` | Challenge/code invalid, expired or superseded |
| 429 `OTP_ATTEMPTS_EXHAUSTED` | Five wrong codes on the scope; locked for the returned TTL |
| 429 `OTP_RATE_LIMITED` | Verify IP/global quota exceeded |
| 503 `OTP_SERVICE_UNAVAILABLE` | Redis state unavailable; fail closed |

---

### POST /api/v1/auth/forgot-password/reset Public

Reset password using verified OTP.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "newPassword": "newPassword123",
  "otpProofToken": "zQ8M5gHlKcJw3tP2vYn0dSbArExUiFoN6R9W1aD7C4k"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "allSessionsRevoked": true,
    "reauthenticationRequired": true
  },
  "message": "Password reset successfully",
  "timestamp": "2026-07-16T10:02:00"
}
```

The response clears the `refresh_token` cookie. The proof must be bound to
`FORGOT_PASSWORD + normalized email` and is atomically consumed once. All access and
refresh sessions are revoked by incrementing `securityVersion`.

---

### PUT /api/v1/auth/me/email Bearer Implemented

Change authenticated user's email using verified OTP (for the new email).

**Request Body:**

```json
{
  "newEmail": "newemail@example.com",
  "otpProofToken": "zQ8M5gHlKcJw3tP2vYn0dSbArExUiFoN6R9W1aD7C4k"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "allSessionsRevoked": true,
    "reauthenticationRequired": true
  },
  "message": "Email updated successfully",
  "timestamp": "2026-07-16T10:02:00"
}
```

The proof must be bound to `CHANGE_EMAIL + authenticated userId + normalized new
email`. The response clears the refresh cookie and all sessions are revoked.

**Errors for proof-protected final actions:**

| Status/code | When |
|---|---|
| 400 `OTP_PROOF_INVALID_OR_EXPIRED` | Proof expired, reused, superseded or bound to another scope |
| 401 `SESSION_REVOKED` | Access JWT was revoked before the authenticated action |
| 429 `AUTH_RATE_LIMITED` | Endpoint user/IP/global quota exceeded |
| 503 `OTP_SERVICE_UNAVAILABLE` | Proof store unavailable; action is not allowed |

---

### PUT /api/v1/auth/me/password Bearer Implemented

Set a first password for an OAuth-only account or change an existing password.
Existing password accounts must provide the current password.

**Request Body:**

```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword123"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "allSessionsRevoked": true,
    "reauthenticationRequired": true
  },
  "message": "Password updated successfully",
  "timestamp": "2026-07-16T10:02:00"
}
```

The response clears the refresh cookie and revokes all access/refresh sessions.

---

## 2. Users Implemented

### GET /users

List all users.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": { "page": 1, "pageSize": 10, "pages": 1, "total": 1 },
    "result": [
      {
        "id": 1,
        "fullName": "System Admin",
        "email": "admin@example.com",
        "birthDate": "1990-01-01",
        "avatar": null,
        "gender": "OTHER",
        "createdAt": "2026-06-14T14:00:00Z",
        "updatedAt": "2026-06-14T14:00:00Z",
        "roles": [
          {
            "id": 1,
            "name": "SUPER_ADMIN"
          }
        ],
        "permissions": []
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### GET /users/{id}

Get a single user by numeric ID. Detail responses include role summaries and effective permissions resolved
through `user_role` and `permission_role`.

**Errors:**

| Status | When |
|--------|------|
| 404 | User not found |

---

### POST /users

Create a user. Password is stored only as BCrypt hash.

**Request Body:**

```json
{
  "fullName": "Tran Thi B",
  "email": "staff@example.com",
  "password": "password123",
  "birthDate": "1995-05-20",
  "avatar": "https://cdn.example.com/avatars/staff01.png",
  "gender": "FEMALE"
}
```

**Success Response (201):** `UserResponse`

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Email already exists |

---

### PUT /api/v1/users/me

Cập nhật profile của user đang đăng nhập. Backend lấy identity từ JWT subject; client
không gửi `userId`.

Request dùng riêng `UpdateMyProfileRequest`:

```json
{
  "fullName": "Tran Thi B Updated",
  "birthDate": "1995-05-20",
  "gender": "FEMALE"
}
```

Ba field trên đều bắt buộc và `birthDate` phải ở quá khứ. DTO này không có
`avatar`; field `avatar` gửi thừa không được bind và không thể thay đổi avatar đang
lưu. Customer avatar upload được quản lý ở contract file riêng trong BE-004.

**Success Response (200):** `UserResponse`

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 401 | Missing/invalid access token |
| 403 | Authenticated role lacks the exact permission |
| 404 | Active user from JWT subject no longer exists |

---

### PUT /users/{id}

Generic operator endpoint kept for compatibility. Email and password are not
updated here; unlike `/api/v1/users/me`, its operator `UpdateUserRequest` still
contains `avatar` until the separate customer-avatar contract is delivered.

**Request Body:**

```json
{
  "fullName": "Tran Thi B Updated",
  "birthDate": "1995-05-20",
  "avatar": "https://cdn.example.com/avatars/staff01.png",
  "gender": "FEMALE"
}
```

**Success Response (200):** `UserResponse`

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 404 | User not found |

---

### DELETE /users/{id}

Soft delete a user by setting `deleted_at = now`.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

## 3. Roles Implemented

### GET /roles

List roles.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": { "page": 1, "pageSize": 10, "pages": 1, "total": 1 },
    "result": [
      {
        "id": 1,
        "name": "ADMIN",
        "description": "Full system access",
        "permissions": [
          {
            "id": 1,
            "name": "CREATE_USER",
            "apiPath": "/api/v1/users",
            "method": "POST",
            "module": "USER"
          }
        ],
        "createdAt": "2026-06-14T14:00:00Z",
        "updatedAt": "2026-06-14T14:00:00Z"
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### GET /roles/{id}

Get role by numeric ID. Detail responses include permissions assigned through `permission_role`.

**Errors:**

| Status | When |
|--------|------|
| 404 | Role not found |

---

### POST /roles

Create role.

**Request Body:**

```json
{
  "code": "MERCHANDISER",
  "name": "Merchandiser",
  "description": "Manage product catalog.",
  "systemRole": false
}
```

**Success Response (201):** `RoleResponse`

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Role code already exists |

---

### PUT /roles/{id}

Update role display fields. `code` is immutable through this endpoint.

**Request Body:**

```json
{
  "name": "Merchandiser",
  "description": "Manage categories, products, and variants.",
  "systemRole": false
}
```

---

### DELETE /roles/{id}

Delete role. Join-table records are removed by database cascade.

---

## 4. Permissions Implemented

### GET /permissions

List permissions.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": { "page": 1, "pageSize": 10, "pages": 1, "total": 10 },
    "result": [
      {
        "id": 1,
        "name": "CREATE_USER",
        "apiPath": "/api/v1/users",
        "method": "POST",
        "module": "USER",
        "createdAt": "2026-06-14T14:00:00Z",
        "updatedAt": null
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### GET /permissions/{id}

Get permission by UUID.

---

### POST /permissions

Create permission.

**Request Body:**

```json
{
  "code": "PROMOTION_WRITE",
  "name": "Write promotions",
  "module": "PROMOTION",
  "description": "Create and update promotion campaigns."
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Permission code already exists |

---

### PUT /permissions/{id}

Update permission display fields. `code` is immutable through this endpoint.

**Request Body:**

```json
{
  "name": "Write promotions",
  "module": "PROMOTION",
  "description": "Create, update, and disable promotion campaigns."
}
```

---

### DELETE /permissions/{id}

Delete permission. Join-table records are removed by database cascade.

---

## 5. Categories Implemented

### GET /categories

List product categories.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": { "page": 1, "pageSize": 10, "pages": 1, "total": 1 },
    "result": [
      {
        "id": 1,
        "parentId": null,
        "name": "Shoes",
        "createdAt": "2026-06-14T14:00:00Z",
        "updatedAt": "2026-06-14T14:00:00Z"
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### POST /categories

Create category.

**Request Body:**

```json
{
  "parentId": null,
  "name": "Electronics",
  "slug": "electronics",
  "description": "Electronic products.",
  "sortOrder": 0,
  "active": true
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Category slug already exists |

---

### PUT /categories/{id}

Update category. `slug` is immutable through this endpoint.

**Request Body:**

```json
{
  "parentId": null,
  "name": "Electronics & Devices",
  "description": "Electronic products and smart devices.",
  "sortOrder": 1,
  "active": true
}
```

---

### DELETE /categories/{id}

Delete category. Products referencing the category are set to `category_id = null` by database rule.

---

## 6. Products Implemented

### GET /products

List base products.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "meta": { "page": 1, "pageSize": 10, "pages": 1, "total": 1 },
    "result": [
      {
        "id": 1,
        "name": "Running Shoes",
        "description": "Lightweight daily running shoes.",
        "categoryId": 1,
        "brandId": 1,
        "status": "ACTIVE",
        "createdAt": "2026-06-14T14:00:00Z",
        "updatedAt": "2026-06-14T14:00:00Z"
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### POST /products

Create product.

**Request Body:**

```json
{
  "categoryId": 1,
  "brandId": 1,
  "name": "Running Shoes",
  "slug": "running-shoes",
  "description": "Base product for lightweight daily running shoes.",
  "status": "ACTIVE"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Product slug already exists |

---

### PUT /products/{id}

Update product fields. `slug` is immutable through this endpoint.

**Request Body:**

```json
{
  "categoryId": 1,
  "brandId": 1,
  "name": "Running Shoes 2026",
  "description": "Updated product description.",
  "status": "ACTIVE"
}
```

---

### DELETE /products/{id}

Soft archive product by setting `status = ARCHIVED` and `deleted_at = now`.

---

## 6.1 Storefront Catalog Implemented

### GET /api/v1/storefront/products Public

Endpoint này tách biệt với `/products` quản trị. Chỉ Product, Category và Product
Variant `ACTIVE`, chưa soft-delete được trả về. Màu, size và khoảng giá phải khớp
trên cùng variant; OR trong cùng facet và AND giữa các facet.

| Query | Contract |
|---|---|
| `q` | tối đa 120 ký tự |
| `categorySlugs` | CSV hoặc repeated slug list |
| `colorIds`, `sizeIds` | CSV hoặc repeated positive ID list |
| `minPrice`, `maxPrice` | inclusive, không âm, min không lớn hơn max |
| `sort` | `featured`, `newest`, `price-asc`, `price-desc` |
| `page`, `size` | 1-based; mặc định 1/12; size tối đa 60 |
| `locale` | query, rồi `Accept-Language`, rồi `vi` |

```http
GET /api/v1/storefront/products?categorySlugs=ao,ao-khoac&colorIds=1,2&sizeIds=3,4&minPrice=300000&maxPrice=1800000&sort=price-asc&page=1&size=12
```

`data` có dạng `{result, meta, facets}`. `facets` gồm `categories`, `colors`,
`sizes`, `priceRange`; count là số Product distinct. Giá filter/sort/response lấy
từ `VariantPricingService` và trả chi tiết trong `ProductResponse.pricing`.

Tài liệu contract, thuật toán và response đầy đủ xem
[STOREFRONT_CATALOG_UX_BACKEND_VI.md](./STOREFRONT_CATALOG_UX_BACKEND_VI.md).

---

## 7. Files Implemented

### POST /files

Upload an image file for later use as a user avatar or company logo.

**Auth:** Bearer

**Content-Type:** `multipart/form-data`

**Form Data:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | Image file. Allowed extensions and size are configured by `app.upload`. |
| `folder` | String | Yes | Target folder. Allowed values are configured by `app.upload.allowed-folders`. |

**Success Response (201):**

```json
{
  "statusCode": 201,
  "data": {
    "fileName": "1709123456789_photo.jpg",
    "folder": "avatars",
    "fileUrl": "/uploads/avatars/1709123456789_photo.jpg",
    "size": 24576,
    "uploadedAt": "2026-06-26T09:00:00Z"
  },
  "message": "Created",
  "timestamp": "2026-06-26T09:00:00"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Missing file/folder, invalid folder, invalid extension, invalid file name, or business size validation failed |
| 401 | Missing or invalid JWT |
| 403 | Authenticated user does not have `UPLOAD_FILE` permission |
| 413 | Servlet multipart size limit exceeded |

Client uses the returned `fileName` to update the related entity, for example `avatar` on `PUT /users/{id}`.

---

## 7.1 Verified Product Reviews Implemented

### GET /api/v1/reviews/product/{productId} Public

Nhận `rating`, public sort whitelist, `page` 1-based và `size` mặc định 10. Service
giới hạn size tối đa 100. Item dùng `PublicReviewResponse`, không trả `userId`,
`orderId`, `orderCode` hoặc `orderItemId`.

### GET /api/v1/reviews/product/{productId}/summary Public

Trả `{total, averageRating, ratingCounts}`; `ratingCounts` luôn đủ key 1–5.

### GET /api/v1/reviews/me Bearer

Nhận `orderId` tùy chọn và luôn ép User theo JWT subject. Response giàu thông tin
đơn hàng chỉ dành cho principal hiện tại.

### POST /api/v1/reviews Bearer

`Content-Type: multipart/form-data`, part `review` là JSON
`{orderItemId, rating, comment}`, part `images` tùy chọn và lặp tối đa 5 lần.
Mỗi ảnh tối đa 5 MB, chỉ JPG/JPEG/PNG/WebP. OrderItem phải thuộc principal,
Order phải `COMPLETED`; duplicate trả `409 REVIEW_ALREADY_EXISTS` và đơn chưa
hoàn tất trả `409 REVIEW_ORDER_NOT_COMPLETED`.

File dùng UUID, ghi `.tmp` rồi atomic move vào `/uploads/reviews`. Generic
`POST /files` từ chối `folder=reviews`. Chi tiết rollback/cleanup và error contract:
[STOREFRONT_CATALOG_UX_BACKEND_VI.md](./STOREFRONT_CATALOG_UX_BACKEND_VI.md).

---

## 7.2 Locale và nội dung động

Các API hiển thị Product, Category, Product Variant, Sale, Cart và Checkout resolve
đúng một locale theo thứ tự:

1. Query parameter `locale`, ví dụ `?locale=en`.
2. Header `Accept-Language`.
3. Locale mặc định `vi`.

Nếu locale không được bật hoặc entity thiếu bản dịch được yêu cầu, API fallback
về `vi`, rồi mới dùng text ở bảng core. Response storefront trả trực tiếp các
field đã resolve, không trả toàn bộ translation map. Client phải đưa locale vào
query/cache key để dữ liệu VI và EN không dùng chung cache.

`Accept-Language` tuân theo q-weight, bỏ qua lựa chọn `q=0` và fallback locale
vùng về ngôn ngữ gốc, ví dụ `en-US` về `en`. Cart, checkout preview và checkout
thật dùng cùng locale cho tên/slug Product và tên Sale Campaign. Checkout lưu
những giá trị này thành snapshot trong order item; đổi ngôn ngữ sau đó không làm
thay đổi lịch sử đơn hàng đã tạo.

Vì URL hiện tại không có prefix locale, endpoint detail Product/Category nhận
slug thuộc bất kỳ translation nào để xác định entity, sau đó mới localize response
theo locale request. Do đó link dùng English slug vẫn hoạt động khi chuyển sang
VI và ngược lại; response trả slug đúng của ngôn ngữ mới để client có thể chuẩn
hóa URL nếu cần.

`color`, `size`, SKU, campaign code và enum trạng thái là giá trị kỹ thuật;
frontend chịu trách nhiệm dịch nhãn giao diện tương ứng.

---

## 8. Business Modules Implemented

The following business modules already expose controllers. All list endpoints
should align to the paginated response contract defined in `Response Format`.

### Catalog Supporting Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/brands` | List brands |
| GET | `/brands/{id}` | Get brand |
| POST | `/brands` | Create brand |
| PUT | `/brands/{id}` | Update brand |
| DELETE | `/brands/{id}` | Delete brand |
| GET | `/sizes` | List sizes |
| GET | `/sizes/{id}` | Get size |
| POST | `/sizes` | Create size |
| PUT | `/sizes/{id}` | Update size |
| DELETE | `/sizes/{id}` | Delete size |
| GET | `/colors` | List colors |
| GET | `/colors/{id}` | Get color |
| POST | `/colors` | Create color |
| PUT | `/colors/{id}` | Update color |
| DELETE | `/colors/{id}` | Delete color |

### Admin quản trị bản dịch Catalog

`ADMIN` và `MANAGER` quản lý nội dung Product/Category qua subresource riêng;
endpoint create/update core hiện có vẫn giữ tương thích.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/products/{id}/translations` | Lấy mọi bản dịch của Product |
| PUT | `/api/v1/products/{id}/translations` | Upsert danh sách bản dịch Product |
| DELETE | `/api/v1/products/{id}/translations/{locale}` | Xóa một locale không mặc định |
| GET | `/api/v1/categories/{id}/translations` | Lấy mọi bản dịch của Category |
| PUT | `/api/v1/categories/{id}/translations` | Upsert danh sách bản dịch Category |
| DELETE | `/api/v1/categories/{id}/translations/{locale}` | Xóa một locale không mặc định |

Product `PUT` dùng shape:

```json
{
  "translations": [
    {
      "localeCode": "en",
      "name": "Essential Cotton Tee",
      "slug": "essential-cotton-tee",
      "shortDescription": "A soft everyday cotton tee.",
      "description": "English product description.",
      "material": "100% cotton",
      "careInstruction": "Machine wash cold.",
      "seoTitle": "Essential Cotton Tee",
      "seoDescription": "English SEO description."
    }
  ]
}
```

Category translation gồm `localeCode`, `name`, `slug`, `description`,
`seoTitle`, `seoDescription`. GET/PUT trả `{ "translations": [...] }`.
`vi` là locale mặc định bắt buộc và không được xóa. Slug phải duy nhất trong
từng locale; locale không tồn tại hoặc chưa bật bị từ chối.

Response Product, Category và Sale có `translationLocales` để Admin biết entity
đã có bản dịch nào; storefront vẫn chỉ nhận nội dung của locale đã resolve.

### Gợi ý nội dung English bằng Gemini

Ba endpoint dưới đây chỉ tạo bản nháp English từ nội dung VI đang có trên form;
chúng không cần entity ID và không ghi vào database:

| Method | Endpoint | Permission |
|--------|----------|------------|
| POST | `/api/v1/products/translation-suggestions/en` | `GENERATE_PRODUCT_ENGLISH_CONTENT` |
| POST | `/api/v1/categories/translation-suggestions/en` | `GENERATE_CATEGORY_ENGLISH_CONTENT` |
| POST | `/api/v1/sale-campaigns/translation-suggestions/en` | `GENERATE_SALE_CAMPAIGN_ENGLISH_CONTENT` |

Các permission chỉ cấp mặc định cho `ADMIN` và `MANAGER`. Product request:

```json
{
  "model": "gemini-3.1-flash-lite",
  "name": "Áo thun cotton thiết yếu",
  "shortDescription": "Áo thun cotton mềm mại.",
  "description": "Mô tả chi tiết tiếng Việt.",
  "material": "100% cotton",
  "careInstruction": "Giặt máy bằng nước lạnh.",
  "seoTitle": "Áo thun cotton thiết yếu",
  "seoDescription": "Mô tả SEO tiếng Việt."
}
```

Product response trong `ApiResponse.data`:

```json
{
  "localeCode": "en",
  "name": "Essential Cotton Tee",
  "shortDescription": "A soft cotton tee.",
  "description": "Detailed English description.",
  "material": "100% cotton",
  "careInstruction": "Machine wash cold.",
  "seoTitle": "Essential Cotton Tee",
  "seoDescription": "English SEO description."
}
```

Category request/response dùng `name`, `description`, `seoTitle`,
`seoDescription`; Sale Campaign dùng `name`, `description`. Cả ba request có
`model` tùy chọn. Các model được whitelist:

- `gemini-3.1-flash-lite` — mặc định, tiết kiệm.
- `gemini-3.5-flash` — cân bằng.
- `gemini-3.1-pro-preview` — chất lượng cao, preview.

Response cố ý không trả `slug`. Frontend tự tạo English slug theo cách xác định
từ English name rồi Admin xem lại. Chỉ thao tác lưu translation hiện có mới ghi
database; vì vậy provider lỗi không ảnh hưởng luồng tạo/sửa thủ công.

| Status | Code | Ý nghĩa |
|--------|------|---------|
| 400 | `CONTENT_GENERATION_MODEL_NOT_ALLOWED` | Model không thuộc whitelist |
| 400 | `CONTENT_GENERATION_INPUT_TOO_LARGE` | Tổng nội dung VI vượt giới hạn |
| 429 | `CONTENT_GENERATION_RATE_LIMITED` | Gemini trả rate limit |
| 502 | `CONTENT_GENERATION_PROVIDER_ERROR` | Gemini lỗi hoặc không truy cập được |
| 502 | `CONTENT_GENERATION_INVALID_RESPONSE` | JSON trả về sai schema/validation |
| 503 | `CONTENT_GENERATION_DISABLED` | Tính năng tắt hoặc backend chưa có key |

API key chỉ đọc từ `GEMINI_API_KEY` ở backend và không xuất hiện trong request
Frontend, response hoặc log. Adapter gọi Gemini `generateContent` bằng header
`x-goog-api-key`; structured output dùng cặp
`generationConfig.responseMimeType=application/json` và
`generationConfig.responseJsonSchema`. Shape này đã được kiểm tra trực tiếp với
`v1beta`; không dùng `responseFormat.text` vì endpoint này trả
`400 INVALID_ARGUMENT` với shape mới đó.

`ENGLISH_CONTENT_MAX_OUTPUT_TOKENS` mặc định là `16384`. Backend từ chối
candidate có `finishReason` khác `STOP` (nhưng chấp nhận response tương thích cũ
không có field này), vì output `MAX_TOKENS` có thể là JSON hợp lệ nhưng nội dung
đã bị cắt. Với field VI null/rỗng, response tương ứng luôn bị ép về null kể cả
khi provider tự sinh text, để không bịa thêm thuộc tính sản phẩm/campaign.

### Admin bật/tắt nhanh trạng thái

Các endpoint toggle nhận body `{ "status": "ACTIVE" }` hoặc
`{ "status": "INACTIVE" }`:

| Method | Endpoint | Chuyển trạng thái hợp lệ |
|--------|----------|--------------------------|
| PATCH | `/api/v1/products/{id}/status` | `DRAFT/INACTIVE -> ACTIVE`, `ACTIVE -> INACTIVE` |
| PATCH | `/api/v1/categories/{id}/status` | `ACTIVE <-> INACTIVE` |
| PATCH | `/api/v1/brands/{id}/status` | `ACTIVE <-> INACTIVE` |
| PATCH | `/api/v1/product-variants/{id}/status` | `ACTIVE <-> INACTIVE` |

Product/variant ở trạng thái đặc biệt như `OUT_OF_STOCK` hoặc `DISCONTINUED`
không bị toggle ghi đè; Admin phải dùng form cập nhật đầy đủ nếu nghiệp vụ cho
phép đổi các trạng thái này. Các guard Sale Campaign đang áp dụng cho Product và
variant vẫn được kiểm tra trước khi bật/tắt.

### Product Variants

Product variant is the sellable SKU. Variant price can differ by color and size.
`Product` stores base catalog information; `ProductVariant` stores the list
`price`, `stockQuantity`, `color`, and `size`. Promotional price is resolved from
Sale Campaign; there is no standalone `salePrice` field.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/product-variants` | List product variants |
| GET | `/product-variants/{id}` | Get product variant |
| POST | `/product-variants` | Create product variant |
| PUT | `/product-variants/{id}` | Update product variant |
| DELETE | `/product-variants/{id}` | Delete product variant |

**Create/Update Request Body:**

```json
{
  "productId": 1,
  "sku": "RUN-SHOE-BLACK-40",
  "price": 1200000.00,
  "stockQuantity": 50,
  "colorId": 1,
  "sizeId": 1,
  "status": "ACTIVE"
}
```

**Response Example:**

```json
{
  "statusCode": 200,
  "data": {
    "id": 1,
    "product": { "id": 1, "name": "Running Shoes" },
    "sku": "RUN-SHOE-BLACK-40",
    "price": 1200000.00,
    "stockQuantity": 50,
    "pricing": {
      "listPrice": 1200000.00,
      "effectivePrice": 990000.00,
      "priceSource": "FLASH_SALE",
      "campaignId": 12,
      "campaignItemId": 84,
      "campaignCode": "FLASH-2000",
      "campaignName": "Flash Sale 20h",
      "startsAt": "2026-07-15T13:00:00Z",
      "endsAt": "2026-07-15T15:00:00Z",
      "remainingQuota": 3,
      "maxPerCustomer": 2,
      "customerRemaining": 1,
      "couponEligible": false,
      "availableQuantity": 1
    },
    "color": { "id": 1, "name": "Black" },
    "size": { "id": 1, "name": "40" },
    "status": "ACTIVE",
    "createdAt": "2026-06-14T14:00:00Z",
    "updatedAt": "2026-06-14T14:00:00Z"
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

### User Addresses

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/user-addresses/me` | List only the authenticated user's addresses |
| GET | `/api/v1/user-addresses/me/{id}` | Get an owned address; foreign/missing ID is `404` |
| POST | `/api/v1/user-addresses/me` | Create for the authenticated user; body has no `userId` |
| PUT | `/api/v1/user-addresses/me/{id}` | Update an owned address; foreign/missing ID is `404` |
| DELETE | `/api/v1/user-addresses/me/{id}` | Delete an owned address; foreign/missing ID is `404` |
| GET | `/user-addresses` | Operator-only: list addresses, optionally filter by `userId` |
| GET | `/user-addresses/{id}` | Operator-only: get address |
| POST | `/user-addresses` | Operator-only: create address for a selected user |
| PUT | `/user-addresses/{id}` | Operator-only: update address by ID |
| DELETE | `/user-addresses/{id}` | Operator-only: delete address by ID |

`POST /api/v1/user-addresses/me` dùng `CreateMyUserAddressRequest`:

```json
{
  "receiverName": "Tran Thi B",
  "phone": "0900000000",
  "province": "Ho Chi Minh",
  "ward": "Ben Nghe",
  "addressDetail": "123 Test Street",
  "isDefault": true
}
```

Owner luôn được resolve từ JWT trong service. Khi một address được đặt làm default,
default cũ chỉ bị gỡ trong cùng user; invariant mỗi user tối đa một default address
được giữ nguyên.

### Coupon, Cart, Wishlist

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/coupons` | List coupons |
| GET | `/coupons/{id}` | Get coupon |
| POST | `/coupons` | Create coupon |
| PUT | `/coupons/{id}` | Update coupon |
| DELETE | `/coupons/{id}` | Delete coupon |
| GET | `/carts` | Operator-only: list carts |
| GET | `/carts/{id}` | Operator-only: get cart |
| GET | `/carts/user/{userId}` | Operator-only: get cart by selected user |
| POST | `/carts` | Operator-only: create cart for a selected user |
| DELETE | `/carts/{id}` | Operator-only: delete cart by ID |
| GET | `/wishlists` | Operator-only: list wishlists by arbitrary filters |
| GET | `/wishlists/{id}` | Operator-only: get wishlist item by ID |
| POST | `/wishlists` | Operator-only: create wishlist item for a selected user |
| DELETE | `/wishlists/{id}` | Operator-only: delete wishlist item by ID |

### Orders, Payments, Reviews

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/orders/me` | Paginated orders owned by the authenticated user |
| GET | `/api/v1/orders/me/{id}` | Owned order detail; foreign/missing ID is `404` |
| GET | `/api/v1/orders/me/code/{orderCode}` | Owned order detail by business code; foreign/missing code is `404` |
| GET | `/api/v1/orders/me/{id}/status-histories` | History of an owned order; foreign/missing order ID is `404` |
| GET | `/orders` | Operator-only: list orders |
| GET | `/orders/{id}` | Operator-only: get order by ID |
| GET | `/orders/code/{orderCode}` | Operator-only: get order by business code |
| GET | `/orders/user/{userId}` | Operator-only: list orders by selected user |
| GET | `/orders/{id}/status-histories` | Operator-only: list status history of an order |
| POST | `/orders` | Operator-only legacy create; customer checkout uses `/api/v1/checkout` |
| PUT | `/orders/{id}` | Operator-only: update order |
| DELETE | `/orders/{id}` | Operator-only: delete order |
| GET | `/payments` | Operator-only: list payments |
| GET | `/payments/{id}` | Operator-only: get payment |
| POST | `/payments` | Operator-only: create payment |
| PUT | `/payments/{id}` | Operator-only: update payment |
| DELETE | `/payments/{id}` | Operator-only: delete payment |
| GET | `/reviews` | Operator-only: list reviews with arbitrary filters |
| GET | `/reviews/user/{userId}` | Operator-only: list reviews by selected user |
| GET | `/reviews/product/{productId}` | List public product reviews with rating/sort/page |
| GET | `/reviews/product/{productId}/summary` | Get public rating summary |
| GET | `/reviews/me` | List current principal reviews, optional `orderId` |
| GET | `/reviews/order/{orderId}` | Operator-only: list reviews by order |
| GET | `/reviews/order-item/{orderItemId}` | Operator-only: list reviews by order item |
| POST | `/reviews` | Create verified review using multipart JSON and optional images |

Các customer route `/orders/me/**` và `/user-addresses/me/**` bind ownership ở
service/repository bằng principal + identifier, không load unscoped rồi authorize ở
controller. Rollout **BE-001 → FE-001 → BE-002** đã hoàn tất: customer client phải
dùng route `/me` và không được quay lại generic ID/user route. Request không có token
trả `401`; `ROLE_USER` gọi một route generic đã contract trả `403`; foreign hoặc
missing resource qua `/me` trả `404` để không lộ ownership.

V22 chỉ thu hồi 27 mapping dưới đây khỏi production role `USER`; permission row,
controller và mapping của role khác vẫn tồn tại. “Operator-only” nghĩa là access phụ
thuộc exact RBAC mapping của từng `ADMIN`, `MANAGER` hoặc `STAFF`, không có nghĩa mọi
operator role đều được gọi mọi route.

| Module | Method/path bị thu hồi khỏi `ROLE_USER` |
|--------|------------------------------------------|
| Cart | `GET /api/v1/carts`; `POST /api/v1/carts/items`; `DELETE /api/v1/carts/items/{id}`; `POST /api/v1/carts`; `DELETE /api/v1/carts/{id}`; `GET /api/v1/carts/{id}`; `GET /api/v1/carts/user/{userId}` |
| User address | `GET /api/v1/user-addresses`; `POST /api/v1/user-addresses`; `GET`, `PUT`, `DELETE /api/v1/user-addresses/{id}` |
| Order | `POST /api/v1/orders`; `GET /api/v1/orders/{id}`; `GET /api/v1/orders/code/{orderCode}`; `GET /api/v1/orders/user/{userId}`; `GET /api/v1/orders/{id}/status-histories` |
| Payment | `POST /api/v1/payments` |
| Review | `PUT`, `DELETE /api/v1/reviews/{id}`; `GET /api/v1/reviews/user/{userId}`; `GET /api/v1/reviews/order/{orderId}`; `GET /api/v1/reviews/order-item/{orderItemId}` |
| Wishlist | `GET`, `POST /api/v1/wishlists`; `GET`, `DELETE /api/v1/wishlists/{id}` |

V22 giữ nguyên catalog reads, `/api/v1/checkout`, `POST /api/v1/reviews`, self cart,
self wishlist, self coupon và toàn bộ permission `/me` của V21. Migration match bằng
`api_path + method`, không dựa riêng vào permission name, nên vẫn thu hồi đúng mapping
trên database development đã từng rename permission bằng repeatable seed.

### Sale Campaign Admin

Admin Sale Campaign là module riêng với Coupon. `ADMIN` và `MANAGER` có quyền
quản lý; `STAFF` chỉ có quyền đọc. Mọi timestamp gửi/nhận theo ISO-8601 có
offset/UTC.

Campaign lifecycle lưu trong database là `DRAFT`, `PUBLISHED`, `CANCELLED`.
`UPCOMING`, `LIVE`, `ENDED` là phase được server tính từ lifecycle và thời gian:
campaign `CANCELLED` luôn có phase `ENDED`; campaign khác là `UPCOMING` khi
`now < startsAt`, `LIVE` trong `[startsAt, endsAt)`, và `ENDED` khi
`now >= endsAt`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/sale-campaigns` | List/filter campaigns |
| GET | `/api/v1/sale-campaigns/{id}` | Campaign detail including variants/counters |
| POST | `/api/v1/sale-campaigns` | Create DRAFT |
| PUT | `/api/v1/sale-campaigns/{id}` | Update DRAFT or UPCOMING with optimistic version |
| DELETE | `/api/v1/sale-campaigns/{id}` | Delete DRAFT only |
| POST | `/api/v1/sale-campaigns/{id}/publish` | Validate prices, variants and overlap, then publish |
| POST | `/api/v1/sale-campaigns/{id}/cancel` | Cancel UPCOMING campaign |
| PATCH | `/api/v1/sale-campaigns/{id}/display` | Update display fields while LIVE |
| POST | `/api/v1/sale-campaigns/{id}/items/{itemId}/increase-quota` | Increase live Flash quota |
| POST | `/api/v1/sale-campaigns/{id}/end` | End a live campaign early |
| POST | `/api/v1/sale-campaigns/{id}/end-and-clone` | End live campaign and create successor DRAFT |
| GET | `/api/v1/sale-campaigns/{id}/translations` | Lấy bản dịch VI/EN của campaign |
| PUT | `/api/v1/sale-campaigns/{id}/translations` | Upsert bản dịch với optimistic version |
| DELETE | `/api/v1/sale-campaigns/{id}/translations/{locale}?version={version}` | Xóa locale không mặc định |

`publish`, `cancel` and `end` receive optimistic version as query parameter,
for example `POST /api/v1/sale-campaigns/12/publish?version=3`. Other write
actions carry `version` in their JSON body.

Nội dung song ngữ được quản lý qua subresource translations. `PUT` nhận:

```json
{
  "version": 3,
  "translations": [
    {
      "localeCode": "vi",
      "name": "Flash Sale nổi bật",
      "description": "Ưu đãi số lượng giới hạn."
    },
    {
      "localeCode": "en",
      "name": "Featured Flash Sale",
      "description": "Limited-quantity deals."
    }
  ]
}
```

GET/PUT/DELETE trả `{ "version": 4, "translations": [...] }`. `vi` không được
xóa. Version cũ trả conflict để hai Admin không ghi đè nội dung của nhau.
Translation được sửa khi campaign còn `DRAFT` hoặc `PUBLISHED` nhưng chưa kết
thúc; campaign `CANCELLED`/`ENDED` là lịch sử chỉ đọc.

**Create Request:**

```json
{
  "code": "FLASH-2000",
  "name": "Flash Sale 20h",
  "description": "Hai giờ giá sốc",
  "bannerUrl": "/uploads/sales/flash-2000.webp",
  "type": "FLASH",
  "startsAt": "2026-07-15T13:00:00Z",
  "endsAt": "2026-07-15T15:00:00Z",
  "items": [
    {
      "variantId": 41,
      "promotionalPrice": 990000,
      "quota": 20,
      "maxPerCustomer": 2
    },
    {
      "variantId": 42,
      "promotionalPrice": 1090000,
      "quota": 15,
      "maxPerCustomer": null
    }
  ]
}
```

For `STANDARD`, `quota` and `maxPerCustomer` must be null. One campaign may
contain variants from one or many products.

**Detail Response:**

```json
{
  "statusCode": 200,
  "data": {
    "id": 12,
    "code": "FLASH-2000",
    "name": "Flash Sale 20h",
    "description": "Hai giờ giá sốc",
    "bannerUrl": "/uploads/sales/flash-2000.webp",
    "type": "FLASH",
    "status": "PUBLISHED",
    "phase": "LIVE",
    "startsAt": "2026-07-15T13:00:00Z",
    "endsAt": "2026-07-15T15:00:00Z",
    "version": 3,
    "items": [
      {
        "id": 84,
        "variantId": 41,
        "productId": 7,
        "sku": "RUN-SHOE-BLACK-40",
        "productName": "Running Shoes",
        "productSlug": "running-shoes",
        "image": "/uploads/products/running-shoes-black.webp",
        "color": "Black",
        "size": "40",
        "referencePrice": 1500000,
        "promotionalPrice": 990000,
        "quota": 20,
        "reservedQuantity": 2,
        "soldQuantity": 10,
        "remainingQuota": 8,
        "maxPerCustomer": 2,
        "stockQuantity": 6,
        "availableQuantity": 6
      }
    ],
    "createdAt": "2026-07-14T09:00:00Z",
    "updatedAt": "2026-07-15T13:10:00Z"
  },
  "message": "Success",
  "timestamp": "2026-07-15T13:20:00Z"
}
```

**Optimistic update:**

`PUT` includes the latest `version`. Action endpoints receive the same version
to reject stale admin screens.

```json
{
  "version": 3,
  "code": "FLASH-2000",
  "name": "Flash Sale 20h - cập nhật",
  "description": "Nội dung mới",
  "bannerUrl": "/uploads/sales/flash-2000-v2.webp",
  "type": "FLASH",
  "startsAt": "2026-07-15T13:00:00Z",
  "endsAt": "2026-07-15T15:30:00Z",
  "items": [
    {
      "variantId": 41,
      "promotionalPrice": 950000,
      "quota": 30,
      "maxPerCustomer": 2
    }
  ]
}
```

`PUT` là full replacement: `code`, `name`, `type`, thời gian và ít nhất một
item đều bắt buộc, không chỉ gửi riêng các field vừa sửa.

**Increase quota:**

```json
{
  "version": 4,
  "additionalQuantity": 10
}
```

Quota can only increase while LIVE. Decreasing quota or changing price/variant
after campaign starts returns `CAMPAIGN_ALREADY_STARTED`.

**End and clone:**

```json
{
  "version": 5,
  "code": "FLASH-2200",
  "name": "Flash Sale 22h",
  "startsAt": "2026-07-15T15:00:00Z",
  "endsAt": "2026-07-15T17:00:00Z"
}
```

The original is ended at server time. The new campaign copies type, display
content, variants, promotional prices, quota and customer limits into a new
`DRAFT`; counters are reset to zero. Toàn bộ translation VI/EN được sao chép;
tên VI mới trong request được áp vào bản VI của campaign kế tiếp.

### Public Sale and Pricing

Public Sale hỗ trợ `?locale=vi|en` và `Accept-Language` theo contract locale ở
trên. `campaign.name`, `campaign.description`, `item.productName` và
`item.productSlug` phải thuộc cùng locale đã resolve; nếu thiếu EN thì fallback
về VI. Banner URL, code, SKU, color và size dùng chung.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/v1/sales?type=STANDARD` | Public | Non-ended published Standard campaigns |
| GET | `/api/v1/sales?type=FLASH` | Public | Live/upcoming published Flash campaigns |
| GET | `/api/v1/sales?type=FLASH&phase=LIVE&phase=UPCOMING` | Public | Filter nhiều phase bằng query parameter lặp |
| GET | `/api/v1/sales/{code}` | Public | Public detail by stable campaign code |

`type` và `phase` là optional; omitting them returns both types and every public
phase. To request several phases, repeat `phase` as shown above instead of
sending one comma-delimited value. The list response wraps the campaign array
with `serverTime`. The detail endpoint returns the campaign directly. Pricing
on catalog/product/cart uses:

```json
{
  "listPrice": 1500000,
  "effectivePrice": 990000,
  "priceSource": "FLASH_SALE",
  "campaignId": 12,
  "campaignItemId": 84,
  "campaignCode": "FLASH-2000",
  "campaignName": "Flash Sale 20h",
  "startsAt": "2026-07-15T13:00:00Z",
  "endsAt": "2026-07-15T15:00:00Z",
  "remainingQuota": 8,
  "maxPerCustomer": 2,
  "customerRemaining": 1,
  "couponEligible": false,
  "availableQuantity": 1
}
```

`customerRemaining` is null without an authenticated user. `availableQuantity`
is bounded by stock, remaining Flash quota and customer remaining limit.
Displayed pricing is not a reservation; add-to-cart does not hold stock or
Flash quota.

### Still Planned

| Module | Planned scope |
|--------|---------------|
| Product images | Image upload/listing APIs |
| Inventory | Stock records and stock adjustment APIs |
| Shipments | Shipment creation and shipment status tracking |

---

## Endpoint Summary

Giá trị `Bearer` chỉ cho biết endpoint cần access token; authorization thực tế vẫn
theo exact method/path RBAC. Với generic customer-resource route đã contract ở V22,
`ROLE_USER` nhận `403`, còn operator access phụ thuộc mapping của từng production role.

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/api/v1/auth/login` | Public | Implemented | Login |
| POST | `/api/v1/auth/register` | Public | Implemented | Register |
| POST | `/api/v1/auth/refresh` | Public | Implemented | Refresh token |
| POST | `/api/v1/auth/logout` | Public | Implemented | Logout |
| GET | `/api/v1/auth/me` | Bearer | Implemented | Current user |
| POST | `/api/v1/auth/otp/request` | Public/Bearer for `CHANGE_EMAIL` | Implemented | Request OTP challenge |
| POST | `/api/v1/auth/otp/verify` | Public/Bearer for `CHANGE_EMAIL` | Implemented | Verify challenge and issue proof |
| POST | `/api/v1/auth/forgot-password/reset` | Public | Implemented | Reset password with proof; revoke all sessions |
| PUT | `/api/v1/auth/me/email` | Bearer | Implemented | Change email with actor-bound proof; revoke all sessions |
| PUT | `/api/v1/auth/me/password` | Bearer | Implemented | Set/change password; revoke all sessions |
| GET | `/oauth2/authorization/google` | Public | Implemented | Start Google OAuth2 Login with server-side Redis state |
| GET | `/login/oauth2/code/google` | Public callback | Implemented | Consume OAuth2 state once and redirect frontend |
| GET | `/actuator/metrics/**` | Bearer `ROLE_ADMIN` | Implemented | In-memory Micrometer metrics |
| GET | `/users` | Bearer | Implemented | List users |
| GET | `/users/{id}` | Bearer | Implemented | Get user |
| POST | `/users` | Bearer | Implemented | Create user |
| PUT | `/api/v1/users/me` | Bearer | Implemented | Principal-scoped profile update; cannot mutate avatar |
| PUT | `/users/{id}` | Bearer | Implemented | Update user |
| DELETE | `/users/{id}` | Bearer | Implemented | Soft delete user |
| GET | `/roles` | Bearer | Implemented | List roles |
| GET | `/roles/{id}` | Bearer | Implemented | Get role |
| POST | `/roles` | Bearer | Implemented | Create role |
| PUT | `/roles/{id}` | Bearer | Implemented | Update role |
| DELETE | `/roles/{id}` | Bearer | Implemented | Delete role |
| GET | `/permissions` | Bearer | Implemented | List permissions |
| GET | `/permissions/{id}` | Bearer | Implemented | Get permission |
| POST | `/permissions` | Bearer | Implemented | Create permission |
| PUT | `/permissions/{id}` | Bearer | Implemented | Update permission |
| DELETE | `/permissions/{id}` | Bearer | Implemented | Delete permission |
| GET | `/categories` | Bearer | Implemented | List categories |
| GET | `/categories/{id}` | Bearer | Implemented | Get category |
| POST | `/categories` | Bearer | Implemented | Create category |
| PUT | `/categories/{id}` | Bearer | Implemented | Update category |
| DELETE | `/categories/{id}` | Bearer | Implemented | Delete category |
| GET | `/products` | Bearer | Implemented | List products |
| GET | `/products/{id}` | Bearer | Implemented | Get product |
| GET | `/api/v1/storefront/products` | Public | Implemented | Search/filter/sort products with effective price and facets |
| POST | `/products` | Bearer | Implemented | Create product |
| PUT | `/products/{id}` | Bearer | Implemented | Update product |
| DELETE | `/products/{id}` | Bearer | Implemented | Soft archive product |
| POST | `/files` | Bearer | Implemented | Upload image file |
| GET | `/brands` | Bearer | Implemented | List brands |
| GET | `/brands/{id}` | Bearer | Implemented | Get brand |
| POST | `/brands` | Bearer | Implemented | Create brand |
| PUT | `/brands/{id}` | Bearer | Implemented | Update brand |
| DELETE | `/brands/{id}` | Bearer | Implemented | Delete brand |
| GET | `/sizes` | Bearer | Implemented | List sizes |
| GET | `/sizes/{id}` | Bearer | Implemented | Get size |
| POST | `/sizes` | Bearer | Implemented | Create size |
| PUT | `/sizes/{id}` | Bearer | Implemented | Update size |
| DELETE | `/sizes/{id}` | Bearer | Implemented | Delete size |
| GET | `/colors` | Bearer | Implemented | List colors |
| GET | `/colors/{id}` | Bearer | Implemented | Get color |
| POST | `/colors` | Bearer | Implemented | Create color |
| PUT | `/colors/{id}` | Bearer | Implemented | Update color |
| DELETE | `/colors/{id}` | Bearer | Implemented | Delete color |
| GET | `/product-variants` | Bearer | Implemented | List product variants |
| GET | `/product-variants/{id}` | Bearer | Implemented | Get product variant |
| POST | `/product-variants` | Bearer | Implemented | Create product variant |
| PUT | `/product-variants/{id}` | Bearer | Implemented | Update product variant |
| DELETE | `/product-variants/{id}` | Bearer | Implemented | Delete product variant |
| GET | `/api/v1/sale-campaigns` | Bearer | Implemented | Admin list/filter campaigns |
| GET | `/api/v1/sale-campaigns/{id}` | Bearer | Implemented | Admin campaign detail |
| POST | `/api/v1/sale-campaigns` | Bearer | Implemented | Create campaign DRAFT |
| PUT | `/api/v1/sale-campaigns/{id}` | Bearer | Implemented | Update DRAFT/UPCOMING with version |
| DELETE | `/api/v1/sale-campaigns/{id}` | Bearer | Implemented | Delete DRAFT |
| POST | `/api/v1/sale-campaigns/{id}/publish` | Bearer | Implemented | Publish campaign |
| POST | `/api/v1/sale-campaigns/{id}/cancel` | Bearer | Implemented | Cancel UPCOMING campaign |
| PATCH | `/api/v1/sale-campaigns/{id}/display` | Bearer | Implemented | Update LIVE display fields |
| POST | `/api/v1/sale-campaigns/{id}/items/{itemId}/increase-quota` | Bearer | Implemented | Increase live Flash quota |
| POST | `/api/v1/sale-campaigns/{id}/end` | Bearer | Implemented | End campaign early |
| POST | `/api/v1/sale-campaigns/{id}/end-and-clone` | Bearer | Implemented | End and clone to DRAFT |
| GET | `/api/v1/sales` | Public | Implemented | Public STANDARD/FLASH list |
| GET | `/api/v1/sales/{code}` | Public | Implemented | Public campaign detail |
| GET | `/api/v1/user-addresses/me` | Bearer | Implemented | List principal-owned addresses |
| GET | `/api/v1/user-addresses/me/{id}` | Bearer | Implemented | Get principal-owned address; foreign ID is 404 |
| POST | `/api/v1/user-addresses/me` | Bearer | Implemented | Create address bound to principal; no userId body field |
| PUT | `/api/v1/user-addresses/me/{id}` | Bearer | Implemented | Update principal-owned address |
| DELETE | `/api/v1/user-addresses/me/{id}` | Bearer | Implemented | Delete principal-owned address |
| GET | `/user-addresses` | Bearer | Implemented | List user addresses, optional `userId` filter |
| GET | `/user-addresses/{id}` | Bearer | Implemented | Get user address |
| POST | `/user-addresses` | Bearer | Implemented | Create user address |
| PUT | `/user-addresses/{id}` | Bearer | Implemented | Update user address |
| DELETE | `/user-addresses/{id}` | Bearer | Implemented | Delete user address |
| GET | `/coupons` | Bearer | Implemented | List coupons |
| GET | `/coupons/{id}` | Bearer | Implemented | Get coupon |
| POST | `/coupons` | Bearer | Implemented | Create coupon |
| PUT | `/coupons/{id}` | Bearer | Implemented | Update coupon |
| DELETE | `/coupons/{id}` | Bearer | Implemented | Delete coupon |
| GET | `/carts` | Bearer | Implemented | List carts |
| GET | `/carts/{id}` | Bearer | Implemented | Get cart |
| GET | `/carts/user/{userId}` | Bearer | Implemented | Get cart by user |
| POST | `/carts` | Bearer | Implemented | Create cart |
| DELETE | `/carts/{id}` | Bearer | Implemented | Delete cart |
| GET | `/wishlists` | Bearer | Implemented | List wishlists, optional `userId`/`productId` filters |
| GET | `/wishlists/{id}` | Bearer | Implemented | Get wishlist item |
| POST | `/wishlists` | Bearer | Implemented | Create wishlist item |
| DELETE | `/wishlists/{id}` | Bearer | Implemented | Delete wishlist item |
| GET | `/api/v1/orders/me` | Bearer | Implemented | List principal-owned orders |
| GET | `/api/v1/orders/me/{id}` | Bearer | Implemented | Get principal-owned order; foreign ID is 404 |
| GET | `/api/v1/orders/me/code/{orderCode}` | Bearer | Implemented | Get principal-owned order by code; foreign code is 404 |
| GET | `/api/v1/orders/me/{id}/status-histories` | Bearer | Implemented | List history for a principal-owned order |
| GET | `/orders` | Bearer | Implemented | List orders |
| GET | `/orders/{id}` | Bearer | Implemented | Get order by id |
| GET | `/orders/code/{orderCode}` | Bearer | Implemented | Get order by code |
| GET | `/orders/user/{userId}` | Bearer | Implemented | List orders by user |
| GET | `/orders/{id}/status-histories` | Bearer | Implemented | List order status histories |
| POST | `/orders` | Bearer | Implemented | Create order |
| PUT | `/orders/{id}` | Bearer | Implemented | Update order |
| DELETE | `/orders/{id}` | Bearer | Implemented | Delete order |
| GET | `/payments` | Bearer | Implemented | List payments |
| GET | `/payments/{id}` | Bearer | Implemented | Get payment |
| POST | `/payments` | Bearer | Implemented | Create payment |
| PUT | `/payments/{id}` | Bearer | Implemented | Update payment |
| DELETE | `/payments/{id}` | Bearer | Implemented | Delete payment |
| GET | `/reviews` | Bearer | Implemented | List reviews |
| GET | `/reviews/user/{userId}` | Bearer | Implemented | List reviews by user |
| GET | `/reviews/order/{orderId}` | Bearer | Implemented | List reviews by order |
| GET | `/reviews/order-item/{orderItemId}` | Bearer | Implemented | List reviews by order item |
| GET | `/reviews/product/{productId}` | Public | Implemented | Public filtered/sorted product reviews |
| GET | `/reviews/product/{productId}/summary` | Public | Implemented | Public review summary and star distribution |
| GET | `/reviews/me` | Bearer | Implemented | Principal-scoped review list, optional order filter |
| POST | `/reviews` | Bearer | Implemented | Create verified review with optional multipart images |
| POST | `/api/v1/checkout/preview` | Bearer | Implemented | Authoritative checkout pricing preview |
| POST | `/api/v1/checkout` | Bearer | Implemented | Idempotent checkout and Sale reservation |
| POST | `/api/v1/checkout/{orderId}/cancel` | Bearer | Implemented | Cancel and release/reverse resources |

---

## 9. Checkout and Sale Reservation

Checkout luôn tải item/quantity từ cart đã lưu trong database. Client không gửi
giá, quota, subtotal hoặc shipping fee làm nguồn sự thật. Add-to-cart không giữ
stock/quota; reservation chỉ bắt đầu khi checkout transaction thành công.

### POST /api/v1/checkout/preview Bearer

Tính giá hiện hành, coupon eligibility, phí giao hàng và fingerprint trước khi
người dùng xác nhận đặt đơn.

Checkout flow hiện hỗ trợ `COD` và `SEPAY`; giá trị khác trả `400`.

**Request Body:**

```json
{
  "paymentMethod": "SEPAY",
  "couponCode": "SUMMER10"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "items": [
      {
        "variantId": 41,
        "productId": 7,
        "productName": "Running Shoes",
        "sku": "RUN-SHOE-BLACK-40",
        "quantity": 1,
        "listPrice": 1500000,
        "price": 990000,
        "priceSource": "FLASH_SALE",
        "subtotal": 990000,
        "saleCampaignItemId": 84,
        "saleCampaignCode": "FLASH-2000",
        "saleCampaignName": "Flash Sale 20h",
        "pricing": {
          "listPrice": 1500000,
          "effectivePrice": 990000,
          "priceSource": "FLASH_SALE",
          "campaignId": 12,
          "campaignItemId": 84,
          "campaignCode": "FLASH-2000",
          "campaignName": "Flash Sale 20h",
          "startsAt": "2026-07-15T13:00:00Z",
          "endsAt": "2026-07-15T15:00:00Z",
          "remainingQuota": 8,
          "maxPerCustomer": 2,
          "customerRemaining": 1,
          "couponEligible": false,
          "availableQuantity": 1
        },
        "couponEligible": false
      },
      {
        "variantId": 52,
        "productId": 9,
        "productName": "Training Tee",
        "sku": "TEE-WHITE-M",
        "quantity": 1,
        "listPrice": 800000,
        "price": 720000,
        "priceSource": "STANDARD_SALE",
        "subtotal": 720000,
        "saleCampaignItemId": 91,
        "saleCampaignCode": "SUMMER-2026",
        "saleCampaignName": "Summer Sale 2026",
        "pricing": {
          "listPrice": 800000,
          "effectivePrice": 720000,
          "priceSource": "STANDARD_SALE",
          "campaignId": 13,
          "campaignItemId": 91,
          "campaignCode": "SUMMER-2026",
          "campaignName": "Summer Sale 2026",
          "startsAt": "2026-07-01T00:00:00Z",
          "endsAt": "2026-08-01T00:00:00Z",
          "remainingQuota": null,
          "maxPerCustomer": null,
          "customerRemaining": null,
          "couponEligible": true,
          "availableQuantity": 18
        },
        "couponEligible": true
      }
    ],
    "subtotal": 1710000,
    "couponEligibleSubtotal": 720000,
    "shippingFee": 30000,
    "discountAmount": 72000,
    "finalAmount": 1668000,
    "pricingFingerprint": "2dc90d9f2e34099d8b2135608e8ba332e7106cf047b8b98d30f59196f87623d4",
    "serverTime": "2026-07-15T13:20:00Z"
  },
  "message": "Success",
  "timestamp": "2026-07-15T13:20:00Z"
}
```

`FLASH_SALE` rows are excluded from `couponEligibleSubtotal`. `BASE` and
`STANDARD_SALE` rows continue through normal coupon product/category rules.

### POST /api/v1/checkout Bearer

Creates the order, atomically reserves stock/Flash quota/customer usage,
consumes coupon, snapshots price source, creates payment/allocation, and clears
the cart.

**Required Header:**

```http
Idempotency-Key: 43b34a30-444a-4bb7-9845-8ca560f67df0
```

**Request Body:**

```json
{
  "receiverName": "Nguyen Van A",
  "receiverPhone": "0901234567",
  "receiverAddress": "123 Nguyen Hue, TP.HCM",
  "paymentMethod": "SEPAY",
  "couponCode": "SUMMER10",
  "pricingFingerprint": "2dc90d9f2e34099d8b2135608e8ba332e7106cf047b8b98d30f59196f87623d4"
}
```

`shippingFee` cũ vẫn có thể xuất hiện trong request để tương thích client cũ
nhưng không được dùng để tính tiền. Backend lấy phí từ cấu hình
`app.checkout.shipping-fee` và trả giá trị chuẩn trong preview/checkout.
`pricingFingerprint` là bắt buộc và phải lấy từ response preview gần nhất;
thiếu/rỗng trả validation `400`, còn fingerprint đã cũ trả `PRICE_CHANGED`.

**Success Response (201):**

```json
{
  "statusCode": 201,
  "data": {
    "orderId": 1,
    "orderCode": "VELA-A1B2C3D4",
    "status": "PENDING",
    "subtotal": 1710000,
    "shippingFee": 30000,
    "discountAmount": 72000,
    "finalAmount": 1668000,
    "receiverName": "Nguyen Van A",
    "receiverPhone": "0901234567",
    "receiverAddress": "123 Nguyen Hue, TP.HCM",
    "paymentMethod": "SEPAY",
    "paymentStatus": "UNPAID",
    "paymentId": 10,
    "paymentDueAt": "2026-07-15T13:35:00Z",
    "reservationExpiresAt": "2026-07-15T13:35:30Z",
    "items": [
      {
        "orderItemId": 101,
        "variantId": 41,
        "productName": "Running Shoes",
        "variantName": "Black / 40",
        "sku": "RUN-SHOE-BLACK-40",
        "quantity": 1,
        "listPrice": 1500000,
        "price": 990000,
        "priceSource": "FLASH_SALE",
        "saleCampaignItemId": 84,
        "saleCampaignCode": "FLASH-2000",
        "saleCampaignName": "Flash Sale 20h",
        "subtotal": 990000
      }
    ],
    "paymentInitiation": {
      "provider": "SEPAY",
      "method": "POST",
      "actionUrl": "https://pay.sepay.vn/v1/checkout/init",
      "fields": {}
    },
    "createdAt": "2026-07-15T13:20:00Z"
  },
  "message": "Created",
  "timestamp": "2026-07-15T13:20:00Z"
}
```

Retry with the same user, `Idempotency-Key`, and request payload returns the
same order. Reusing the key for another payload returns
`IDEMPOTENCY_KEY_REUSED`.
For an unpaid SePay order, the checkout form is returned again only before
`paymentDueAt`; replay after `reservationExpiresAt` releases resources once and
does not issue another payment form.

**Business Errors:**

| Code | HTTP | When |
|------|------|------|
| `COUPON_INVALID` | 400 | Coupon does not exist, expired, or fails its conditions |
| `IDEMPOTENCY_KEY_INVALID` | 400 | Idempotency header is blank or longer than 100 characters |
| `MISSING_REQUEST_HEADER` | 400 | A required header such as `Idempotency-Key` is absent |
| `INSUFFICIENT_STOCK` | 409 | Variant stock is lower than cart quantity |
| `FLASH_SALE_SOLD_OUT` | 409 | Flash quota is insufficient |
| `FLASH_SALE_ENDED` | 409 | Campaign is no longer eligible |
| `FLASH_SALE_LIMIT_EXCEEDED` | 409 | Customer cumulative limit would be exceeded |
| `PRICE_CHANGED` | 409 | Pricing fingerprint no longer matches server pricing |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Same idempotency key is used with another request hash |

No partial reservation remains after an error: stock, quota, customer usage,
coupon, order and payment participate in one transaction.

### POST /api/v1/checkout/{orderId}/cancel Bearer

Cancels an eligible order and releases/reverses stock, coupon, Flash allocation
and customer usage exactly once.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Success",
  "timestamp": "2026-07-15T13:25:00Z"
}
```

| Status | When |
|--------|------|
| 400 | Invalid order transition |
| 400 | Order does not belong to authenticated user |
| 404 | Order not found |
| 409 | Concurrent payment/cancel/timeout transition already won |

### Payment timeout and late IPN

- Online payment is due in 15 minutes; reservation expires 30 seconds later.
- Payment IPN and timeout scheduler lock/check the same persisted state.
- If payment succeeds before release, allocation moves `RESERVED -> CONFIRMED`.
- Timeout/cancel moves `RESERVED -> RELEASED`.
- Money received after release sets payment state to `REFUND_PENDING`; the order
  is not automatically restored.
- A second captured gateway transaction for an already paid order is recorded
  as `REFUND_PENDING`; it does not confirm stock/quota again or overwrite the
  original transaction code.
- COD creates `CONFIRMED` allocations immediately. Valid cancellation moves
  them to `REVERSED` exactly once. COD collection may move `UNPAID -> PAID`;
  shipped/completed orders cannot use cancellation to restore stock.
