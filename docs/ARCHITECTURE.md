# Architecture

> System design overview. Update only during architecture review sessions.

---

## High-Level Architecture

```
                         ┌─────────────────┐
                         │   Client (SPA)   │
                         └────────┬─────────┘
                                  │ HTTPS
                                  ▼
                         ┌─────────────────┐
                         │   Spring Boot    │
                         │   Application    │
                         └────────┬─────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                    ▼
     ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
     │   Controller   │  │ Security Layer │  │ Exception      │
     │   (REST API)   │  │ (JWT + RBAC)   │  │ Handler        │
     └───────┬────────┘  └────────────────┘  └────────────────┘
             │
             ▼
     ┌────────────────┐
     │   Service      │
     │ (Interface +   │
     │  Impl)         │
     └───────┬────────┘
             │
             ▼
     ┌────────────────┐
     │  Repository    │
     │  (Spring Data) │
     └───────┬────────┘
             │
             ▼
     ┌────────────────┐
     │  PostgreSQL    │
     └────────────────┘
```

---

## Request Flow

### Standard CRUD Request
```
Client
  → [HTTP Request]
  → SecurityFilterChain (JWT validation via oauth2-resource-server)
  → Controller (receive request, validate with @Valid)
  → Service (business logic, entity ↔ DTO conversion)
  → Repository (JPA query)
  → Database
  → Repository (return Entity)
  → Service (convert Entity → Response DTO)
  → Controller (wrap in ApiResponse)
  → [HTTP Response]
  → Client
```

### Authentication Flow
```
1. Login:
   Client → POST /api/v1/auth/login (email + password)
   → AuthController → AuthService
   → AuthenticationManager.authenticate()
   → CustomUserDetailsService.loadUserByUsername() → query DB
   → Password verified (BCrypt)
   → JwtEncoder creates access token (15min) + refresh token (7d)
   → Return tokens to client

2. Authenticated Request:
   Client → [Authorization: Bearer <access_token>]
   → SecurityFilterChain → oauth2ResourceServer
   → JwtDecoder verifies token automatically (HS512)
   → SecurityContext populated with user info
   → Controller → Service → Repository → Response

3. Token Refresh:
   Client → POST /api/v1/auth/refresh (refresh token)
   → Validate refresh token → Issue new access token
   → Return new tokens
```

### Permission Check Flow
```
   Request arrives with JWT
   → JWT contains: sub (email), userId, roles
   → For endpoint-level: Spring Security checks roles
   → For fine-grained: Permission entity maps (apiPath + method) to Role
   → Service layer can check: does user's role have permission for this action?
```

---

## Feature Package Structure

Each business feature is self-contained:

```
feature/
├── auth/                    # Authentication & token management
├── user/                    # User CRUD + profile
├── company/                 # Company CRUD
├── role/                    # Role CRUD + assign permissions
└── permission/              # Permission CRUD + RBAC mapping
```

### Feature Dependencies

```
permission  ← (no dependencies)
     ↑
   role     ← permission (ManyToMany: role has permissions)
     ↑
   user     ← role (ManyToMany: user has roles)
     │
     └──── ← company (ManyToOne: user belongs to company)
             company ← (no dependencies)

auth        ← user (authenticate, generate token)
```

Dependency rules:
- `permission` and `company` are independent — no dependencies on other features
- `role` depends on `permission`
- `user` depends on `role` and `company`
- `auth` depends on `user`
- **No circular dependencies allowed**

---

## Cross-Cutting Concerns

### Security
- JWT via Spring Security's oauth2-resource-server — no custom filter
- Algorithm: HS512 (symmetric secret key)
- Access token: 15 min / Refresh token: 7 days
- Secret key loaded from environment variable, never hardcoded
- Permission-based authorization: each Permission maps an API path + HTTP method to roles

### Exception Handling
- `GlobalExceptionHandler` (`@RestControllerAdvice`) catches all exceptions
- All responses use `ApiResponse<T>` wrapper — including errors
- No stack traces or SQL errors exposed to client

### Audit Fields
- Every entity has: `createdAt`, `updatedAt` (managed by Hibernate)
- Timestamps use `Instant` type

### DTO Strategy
- Request/Response DTOs are Java Records
- Entity never exposed outside Service layer
- Response DTO has `static fromEntity()` factory method
- Feature-specific DTOs live inside feature's `dto/` sub-package

---

## Scalability Notes

### Current Design (MVP)
- Single Spring Boot instance
- Single PostgreSQL database
- Stateless JWT — no server-side session storage
- Suitable for: < 10k users, single region deployment

### Future Considerations (not implemented yet)
- Horizontal scaling: stateless design already supports multiple instances behind load balancer
- Caching: Spring Cache + Redis for frequently accessed data (roles, permissions)
- File storage: if avatar/logo upload needed, use external storage (S3) with presigned URLs
- Search: if full-text search needed, consider Elasticsearch for user/company search
