# Project Status

## 2026-06-14

### Completed

- Added PostgreSQL configuration with Flyway migrations.
- Added commercial base schema:
  - RBAC: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`
  - Customer: `customers`, `addresses`
  - Catalog: `categories`, `products`, `product_images`, `product_variants`, `inventory_items`
  - Sales: `carts`, `cart_items`, `orders`, `order_items`, `payments`, `shipments`
- Seeded base RBAC roles and permissions.
- Rebuilt Java package structure according to `docs/PROJECT-RULES.md`.
- Added top-level `config`, `security`, `exception`, and shared `dto` packages.
- Re-enabled Lombok for boilerplate according to the updated project rules.
- Reworked `docs/API_SPEC.md` and `docs/DATABASE.md` into detailed commercial documentation while keeping the original documentation structure:
  - API docs include auth plan, implemented CRUD endpoints, request/response examples, errors, planned commercial endpoints, and endpoint summary.
  - Database docs include PostgreSQL engine notes, ERD, table-by-table columns, relationships, JPA mapping notes, seed data, migration notes, and future schema candidates.
- Added full Swagger/OpenAPI customization:
  - API metadata, contact, license, local/production servers.
  - JWT bearer security scheme for Swagger UI `Authorize`.
  - Swagger UI path and sorting/display settings.
- Split environment configuration into `dev`, `test`, and `prod` profiles:
  - `application.yaml` contains shared defaults and activates `dev` by default.
  - `application-dev.yml` uses local PostgreSQL defaults and verbose SQL logging.
  - `application-test.yml` uses PostgreSQL test env variables and Flyway.
  - `application-prod.yml` requires env-provided PostgreSQL/JWT config and disables Swagger UI by default.
- Added CRUD features:
  - `feature/user`
  - `feature/role`
  - `feature/permission`
  - `feature/category`
  - `feature/product`

### Notes

- Controllers return `ResponseEntity<ApiResponse<T>>`.
- Services use interface + implementation pattern.
- DTOs are Java records with Jakarta validation.
- Entities and Spring components may use Lombok for boilerplate, but entities must not use `@Data`.
- H2 test dependency was removed to comply with project rules.
- Test database target is PostgreSQL, not H2.

### Verification

- `pom.xml` was validated as XML.
- `mvnw -DskipTests compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-25` after enabling Lombok annotation processing.
- `mvnw -DskipTests test-compile` passed with `JAVA_HOME=C:\Program Files\Java\jdk-25`.
- Full tests were not run because the required `test` profile database environment variables are not configured.
- PostgreSQL migration runtime validation was not completed because Docker Desktop daemon was not running.

## 2026-06-19

### Completed

- Redesigned `docs/DATABASE.md` for the current VelaWear e-commerce schema while preserving the existing documentation format:
  - PostgreSQL engine notes and naming conventions.
  - ERD, table-by-table definitions, constraints, indexes, notes, relationships, JPA mapping examples, sample data, migration notes, and design review notes.
  - RBAC join tables use composite primary keys with `ON DELETE CASCADE` on both foreign keys.
  - Soft-delete tables include indexed `deleted_at` guidance and cleanup-job notes.
- Replaced outdated HR/company schema references with VelaWear commercial tables.

### Notes

- PostgreSQL requires triggers or Hibernate timestamp handling for `updated_at`; there is no direct automatic column clause.
- Current schema design intentionally keeps order/payment/review/inventory history append-friendly and avoids hard deletes in normal workflows.

## 2026-06-19

### Completed

- Updated database documentation to require cleanup/anonymization job policies for every indexed `deleted_at` soft-delete column.
- Updated RBAC JPA guidance to avoid `@ManyToMany`; join tables should be modeled as explicit entities using `@ManyToOne`, `@OneToMany`, `@EmbeddedId`, and `@MapsId`.
- Updated project rules to forbid `@ManyToMany` mappings in JPA entities.

## 2026-06-19

### Completed

- Updated `product_variants` database design with `status`, indexed `deleted_at`, and cleanup retention notes.
- Updated `payment_transactions.transaction_code` to use a unique partial index when the transaction code is not null.
- Clarified product status meanings, especially `DRAFT`.
- Removed resolved items from `Design Review Notes`.

## 2026-06-19

### Completed

- Resolved remaining database design review notes:
  - Added `orders.updated_at`.
  - Clarified immutable order item snapshot fields.
  - Added transaction and row-locking guidance for coupon usage.
  - Kept `colors` and `sizes` as simple master data without audit timestamps.
  - Removed persistent `notifications` table from the current schema; important order/payment events should use email and short-lived UI messages can stay outside database state.

## 2026-06-19

### Completed

- Added project-local Codex skill definitions under `.agents/skills`:
  - `spring-crud` for VelaWear CRUD feature creation.
  - `testing` for unit and integration test workflows.
  - `review-pr` for code review checklists.
  - `flyway` for PostgreSQL/Flyway migration conventions.
- Split detailed skill checklists into `references/` files to keep each `SKILL.md` concise.
- Refined `review-pr` checklist around practical VelaWear architecture fit, security, naming consistency, performance risks, tests, docs, and project-specific rules without overloading reviews with unnecessary patterns.
- Added optional SOLID and Clean Architecture review lenses that should be applied only when changed code is complex enough to need them.
- Updated Flyway skill conventions to prefer identity-backed `BIGINT` primary keys, use `uidx_` for unique indexes, and reuse existing trigger functions/triggers.
- Simplified `docs/DATABASE.md` index strategy:
  - Removed low-value status/type/date indexes requested for cleanup.
  - Stopped listing indexes that PostgreSQL already creates for primary key and unique constraints.
  - Moved unique business rules into table constraints instead of the index list.

## 2026-06-20

### Completed

- Rebuilt Flyway base migrations to match the current `docs/DATABASE.md` VelaWear schema:
  - Replaced old UUID/customer/shipment schema with 28-table identity-backed `BIGINT` schema.
  - Added named primary key, foreign key, unique, and check constraints.
  - Added explicit indexes only for FK lookup, cleanup, and selected range/history queries.
  - Added partial unique indexes with `uidx_` for conditional uniqueness.
  - Added reusable `set_updated_at()` trigger function and table triggers.
- Replaced RBAC seed migration with roles, permissions, and `permission_role` mappings for the current schema.
- Added dev-only repeatable Flyway mock data under `db/dev`.
- Configured `application-dev.yml` to load `classpath:db/migration,classpath:db/dev` while shared/test/prod Flyway locations remain schema-only.
- Updated `docs/DATABASE.md` to document identity-backed `BIGINT` primary keys.

### Notes

- Existing local databases that already applied the old `V1/V2` migrations need to be dropped, cleaned, or repaired before rerunning Flyway.
- Dev mock accounts use raw password `Password123!` represented by a BCrypt hash in SQL.
