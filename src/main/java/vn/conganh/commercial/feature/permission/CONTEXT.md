# Permission Feature Context

Manages RBAC permissions stored in `permissions`.

- Seed permissions are created by Flyway in `V2__seed_rbac.sql`.
- Permission codes are immutable through the update endpoint to avoid breaking assignments.
