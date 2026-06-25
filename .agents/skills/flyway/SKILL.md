---
name: flyway
description: Create or review PostgreSQL Flyway migrations for the VelaWear backend. Use when adding or reviewing schema migrations, altering tables, adding indexes or constraints, seeding RBAC/static data, or checking database docs against SQL.
---

# Flyway

Use this skill for database migration work.

## Workflow

1. Read `docs/PROJECT-RULES.md`.
2. Read `docs/DATABASE.md`.
3. Inspect existing files under `src/main/resources/db/migration`.
4. Read `docs/FLYWAY.md`.
5. Choose the next migration version number.
6. Write PostgreSQL-only SQL.
7. Keep seed data idempotent where appropriate.
8. Update `docs/DATABASE.md` and `docs/PROJECT-STATUS.md` when schema changes.
9. Validate with PostgreSQL/Flyway when the environment is available.

## Hard Rules

- Do not edit an applied migration in a shared environment.
- Reuse existing trigger functions and table triggers when possible.
- Keep migrations aligned with `docs/DATABASE.md`.
