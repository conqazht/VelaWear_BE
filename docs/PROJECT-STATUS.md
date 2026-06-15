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
