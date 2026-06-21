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
| POST | `/auth/login` | Login and receive access/refresh tokens |
| POST | `/auth/register` | Register customer/user account |
| POST | `/auth/refresh` | Rotate refresh token and issue new access token |
| GET | `/actuator/health` | Health check |
| GET | `/v3/api-docs/**` | OpenAPI docs |
| GET | `/swagger-ui/**` | Swagger UI |

> Auth endpoints are planned but not implemented yet. Security is already configured with Spring Security oauth2-resource-server.

---

## Response Format

All successful responses follow:

```json
{
  "statusCode": 200,
  "data": {},
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

Application errors:

```json
{
  "statusCode": 404,
  "data": null,
  "message": "User not found with id: 1",
  "timestamp": "2026-06-14T21:00:00"
}
```

---

## 1. Auth Planned

### POST /auth/login Public

Login and receive JWT tokens.

**Request Body:**

```json
{
  "email": "admin@example.com",
  "password": "password123"
}
```

**Success Response (200):**

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
| 423 | User account is locked |

---

### POST /auth/register Public

Register a storefront customer account.

**Request Body:**

```json
{
  "email": "customer@example.com",
  "username": "customer01",
  "password": "password123",
  "fullName": "Nguyen Van A",
  "phone": "0900000000"
}
```

**Success Response (201):**

```json
{
  "statusCode": 201,
  "data": {
    "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a132",
    "email": "customer@example.com",
    "username": "customer01",
    "fullName": "Nguyen Van A",
    "phone": "0900000000",
    "status": "ACTIVE",
    "emailVerified": false,
    "createdAt": "2026-06-14T14:00:00Z"
  },
  "message": "Created",
  "timestamp": "2026-06-14T21:00:00"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 409 | Email or username already exists |

---

### POST /auth/refresh Public

Get a new access token using refresh token.

**Request Body:**

```json
{
  "refreshToken": "eyJ..."
}
```

**Success Response (200):**

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

### POST /auth/logout

Invalidate refresh token.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": null,
  "message": "Success",
  "timestamp": "2026-06-14T21:30:00"
}
```

---

### GET /auth/me

Get current authenticated user.

**Success Response (200):**

```json
{
  "statusCode": 200,
  "data": {
    "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a132",
    "email": "admin@example.com",
    "username": "admin",
    "fullName": "System Admin",
    "roles": ["SUPER_ADMIN"],
    "permissions": ["USER_READ", "USER_WRITE", "ROLE_READ", "ROLE_WRITE"]
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
  "data": [
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
  ],
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
  "data": [
    {
      "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a201",
      "code": "SUPER_ADMIN",
      "name": "Super Admin",
      "description": "Full system access.",
      "systemRole": true,
      "createdAt": "2026-06-14T14:00:00Z",
      "updatedAt": "2026-06-14T14:00:00Z"
    }
  ],
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
  "data": [
    {
      "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a301",
      "code": "CATALOG_WRITE",
      "name": "Write catalog",
      "module": "CATALOG",
      "description": "Create and update categories, products, and variants.",
      "createdAt": "2026-06-14T14:00:00Z"
    }
  ],
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
  "data": [
    {
      "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a401",
      "parentId": null,
      "name": "Electronics",
      "slug": "electronics",
      "description": "Electronic products.",
      "sortOrder": 0,
      "active": true,
      "createdAt": "2026-06-14T14:00:00Z",
      "updatedAt": "2026-06-14T14:00:00Z"
    }
  ],
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
  "data": [
    {
      "id": "018fd0e8-6e0e-7d41-9f38-5962a3d4a501",
      "categoryId": "018fd0e8-6e0e-7d41-9f38-5962a3d4a401",
      "sku": "PHONE-001",
      "name": "Smartphone A",
      "slug": "smartphone-a",
      "description": "Base product for Smartphone A.",
      "status": "ACTIVE",
      "basePrice": 12000000.00,
      "currency": "VND",
      "createdAt": "2026-06-14T14:00:00Z",
      "updatedAt": "2026-06-14T14:00:00Z"
    }
  ],
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
  "categoryId": "018fd0e8-6e0e-7d41-9f38-5962a3d4a401",
  "sku": "PHONE-001",
  "name": "Smartphone A",
  "slug": "smartphone-a",
  "description": "Base product for Smartphone A.",
  "status": "ACTIVE",
  "basePrice": 12000000.00,
  "currency": "VND"
}
```

**Errors:**

| Status | When |
|--------|------|
| 400 | Validation failed |
| 400 | Product sku already exists |
| 400 | Product slug already exists |

---

### PUT /products/{id}

Update product fields. `sku` and `slug` are immutable through this endpoint.

**Request Body:**

```json
{
  "categoryId": "018fd0e8-6e0e-7d41-9f38-5962a3d4a401",
  "name": "Smartphone A 2026",
  "description": "Updated product description.",
  "status": "ACTIVE",
  "basePrice": 11500000.00,
  "currency": "VND"
}
```

---

### DELETE /products/{id}

Soft archive product by setting `status = ARCHIVED` and `deleted_at = now`.

---

## 7. Commercial Features Planned

The database schema already contains these modules, but APIs are not implemented yet.

### Customers

Planned endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/customers` | List customers |
| GET | `/customers/{id}` | Get customer |
| POST | `/customers` | Create customer |
| PUT | `/customers/{id}` | Update customer |
| DELETE | `/customers/{id}` | Soft delete/block customer |

### Addresses

Planned endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/customers/{customerId}/addresses` | List customer addresses |
| POST | `/customers/{customerId}/addresses` | Add address |
| PUT | `/addresses/{id}` | Update address |
| DELETE | `/addresses/{id}` | Delete address |

### Product Variants, Images, Inventory

Planned endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/products/{productId}/variants` | List variants |
| POST | `/products/{productId}/variants` | Create variant |
| GET | `/products/{productId}/images` | List images |
| POST | `/products/{productId}/images` | Add image |
| GET | `/inventory` | List stock records |
| PUT | `/inventory/{id}` | Adjust stock |

### Cart, Orders, Payments, Shipments

Planned endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/cart` | Get current customer cart |
| POST | `/cart/items` | Add item to cart |
| PUT | `/cart/items/{id}` | Update cart item quantity |
| DELETE | `/cart/items/{id}` | Remove cart item |
| POST | `/orders` | Checkout/create order |
| GET | `/orders` | List orders |
| GET | `/orders/{id}` | Get order detail |
| PUT | `/orders/{id}/status` | Update order status |
| POST | `/payments` | Create payment record |
| PUT | `/payments/{id}/status` | Update payment status |
| POST | `/shipments` | Create shipment |
| PUT | `/shipments/{id}/status` | Update shipment status |

---

## Endpoint Summary

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/auth/login` | Public | Planned | Login |
| POST | `/auth/register` | Public | Planned | Register |
| POST | `/auth/refresh` | Public | Planned | Refresh token |
| POST | `/auth/logout` | Bearer | Planned | Logout |
| GET | `/auth/me` | Bearer | Planned | Current user |
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
