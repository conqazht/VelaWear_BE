# Database Schema

> PostgreSQL database design for the Commercial application.
> Update this file whenever schema changes.

Schema is managed by Flyway migrations:

- `src/main/resources/db/migration/V1__init_commercial_schema.sql`
- `src/main/resources/db/migration/V2__seed_rbac.sql`

---

## Database Engine

| Item | Value |
|------|-------|
| Engine | PostgreSQL |
| Local database | `commercial` |
| Local username | `commercial` |
| Local password | `commercial` |
| UUID generation | `pgcrypto` / `gen_random_uuid()` |
| Timestamp type | `TIMESTAMPTZ` |
| Money type | `NUMERIC(19, 2)` |
| JSON type | `JSONB` |

Run local database:

```powershell
docker compose up -d postgres
```

Default local JDBC connection:

```text
jdbc:postgresql://localhost:5432/commercial
```

Profile files:

| Profile | File | Database Source | Flyway |
|---------|------|-----------------|--------|
| dev | `application-dev.yml` | Local PostgreSQL defaults, overridable by env | enabled |
| test | `application-test.yml` | `TEST_DB_*` env variables | enabled |
| prod | `application-prod.yml` | `DB_*` env variables | enabled |

---

## Entity Relationship Diagram

```text
┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│    users     │       │    roles     │       │   permissions    │
├──────────────┤       ├──────────────┤       ├──────────────────┤
│ id (PK)      │ N:M   │ id (PK)      │ N:M   │ id (PK)          │
│ email        │◄─────►│ code         │◄─────►│ code             │
│ username     │       │ name         │       │ name             │
│ password_hash│       │ system_role  │       │ module           │
│ status       │       └──────────────┘       └──────────────────┘
└──────┬───────┘          user_roles             role_permissions
       │ 1:0..1
       ▼
┌──────────────┐       ┌──────────────┐
│  customers   │ 1:N   │  addresses   │
├──────────────┤◄──────┤──────────────┤
│ id (PK)      │       │ id (PK)      │
│ user_id (FK) │       │ customer_id  │
│ code         │       │ line1        │
│ full_name    │       │ city         │
│ status       │       │ address_type │
└──────┬───────┘       └──────────────┘
       │ 1:N
       ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    carts     │ 1:N   │  cart_items  │ N:1   │product_variants
├──────────────┤◄──────┤──────────────┤──────►├──────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ customer_id  │       │ cart_id      │       │ product_id   │
│ status       │       │ variant_id   │       │ sku          │
└──────────────┘       │ quantity     │       │ attributes   │
                       └──────────────┘       └──────┬───────┘
                                                      │ 1:1
                                                      ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│ categories   │ 1:N   │   products   │ 1:N   │inventory_items
├──────────────┤◄──────┤──────────────┤◄──────├──────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ parent_id    │       │ category_id  │       │ variant_id   │
│ slug         │       │ sku          │       │ on_hand      │
└──────────────┘       │ slug         │       │ reserved     │
                       └──────┬───────┘       └──────────────┘
                              │ 1:N
                              ▼
                       ┌──────────────┐
                       │product_images│
                       └──────────────┘

┌──────────────┐       ┌──────────────┐
│    orders    │ 1:N   │ order_items  │
├──────────────┤◄──────┤──────────────┤
│ id (PK)      │       │ id (PK)      │
│ order_number │       │ order_id     │
│ customer_id  │       │ sku snapshot │
│ status       │       │ price snapshot
│ total_amount │       └──────────────┘
└──────┬───────┘
       │ 1:N
       ├──────────────► payments
       │ 1:N
       └──────────────► shipments
```

---

## Tables

### users

