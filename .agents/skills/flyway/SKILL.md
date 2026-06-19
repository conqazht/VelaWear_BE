---
name: flyway
description: Create or review PostgreSQL Flyway migrations for the VelaWear backend. Use when asked to add schema migrations, alter tables, add indexes, seed RBAC data, validate docs/DATABASE.md against SQL, or enforce migration naming, constraints, identity primary keys, unique index naming with uidx_, soft delete, audit timestamp, trigger reuse, and PostgreSQL conventions.
---

# Flyway

Use this skill for database migration work.

## Workflow

1. Read `docs/PROJECT-RULES.md`.
2. Read `docs/DATABASE.md`.
3. Inspect existing files under `src/main/resources/db/migration`.
4. Choose the next migration version number.
5. Write PostgreSQL-only SQL.
6. Keep migrations idempotent where appropriate, especially seed data.
7. Add explicit primary keys, foreign keys, checks, indexes, and trigger names.
8. Prefer identity-backed `BIGINT` primary keys and `uidx_` prefixes for unique indexes.
9. Reuse existing trigger functions and table triggers; do not recreate them unless intentionally changing behavior.
10. Update `docs/DATABASE.md` and `docs/PROJECT-STATUS.md` when schema changes.
11. Validate with PostgreSQL/Flyway when the environment is available.

Read `references/conventions.md` before creating or reviewing migrations.
