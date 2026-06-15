# Role Feature Context

Manages RBAC roles stored in `roles`.

- Seeded system roles are created by Flyway in `V2__seed_rbac.sql`.
- Deleting a role is a hard delete and relies on database foreign-key restrictions/cascades.
