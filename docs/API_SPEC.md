# API Specification

> All endpoints return `ApiResponse<T>` wrapper.
> Update this file whenever endpoints change.
> Sale Campaign design, state machine và race-condition notes: [SALE_CAMPAIGN_BACKEND.md](./SALE_CAMPAIGN_BACKEND.md).

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
| POST | `/api/v1/auth/otp/request` | Request an OTP code via email |
| POST | `/api/v1/auth/otp/verify` | Verify email OTP code |
| POST | `/api/v1/auth/forgot-password/reset` | Reset password using verified OTP |
| PUT | `/api/v1/auth/me/email` | Change email using verified OTP |
| GET | `/api/v1/sales` | List published, non-ended STANDARD/FLASH campaigns; phase is returned per row |
| GET | `/api/v1/sales/{code}` | Public campaign detail and pricing |
| GET | `/actuator/health` | Health check |
| GET | `/v3/api-docs/**` | OpenAPI docs |
| GET | `/swagger-ui/**` | Swagger UI |

> Auth endpoints are implemented. Access tokens and refresh tokens are signed with HS512.
> Raw refresh JWTs are returned to clients, while the database stores only SHA-512 hashes for revoke/rotate.

Auth token configuration:

| `JWT_ACCESS_TOKEN_SECRET_KEY` | dev fallback only | HMAC signing secret for Access tokens. Required in production. |
| `JWT_REFRESH_TOKEN_SECRET_KEY` | dev fallback only | HMAC signing secret for Refresh tokens. Required in production. |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900` | Access token lifetime in seconds, 15 minutes by default. |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `259200` | Refresh token lifetime in seconds, 3 days by default. |

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
| `500 Internal Server Error` | Unexpected server error |

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
- Sorting is delegated to Spring `Pageable`; there is no custom `sortBy` or `sortDir` parser.

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
  "gender": "MALE"
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
| 409 | Email already exists |

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

Request an OTP code via email.

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
  "data": null,
  "message": "OTP generated and sent successfully",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### POST /api/v1/auth/otp/verify Public

Verify email OTP code.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "purpose": "REGISTER",
  "code": "123456"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "OTP verified successfully",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### POST /api/v1/auth/forgot-password/reset Public

Reset password using verified OTP.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "newPassword": "newPassword123"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Password reset successfully",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### PUT /api/v1/auth/me/email Bearer Implemented

Change authenticated user's email using verified OTP (for the new email).

**Request Body:**

```json
{
  "newEmail": "newemail@example.com"
}
```

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Email updated successfully",
  "timestamp": "2026-06-14T21:00:00"
}
```

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

### PUT /users/{id}

Update profile fields. Email and password are not updated by this endpoint.

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

## 7.1 Locale và nội dung động

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
| GET | `/user-addresses` | List addresses, optionally filter by `userId` |
| GET | `/user-addresses/{id}` | Get address |
| POST | `/user-addresses` | Create address |
| PUT | `/user-addresses/{id}` | Update address |
| DELETE | `/user-addresses/{id}` | Delete address |

### Coupon, Cart, Wishlist

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/coupons` | List coupons |
| GET | `/coupons/{id}` | Get coupon |
| POST | `/coupons` | Create coupon |
| PUT | `/coupons/{id}` | Update coupon |
| DELETE | `/coupons/{id}` | Delete coupon |
| GET | `/carts` | List carts |
| GET | `/carts/{id}` | Get cart |
| GET | `/carts/user/{userId}` | Get cart by user |
| POST | `/carts` | Create cart |
| DELETE | `/carts/{id}` | Delete cart |
| GET | `/wishlists` | List wishlists, optionally filter by `userId` and `productId` |
| GET | `/wishlists/{id}` | Get wishlist item |
| POST | `/wishlists` | Create wishlist item |
| DELETE | `/wishlists/{id}` | Delete wishlist item |

### Orders, Payments, Reviews

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/orders` | List orders |
| GET | `/orders/{id}` | Get order by id |
| GET | `/orders/code/{orderCode}` | Get order by business code |
| GET | `/orders/user/{userId}` | List orders by user |
| GET | `/orders/{id}/status-histories` | List status history of an order |
| POST | `/orders` | Create order |
| PUT | `/orders/{id}` | Update order |
| DELETE | `/orders/{id}` | Delete order |
| GET | `/payments` | List payments |
| GET | `/payments/{id}` | Get payment |
| POST | `/payments` | Create payment |
| PUT | `/payments/{id}` | Update payment |
| DELETE | `/payments/{id}` | Delete payment |
| GET | `/reviews` | List reviews |
| GET | `/reviews/user/{userId}` | List reviews by user |
| GET | `/reviews/order/{orderId}` | List reviews by order |
| GET | `/reviews/order-item/{orderItemId}` | List reviews by order item |
| POST | `/reviews` | Create review |

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

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/api/v1/auth/login` | Public | Implemented | Login |
| POST | `/api/v1/auth/register` | Public | Implemented | Register |
| POST | `/api/v1/auth/refresh` | Public | Implemented | Refresh token |
| POST | `/api/v1/auth/logout` | Public | Implemented | Logout |
| GET | `/api/v1/auth/me` | Bearer | Implemented | Current user |
| GET | `/users` | Bearer | Implemented | List users |
| GET | `/users/{id}` | Bearer | Implemented | Get user |
| POST | `/users` | Bearer | Implemented | Create user |
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
| POST | `/reviews` | Bearer | Implemented | Create review |
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
