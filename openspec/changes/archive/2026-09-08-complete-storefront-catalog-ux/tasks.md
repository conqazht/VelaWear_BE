## 1. Storefront Catalog API
- [x] 1.1 Add validated storefront query, result, facet, and price-range DTO records
- [x] 1.2 Add active product/variant loading and canonical effective-price snapshot assembly
- [x] 1.3 Implement multi-facet same-variant filtering, deterministic sorts, paging, and disjunctive counts
- [x] 1.4 Expose anonymous/optional-auth GET endpoint while preserving existing product/admin behavior

## 2. Verified Reviews
- [x] 2.1 Add public summary and paged/filterable privacy-safe review responses with batch-loaded images
- [x] 2.2 Add principal-scoped `/reviews/me` reads and remove client-supplied identity from create flow
- [x] 2.3 Add multipart review creation with eligibility, validation, uniqueness, and media limits
- [x] 2.4 Add UUID/atomic file persistence, rollback cleanup, direct-folder restrictions, and orphan reconciliation

## 3. Verification and Documentation
- [x] 3.1 Test catalog validation, visibility, same-variant facets, effective pricing, four sorts, counts, and pagination
- [x] 3.2 Test review privacy, ownership, completion, duplicate races, images, rollback, and cleanup
- [x] 3.3 Write `docs/STOREFRONT_CATALOG_UX_BACKEND_VI.md` and link it from README
- [x] 3.4 Update API_SPEC, product/review/file context, filter decision, and PROJECT-STATUS
- [x] 3.5 Run targeted tests, clean verify, strict OpenSpec, and diff checks
