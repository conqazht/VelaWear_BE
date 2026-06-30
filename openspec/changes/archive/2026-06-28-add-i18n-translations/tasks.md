## 1. Database Schema and Seed Data

- [x] 1.1 Add a `locales` migration with `vi` seeded as the default enabled locale.
- [x] 1.2 Add `product_translations` with product foreign key, locale foreign key, localized catalog fields, primary key on product and locale, and unique slug per locale.
- [x] 1.3 Add `category_translations` with category foreign key, locale foreign key, localized catalog fields, primary key on category and locale, and unique slug per locale.
- [x] 1.4 Backfill Vietnamese translation rows for every existing product and category in development seed data.
- [x] 1.5 Verify no `color_translations` or `size_translations` tables are introduced and existing color/size seed data remains technical.

## 2. Backend Locale Resolution and Catalog Mapping

- [x] 2.1 Add backend locale resolution using explicit request locale first, language header second, and default `vi` fallback.
- [x] 2.2 Add entity/repository mappings for locales, product translations, and category translations.
- [x] 2.3 Update product list/detail queries to load the requested translation and fall back to the default Vietnamese translation when needed.
- [x] 2.4 Update category list/detail queries to load the requested translation and fall back to the default Vietnamese translation when needed.
- [x] 2.5 Update localized slug lookup behavior for products and categories using locale-specific slug uniqueness.
- [x] 2.6 Update catalog DTO mapping so product and category responses expose localized fields directly while keeping color and size language-neutral.

## 3. Backend Verification

- [x] 3.1 Add or update backend tests for locale resolution, unsupported locale fallback, and missing translation fallback.
- [x] 3.2 Add or update backend tests for product/category localized slug uniqueness and localized DTO mapping.
- [x] 3.3 Verify development seed data loads with Vietnamese translations for all current products and categories.
- [x] 3.4 Run backend compile and focused backend unit tests.
