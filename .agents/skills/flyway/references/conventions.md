# Flyway Convention

## Migration Naming

Use versioned migrations:

```text
V{number}__{action}_{object}.sql
```

Examples:

```text
V1__init_commercial_schema.sql
V2__seed_rbac.sql
V3__add_product_variant_status.sql
V4__create_coupon_tables.sql
```

Rules:
- Use one increasing integer per migration.
- Use lowercase snake_case after `__`.
- Never edit an applied migration in shared environments.
- Add a new migration for every schema change after the migration has been shared.

## PostgreSQL Rules

- Use PostgreSQL syntax only.
- Prefer identity-backed `BIGINT` primary keys over `BIGSERIAL`.
- Use `BIGSERIAL` only when maintaining compatibility with an existing table style.
- Use `TIMESTAMPTZ` for audit timestamps.
- Use `NUMERIC(15,2)` for money.
- Use `JSONB` only for flexible external payloads.
- Use `BOOLEAN DEFAULT false` for boolean flags.
- Use `CHECK` constraints for money, quantity, rating, and valid ranges.

## Audit Columns

For tables with audit fields:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
```

Use a reusable trigger function for `updated_at`. Do not create duplicate trigger functions if one already exists in earlier migrations.

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Rules:
- Create `set_updated_at()` only once in the initial migration that needs it, or use `CREATE OR REPLACE FUNCTION` only when intentionally changing the function body.
- For later tables, reuse the existing `set_updated_at()` function.
- Add one `BEFORE UPDATE` trigger per table with `updated_at`.
- Do not recreate an existing table trigger in later migrations.

## Soft Delete

Use:

```sql
deleted_at TIMESTAMPTZ NULL
```

Every `deleted_at` column must have an index:

```sql
CREATE INDEX idx_{table}_deleted_at ON {table}(deleted_at);
```

Document cleanup or retention behavior in `docs/DATABASE.md`.

## Constraints & Indexes

Name constraints and indexes clearly:

```sql
CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
```

Index naming:

```text
idx_{table}_{field}
idx_{table}_{field1}_{field2}
```

Unique index naming:

```text
uidx_{table}_{field}
uidx_{table}_{field1}_{field2}
```

Use partial unique indexes for nullable unique business keys:

```sql
CREATE UNIQUE INDEX uidx_payment_transactions_transaction_code
ON payment_transactions(transaction_code)
WHERE transaction_code IS NOT NULL;
```

## Join Tables

For pure join tables such as RBAC:

```sql
PRIMARY KEY (user_id, role_id)
```

Use `ON DELETE CASCADE` for both foreign keys only on pure join tables.

Do not cascade-delete business history tables such as orders, payments, reviews, or inventory logs.

## Seed Data

Use idempotent inserts:

```sql
INSERT INTO roles (name, description)
VALUES ('ADMIN', 'Full system administration')
ON CONFLICT (name) DO NOTHING;
```

Seed only stable base data:
- roles
- permissions
- role-permission mappings
- static master data when agreed

## Verification

After adding a migration:
- Ensure `docs/DATABASE.md` matches the migration.
- Ensure entities match the final schema.
- Run compile or migration validation when the PostgreSQL environment is available.
