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
   → AuthTokenCodec creates access token (15min) + refresh token (7d)
   → Return tokens to client

2. Authenticated Request:
   Client → [Authorization: Bearer <access_token>]
   → SecurityFilterChain → oauth2ResourceServer
   → JwtDecoder verifies token automatically using HS512
   → SecurityContext populated with user info
   → Controller → Service → Repository → Response

3. Token Refresh:
   Client → POST /api/v1/auth/refresh (refresh token)
   → Verify refresh JWT (`type=refresh`) → Check DB hash/revoked/expires_at
   → Rotate refresh token and issue new access token
   → Return new tokens and set refresh cookie
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
├── auth/                    # Authentication & registration (AuthTokenCodec, UserSessionService)
├── checkout/                # Checkout quote & preview orchestration (CheckoutFingerprintService)
├── order/                   # Authoritative fulfillment & resource lifecycle (OrderFulfillmentService)
├── salecampaign/            # Sale campaign management & quota engine (CampaignReservationService, SaleCampaignValidator)
├── catalog/                 # Unified catalog display, thumbnail resolution & localization (CatalogDisplayService, CatalogLocaleHelper)
├── product/                 # Product CRUD
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
- `order` owns post-checkout fulfillment & release transitions (0 dependency on `checkout`)
- **No circular dependencies allowed**

---

## Key Design Patterns

### Service Layer Deepening
- Deep domain engines encapsulate transaction boundaries, locks, and complex subsystems:
  - `OrderFulfillmentService`: Authoritative confirmation and atomic rollback engine for inventory, coupons, and flash quotas across all order cancellation/timeout pathways.
  - `CampaignReservationService`: Quota reservations, customer limits, and standard sale validity verification with pessimistic locks.
  - `CatalogDisplayService`: Unified projections for product detail, storefront, cart items, wishlist summaries, and canonical image thumbnail resolution.
  - `UserSessionService`: Encapsulates Redis Lua script sessions + PostgreSQL audit dual-store lifecycle, rotation, and blacklisting.

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
- Dedicated engine services (`CatalogDisplayService`, `CheckoutFingerprintService`, etc.) handle DTO creation
- Feature-specific DTOs live inside feature's `dto/` sub-package

---

## Scalability Notes

### Current Design
- Single Spring Boot instance with Redis session tracking & token revocation
- Single PostgreSQL database with Flyway migrations & Testcontainers integration
- Local file storage for avatar/product/review uploads (`FileStorageServiceImpl`)
- Suitable for: < 10k users, single region deployment

### Future Considerations (not implemented yet)
- Horizontal scaling: stateless JWT token validation + Redis session revocation cluster behind load balancer
- Caching: Spring Cache + Redis for frequently accessed data (roles, permissions, storefront catalog)
- Cloud file storage: optional S3 / Object Storage integration with presigned URLs when scaling beyond local volume storage
- Search: if full-text search needed, consider Elasticsearch for user/product search
