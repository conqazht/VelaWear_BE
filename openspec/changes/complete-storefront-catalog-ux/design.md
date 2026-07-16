## Context

`Product` has no `price`, so Spring sort `price,asc|desc` fails with 400. Existing min/max filters use variant base price while cards resolve campaign pricing only after database pagination. Public review responses expose order/user identifiers, review images are disconnected, and create review trusts `userId` from the body.

## Goals / Non-Goals

**Goals:** correct effective-price product discovery; multi-select facets with same-variant semantics; deterministic sorts and paging; privacy-safe paged reviews; principal-owned completed-order review submission with images; rollback and crash orphan protection; Vietnamese operational documentation.

**Non-Goals:** replacing admin product CRUD, review edit/delete/moderation, shared-CDN caching of personalized price responses, or a denormalized price read model at the current catalog size.

## Decisions

### Dedicated storefront read model
`GET /api/v1/storefront/products` binds a validated storefront request rather than raw `Pageable` sort properties. It loads active candidates and variants, resolves canonical pricing once, filters/sorts globally, slices after sorting, and returns results plus disjunctive facets.

### Effective price is canonical
Color, size, min/max price, representative card price, facets, and price sorts use the same resolved variant-price snapshot. A product matches only when one active variant satisfies all selected variant constraints.

### Featured order uses a deterministic tuple
Promotion tier (`FLASH`, `STANDARD`, `BASE`), discount percentage, average rating, review count, creation time, and id are compared in that order.

### Review ownership comes from JWT
The controller supplies the authenticated JWT and the service resolves the user from its subject. Missing/foreign order items are indistinguishable, only `COMPLETED` orders qualify, and one review per order item is enforced both before save and by the unique constraint.

### Review media is request-bound multipart data
The review endpoint accepts binary parts, never client paths. Files are validated before writes, named with UUIDs, atomically moved, tracked for transaction rollback, and reconciled when old and unreferenced. Direct generic upload to the review folder is denied.

## Risks / Trade-offs

- The in-memory storefront snapshot is correct for the current small catalog but must be replaced by a read model if the candidate set grows materially.
- Effective price can be personalized by quota state; authenticated responses are not shared-cacheable and checkout remains authoritative.
- Filesystem and database transactions cannot be truly atomic; transaction synchronization handles normal rollbacks and the 24-hour reconciler handles process crashes.

## Migration Plan

1. Add storefront DTOs/service/controller without changing existing `/products` behavior.
2. Add review summaries, privacy-safe DTOs, principal-scoped reads/writes, and media persistence.
3. Permit only the public storefront and product-review GET routes.
4. Deploy backend, verify old product/admin clients, then deploy the coordinated frontend.
