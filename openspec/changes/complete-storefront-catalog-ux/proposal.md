## Why

Customer-facing product lists currently reuse admin-style product queries: price sorting targets a nonexistent Product field, filters use base variant prices rather than campaign-effective prices, and public color/size facet calls are protected. Reviews also trust a client-supplied user id and do not connect the existing review image table to create/read flows.

## What Changes

- Add a dedicated public storefront catalog endpoint with validated multi-facets, effective-price filtering/sorting, deterministic featured order, and coherent facet counts.
- Keep existing product/admin endpoints compatible.
- Add privacy-safe product review summaries and paged public reads with images.
- Scope customer review reads/writes to the authenticated principal and accept optional images in one multipart transaction.
- Add rollback-safe UUID file persistence, direct-upload restrictions for review media, and orphan reconciliation.
- Add detailed Vietnamese API, algorithm, security, operations, and troubleshooting documentation.

## Capabilities

### New Capabilities
- `storefront-catalog-api`: Public effective-price catalog, facets, and deterministic storefront sorting.
- `verified-product-reviews`: Principal-scoped completed-order review creation and privacy-safe public review discovery with media.

## Impact

- Affected modules: product, product variant/pricing, category/color/size reads, review, file storage, order item eligibility, security, and multipart configuration.
- No database migration is expected because review images and the review uniqueness constraint already exist.
- Deployment order is backend first, then the coordinated frontend change.
