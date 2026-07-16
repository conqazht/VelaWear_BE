# Permission Feature Context

Manages RBAC permissions stored in `permissions`.

- Base permissions are created by Flyway in `V2__seed_rbac.sql`; later migrations
  evolve both permission definitions and role mappings.
- Permission codes are immutable through the update endpoint to avoid breaking assignments.
- `V22__revoke_legacy_customer_permissions.sql` removes only the 27 legacy
  `permission_role` associations for production role `USER`. It matches
  `api_path + method` so renamed development permissions are also handled, and it
  does not delete permission rows or mappings of other roles.
- Method/path RBAC answers whether a role may enter an endpoint; it does not prove
  resource ownership. Customer resources must use principal-bound `/me` services.