Stores system accounts for admins, staff, and customers.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, default `gen_random_uuid()` | User id |
| email | VARCHAR(255) | NOT NULL, UNIQUE | Login email |
| username | VARCHAR(100) | UNIQUE, nullable | Optional username |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt password hash only |
| full_name | VARCHAR(255) | nullable | Display name |
| phone | VARCHAR(32) | nullable | Phone number |
| avatar_url | TEXT | nullable | Avatar URL |
| status | VARCHAR(30) | NOT NULL, default `ACTIVE` | `ACTIVE`, `INACTIVE`, `LOCKED`, `DELETED` |
| email_verified | BOOLEAN | NOT NULL, default false | Email verification flag |
| last_login_at | TIMESTAMPTZ | nullable | Last login time |
| created_at | TIMESTAMPTZ | NOT NULL, default now | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL, default now | Updated time |
| deleted_at | TIMESTAMPTZ | nullable | Soft delete time |

Indexes/constraints:

- `uq_users_email`
- `uq_users_username`
- `idx_users_status`
- `ck_users_status`

### roles

Stores RBAC roles.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Role id |
| code | VARCHAR(100) | NOT NULL, UNIQUE | Stable role code |
| name | VARCHAR(150) | NOT NULL | Display name |
| description | TEXT | nullable | Role description |
| system_role | BOOLEAN | NOT NULL, default false | Seeded/system role marker |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

Seed roles:

| Code | Description |
|------|-------------|
| SUPER_ADMIN | Full system access |
| ADMIN | Back-office operations |
| STAFF | Catalog and order operations |
| CUSTOMER | Storefront customer |

### permissions

Stores RBAC permissions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Permission id |
| code | VARCHAR(150) | NOT NULL, UNIQUE | Stable permission code |
| name | VARCHAR(150) | NOT NULL | Display name |
| module | VARCHAR(80) | NOT NULL | Business module |
| description | TEXT | nullable | Permission description |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |

Seed modules:

- `USER`
- `RBAC`
- `CATALOG`
- `INVENTORY`
- `ORDER`
- `PAYMENT`
- `SHIPMENT`
- `CUSTOMER`

### user_roles

Join table between users and roles.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| user_id | UUID | PK, FK -> users(id), ON DELETE CASCADE | Assigned user |
| role_id | UUID | PK, FK -> roles(id), ON DELETE CASCADE | Assigned role |
| assigned_by | UUID | FK -> users(id), ON DELETE SET NULL | Admin/staff who assigned |
| assigned_at | TIMESTAMPTZ | NOT NULL, default now | Assignment time |

### role_permissions

Join table between roles and permissions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| role_id | UUID | PK, FK -> roles(id), ON DELETE CASCADE | Role |
| permission_id | UUID | PK, FK -> permissions(id), ON DELETE CASCADE | Permission |
| created_at | TIMESTAMPTZ | NOT NULL, default now | Mapping creation time |

### customers

Stores commerce customer profiles.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Customer id |
| user_id | UUID | UNIQUE, FK -> users(id), ON DELETE SET NULL | Linked account |
| code | VARCHAR(50) | NOT NULL, UNIQUE | Customer code |
| full_name | VARCHAR(255) | NOT NULL | Customer full name |
| email | VARCHAR(255) | nullable | Contact email |
| phone | VARCHAR(32) | nullable | Contact phone |
| status | VARCHAR(30) | NOT NULL, default `ACTIVE` | `ACTIVE`, `INACTIVE`, `BLOCKED` |
| note | TEXT | nullable | Internal note |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |
| deleted_at | TIMESTAMPTZ | nullable | Soft delete time |

### addresses

Stores shipping and billing addresses.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Address id |
| customer_id | UUID | NOT NULL, FK -> customers(id), ON DELETE CASCADE | Owner |
| recipient_name | VARCHAR(255) | NOT NULL | Recipient |
| phone | VARCHAR(32) | NOT NULL | Recipient phone |
| line1 | VARCHAR(255) | NOT NULL | Street/address line |
| line2 | VARCHAR(255) | nullable | Extra line |
| ward | VARCHAR(120) | nullable | Ward |
| district | VARCHAR(120) | nullable | District |
| city | VARCHAR(120) | NOT NULL | City |
| province | VARCHAR(120) | nullable | Province |
| country | VARCHAR(120) | NOT NULL, default `Vietnam` | Country |
| postal_code | VARCHAR(30) | nullable | Postal code |
| address_type | VARCHAR(30) | NOT NULL, default `SHIPPING` | `SHIPPING`, `BILLING` |
| is_default | BOOLEAN | NOT NULL, default false | Default address flag |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

