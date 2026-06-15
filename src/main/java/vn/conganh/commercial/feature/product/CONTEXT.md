# Product Feature Context

Manages base products stored in `products`.

- `sku` and `slug` are created once and immutable through update.
- Delete is a soft archive by setting `status = ARCHIVED` and `deleted_at`.
- Product variants and inventory should be implemented as separate features because they have their own workflows.
