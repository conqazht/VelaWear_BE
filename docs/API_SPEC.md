# API Specification

> All endpoints return `ApiResponse<T>` wrapper.
> Update this file whenever endpoints change.

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
| POST | `/api/v1/auth/register` | Register customer/user account |
| POST | `/api/v1/auth/refresh` | Rotate refresh token and issue new access token |
| POST | `/api/v1/auth/logout` | Revoke refresh token |
| GET | `/actuator/health` | Health check |
| GET | `/v3/api-docs/**` | OpenAPI docs |
| GET | `/swagger-ui/**` | Swagger UI |

> Auth endpoints are implemented. Access tokens and refresh tokens are signed with HS512.
> Raw refresh JWTs are returned to clients, while the database stores only SHA-512 hashes for revoke/rotate.

Auth token configuration:

| Environment variable | Default | Description |
|----------------------|---------|-------------|
| `JWT_SECRET_KEY` | dev fallback only | HMAC signing secret. Required in production. |
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

Collection endpoints should use the paginated response contract below. Current
controller code may still return a plain array while pagination is being
implemented, but the target API contract is `meta + result` for all list APIs.

**Query parameters for paginated list endpoints:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | integer | `1` | 1-based page number |
| `pageSize` | integer | `10` | Number of records per page |
| `sort` | string | module default | Sort expression, for example `createdAt,desc` |
| `keyword` | string | optional | Generic keyword search when supported |

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
    "updatedAt": "2026-06-14T14:00:00Z"
  },
  "message": "Success",
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
        "updatedAt": "2026-06-14T14:00:00Z"
      }
    ]
  },
  "message": "Success",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

### GET /users/{id}

Get a single user by numeric ID.

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

Get role by UUID.

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

## 7. Business Modules Implemented

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

### Product Variants

Product variant is the sellable SKU. Variant price can differ by color and size.
`Product` stores base catalog information; `ProductVariant` stores `price`,
`salePrice`, `stockQuantity`, `color`, and `size`.

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
  "salePrice": 990000.00,
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
    "salePrice": 990000.00,
    "stockQuantity": 50,
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