### categories

Stores product category tree.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Category id |
| parent_id | UUID | FK -> categories(id), ON DELETE SET NULL | Parent category |
| name | VARCHAR(180) | NOT NULL | Category name |
| slug | VARCHAR(220) | NOT NULL, UNIQUE | URL slug |
| description | TEXT | nullable | Description |
| sort_order | INTEGER | NOT NULL, default 0 | Display order |
| active | BOOLEAN | NOT NULL, default true | Visibility flag |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

### products

Stores base products.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Product id |
| category_id | UUID | FK -> categories(id), ON DELETE SET NULL | Category |
| sku | VARCHAR(80) | NOT NULL, UNIQUE | Base SKU |
| name | VARCHAR(255) | NOT NULL | Product name |
| slug | VARCHAR(280) | NOT NULL, UNIQUE | URL slug |
| description | TEXT | nullable | Description |
| status | VARCHAR(30) | NOT NULL, default `DRAFT` | `DRAFT`, `ACTIVE`, `INACTIVE`, `ARCHIVED` |
| base_price | NUMERIC(19,2) | NOT NULL, default 0 | Base price |
| currency | CHAR(3) | NOT NULL, default `VND` | ISO currency |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |
| deleted_at | TIMESTAMPTZ | nullable | Archive time |

### product_images

Stores product images.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Image id |
| product_id | UUID | NOT NULL, FK -> products(id), ON DELETE CASCADE | Product |
| image_url | TEXT | NOT NULL | Image URL |
| alt_text | VARCHAR(255) | nullable | Accessibility text |
| sort_order | INTEGER | NOT NULL, default 0 | Display order |
| is_primary | BOOLEAN | NOT NULL, default false | Main image flag |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |

### product_variants

Stores sellable product variants.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Variant id |
| product_id | UUID | NOT NULL, FK -> products(id), ON DELETE CASCADE | Base product |
| sku | VARCHAR(100) | NOT NULL, UNIQUE | Variant SKU |
| name | VARCHAR(255) | NOT NULL | Variant name |
| price | NUMERIC(19,2) | NOT NULL | Variant price |
| attributes | JSONB | NOT NULL, default `{}` | Variant attributes such as color/size |
| active | BOOLEAN | NOT NULL, default true | Sellable flag |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

### inventory_items

Stores stock for product variants.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Inventory id |
| product_variant_id | UUID | NOT NULL, UNIQUE, FK -> product_variants(id), ON DELETE CASCADE | Variant |
| quantity_on_hand | INTEGER | NOT NULL, default 0 | Physical stock |
| quantity_reserved | INTEGER | NOT NULL, default 0 | Reserved stock |
| reorder_level | INTEGER | NOT NULL, default 0 | Low stock threshold |
| updated_at | TIMESTAMPTZ | NOT NULL | Last stock update |

### carts

Stores shopping carts.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Cart id |
| customer_id | UUID | FK -> customers(id), ON DELETE CASCADE | Customer |
| status | VARCHAR(30) | NOT NULL, default `ACTIVE` | `ACTIVE`, `ORDERED`, `ABANDONED`, `EXPIRED` |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |
| expires_at | TIMESTAMPTZ | nullable | Expiration time |

### cart_items

Stores cart lines.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Cart item id |
| cart_id | UUID | NOT NULL, FK -> carts(id), ON DELETE CASCADE | Cart |
| product_variant_id | UUID | NOT NULL, FK -> product_variants(id), ON DELETE RESTRICT | Variant |
| quantity | INTEGER | NOT NULL, > 0 | Quantity |
| unit_price | NUMERIC(19,2) | NOT NULL | Price snapshot |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

Unique constraint:

- `uq_cart_items_variant (cart_id, product_variant_id)`

### orders

