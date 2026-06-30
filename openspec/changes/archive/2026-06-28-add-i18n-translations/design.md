## Context

The catalog needs localized product and category content, with Vietnamese as the default language. Current catalog data treats user-facing text and technical catalog attributes together, which makes it hard to serve Vietnamese-first content consistently while keeping the option to add English or another locale later.

The first phase should stay narrow: localize product/category content stored in the database, align frontend/backend locale passing, and leave size/color as stable technical attributes.

## Goals / Non-Goals

**Goals:**

- Store supported locales with `vi` as the default enabled locale.
- Store product translations for locale-specific names, slugs, descriptions, material/care text, and SEO metadata.
- Store category translations for locale-specific names, slugs, descriptions, and SEO metadata.
- Resolve catalog API responses using the requested locale, with fallback to Vietnamese when a translation is missing.
- Keep product variants, colors, sizes, stock, and pricing language-neutral.
- Expose a backend contract that lets the frontend pass the active locale consistently when calling catalog APIs.

**Non-Goals:**

- Do not add `color_translations` or `size_translations` tables in this phase.
- Do not duplicate products, categories, variants, prices, or inventory per language.
- Do not move frontend UI copy into the backend database.
- Do not implement a generic `translations(entity_type, field_name, value)` table for core catalog data.
- Do not require all future locales to be fully translated before they can be enabled for testing.
- Do not implement storefront locale state, frontend API client changes, or frontend cache-key changes in this backend-scoped change.

## Decisions

### Use explicit translation tables for core catalog entities

Add `product_translations` and `category_translations` instead of a generic translation table.

Rationale: product/category translations need constraints, locale-specific slugs, search-friendly fields, and predictable query shapes. Explicit tables keep the Java model and API mapping easier to reason about than an untyped key/value table.

Alternative considered: a generic translations table keyed by entity type, entity id, field name, and locale. This is more flexible, but it weakens constraints, complicates query performance, and makes slug uniqueness harder.

### Keep colors and sizes language-neutral

Colors remain `code + hex`, and sizes remain stable codes such as `XS`, `M`, `XXL`, and `35` through `45`.

Rationale: these values are primarily technical selectors and visual attributes. The frontend can display color swatches and size codes without database translations. If accessibility labels are needed, they can live in frontend dictionaries.

Alternative considered: adding color/size translation tables. This adds schema and seed-data work without enough user-facing benefit for the current product scope.

### Default to Vietnamese and fallback predictably

The locale resolver should use this precedence:

1. Explicit `locale` query parameter.
2. Request language header, such as `Accept-Language`.
3. Default enabled locale, initially `vi`.

If a requested translation is unavailable, the backend should return the Vietnamese translation. If the Vietnamese translation is unexpectedly missing, the backend can fall back to the current core entity text during migration.

Rationale: Vietnamese is the product's default audience language, while fallback prevents broken product/category pages during incremental translation.

### Return localized DTOs, not translation maps

Catalog APIs should return the resolved localized fields directly, such as `name`, `slug`, `description`, and SEO fields. They do not need to return all available translations to the storefront.

Rationale: storefront rendering only needs one active locale at a time. Admin translation management can use a separate shape later if needed.

### Make slugs unique per locale

Product and category translation tables should enforce unique slugs per locale.

Rationale: `/vi/...` and `/en/...` can have different readable slugs, but each locale still needs stable routing and SEO uniqueness.

## Risks / Trade-offs

- Missing translations can produce incomplete pages -> Seed Vietnamese rows for all existing products/categories and implement default-locale fallback.
- Locale-specific joins can introduce N+1 queries -> Fetch translations in catalog list/detail queries using joins or batched repository methods.
- Slug migration may hit collisions -> Add unique constraints and review generated seed slugs before rollout.
- Frontend caching can mix languages -> Include locale in query keys, cache keys, or request URLs.
- `Accept-Language` can be complex -> Start with simple supported-locale matching and prefer explicit `?locale=` when present.
- Admin translation workflows are not covered -> Keep schema compatible with future admin editing, but leave UI/workflow for a later change.

## Migration Plan

1. Add `locales`, `product_translations`, and `category_translations` tables.
2. Seed `vi` as the default enabled locale.
3. Backfill one Vietnamese translation row for every existing product and category.
4. Add locale resolution in backend catalog APIs.
5. Update catalog queries/DTO mapping to use localized product/category fields with fallback.
6. Validate backend catalog APIs in the default Vietnamese locale.

Rollback can keep the new tables unused while restoring DTO mapping to the previous core fields. The migration should avoid dropping existing product/category text until localized reads are verified.

## Open Questions

- Should the first seed include English (`en`) sample translations, or only Vietnamese default rows?
- Should the public URL structure include locale path segments later, such as `/vi/products/...`, or should this phase only pass locale through API requests?
- Should catalog API responses expose `resolvedLocale` for debugging and cache clarity?