Stores customer orders.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Order id |
| order_number | VARCHAR(50) | NOT NULL, UNIQUE | Public order number |
| customer_id | UUID | FK -> customers(id), ON DELETE SET NULL | Customer snapshot link |
| status | VARCHAR(30) | NOT NULL, default `PENDING` | Order status |
| subtotal_amount | NUMERIC(19,2) | NOT NULL, default 0 | Items subtotal |
| discount_amount | NUMERIC(19,2) | NOT NULL, default 0 | Discount |
| shipping_amount | NUMERIC(19,2) | NOT NULL, default 0 | Shipping fee |
| tax_amount | NUMERIC(19,2) | NOT NULL, default 0 | Tax |
| total_amount | NUMERIC(19,2) | NOT NULL, default 0 | Final total |
| currency | CHAR(3) | NOT NULL, default `VND` | Currency |
| shipping_address | JSONB | nullable | Address snapshot |
| billing_address | JSONB | nullable | Address snapshot |
| note | TEXT | nullable | Customer/internal note |
| placed_at | TIMESTAMPTZ | nullable | Placement time |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |
| cancelled_at | TIMESTAMPTZ | nullable | Cancellation time |

Order statuses:

- `PENDING`
- `CONFIRMED`
- `PROCESSING`
- `SHIPPED`
- `COMPLETED`
- `CANCELLED`
- `REFUNDED`

### order_items

Stores immutable order line snapshots.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Order item id |
| order_id | UUID | NOT NULL, FK -> orders(id), ON DELETE CASCADE | Order |
| product_id | UUID | FK -> products(id), ON DELETE SET NULL | Product link |
| product_variant_id | UUID | FK -> product_variants(id), ON DELETE SET NULL | Variant link |
| sku | VARCHAR(100) | NOT NULL | SKU snapshot |
| product_name | VARCHAR(255) | NOT NULL | Product name snapshot |
| variant_name | VARCHAR(255) | nullable | Variant name snapshot |
| quantity | INTEGER | NOT NULL, > 0 | Quantity |
| unit_price | NUMERIC(19,2) | NOT NULL | Unit price snapshot |
| total_price | NUMERIC(19,2) | NOT NULL | Line total |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |

### payments

Stores payment records.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Payment id |
| order_id | UUID | NOT NULL, FK -> orders(id), ON DELETE CASCADE | Order |
| provider | VARCHAR(80) | NOT NULL | Payment provider |
| method | VARCHAR(50) | NOT NULL | Payment method |
| status | VARCHAR(30) | NOT NULL, default `PENDING` | Payment status |
| amount | NUMERIC(19,2) | NOT NULL | Amount |
| currency | CHAR(3) | NOT NULL, default `VND` | Currency |
| transaction_id | VARCHAR(150) | nullable | Provider transaction id |
| paid_at | TIMESTAMPTZ | nullable | Paid time |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

Payment statuses:

- `PENDING`
- `AUTHORIZED`
- `PAID`
- `FAILED`
- `CANCELLED`
- `REFUNDED`

### shipments

Stores shipment records.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Shipment id |
| order_id | UUID | NOT NULL, FK -> orders(id), ON DELETE CASCADE | Order |
| carrier | VARCHAR(120) | nullable | Carrier name |
| tracking_number | VARCHAR(150) | nullable | Tracking number |
| status | VARCHAR(30) | NOT NULL, default `PENDING` | Shipment status |
| shipped_at | TIMESTAMPTZ | nullable | Shipped time |
| delivered_at | TIMESTAMPTZ | nullable | Delivered time |
| created_at | TIMESTAMPTZ | NOT NULL | Created time |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated time |

Shipment statuses:

- `PENDING`
- `READY`
- `SHIPPED`
- `DELIVERED`
- `FAILED`
- `RETURNED`

---

## Relationships Summary

| Relationship | Type | Database Rule |
|--------------|------|---------------|
| User ↔ Role | Many-to-many | `user_roles`, cascade delete join rows |
| Role ↔ Permission | Many-to-many | `role_permissions`, cascade delete join rows |
| User → Customer | One-to-zero/one | `customers.user_id`, set null when user deleted |
| Customer → Address | One-to-many | Cascade delete addresses |
| Category → Category | Self reference | Set child parent null when parent deleted |
| Category → Product | One-to-many | Set product category null when category deleted |
| Product → ProductImage | One-to-many | Cascade delete images |
| Product → ProductVariant | One-to-many | Cascade delete variants |
| ProductVariant → InventoryItem | One-to-one | Cascade delete inventory |
| Customer → Cart | One-to-many | Cascade delete carts |
| Cart → CartItem | One-to-many | Cascade delete cart items |
| Customer → Order | One-to-many | Set order customer null when customer deleted |
| Order → OrderItem | One-to-many | Cascade delete order items |
| Order → Payment | One-to-many | Cascade delete payments |
| Order → Shipment | One-to-many | Cascade delete shipments |

---

## JPA Mapping Notes

- Current implemented features map foreign keys as UUID fields for simple CRUD boundaries.
- Future richer aggregate endpoints may introduce `@ManyToOne` and `@OneToMany` mappings where needed.
- Use `FetchType.LAZY` for entity relationships when relationships are introduced.
- Avoid returning entities from controllers; convert to response DTO records in service layer.
- Use Lombok for boilerplate, but do not use `@Data` on JPA entities.
- `createdAt` and `updatedAt` are managed by Hibernate annotations in Java and by defaults in Flyway schema.
- Soft delete is currently used for `users` and `products`.

Example simple UUID FK mapping:

```java
@Column(name = "category_id")
private UUID categoryId;
```

Example future relation mapping:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private Category category;
```

---

## Sample Data

### Roles

| Code | Name | System |
|------|------|--------|
| SUPER_ADMIN | Super Admin | true |
| ADMIN | Admin | true |
| STAFF | Staff | true |
| CUSTOMER | Customer | true |

### Permissions

| Code | Module | Description |
|------|--------|-------------|
| USER_READ | USER | View users and account profile data |
| USER_WRITE | USER | Create and update users |
| ROLE_READ | RBAC | View roles and permissions |
| ROLE_WRITE | RBAC | Create, update, and assign roles |
| CATALOG_READ | CATALOG | View categories, products, and variants |
| CATALOG_WRITE | CATALOG | Create and update categories, products, and variants |
| INVENTORY_READ | INVENTORY | View stock levels |
| INVENTORY_WRITE | INVENTORY | Adjust stock levels |
| ORDER_READ | ORDER | View orders and order items |
| ORDER_WRITE | ORDER | Create and update orders |
| PAYMENT_READ | PAYMENT | View payment records |
| PAYMENT_WRITE | PAYMENT | Create and update payment records |
| SHIPMENT_READ | SHIPMENT | View shipment records |
| SHIPMENT_WRITE | SHIPMENT | Create and update shipment records |
| CUSTOMER_READ | CUSTOMER | View own customer profile, cart, and orders |
| CUSTOMER_WRITE | CUSTOMER | Update own customer profile, cart, and checkout data |

### Role → Permission Mapping

| Role | Permissions |
|------|-------------|
| SUPER_ADMIN | All permissions |
| ADMIN | User read, role read, catalog write/read, inventory write/read, order write/read, payment read, shipment write/read |
| STAFF | Catalog read, inventory read, order write/read, payment read, shipment write/read |
| CUSTOMER | Catalog read, customer write/read, order write, payment write |

---

## Migration Notes

- Total tables in `V1`: 18.
- Seed data is idempotent through `ON CONFLICT DO NOTHING`.
- PostgreSQL extension `pgcrypto` is required for `gen_random_uuid()`.
- Application profiles use `spring.jpa.hibernate.ddl-auto=validate`; Flyway is the schema source of truth.
- Test profile must use PostgreSQL environment variables:

```text
TEST_DB_URL
TEST_DB_USERNAME
TEST_DB_PASSWORD
TEST_JWT_SECRET_KEY
```

---

## Future Schema Candidates

These are likely commercial additions but are not currently in migrations:

- `refresh_tokens` for JWT refresh token rotation/revocation.
- `promotions` and `promotion_redemptions`.
- `coupons`.
- `product_reviews`.
- `audit_logs`.
- `warehouses` and multi-location inventory.
