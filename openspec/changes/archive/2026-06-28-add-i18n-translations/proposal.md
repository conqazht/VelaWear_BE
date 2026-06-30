## Why

The catalog currently has no explicit localization model, so product and category content cannot be served consistently in Vietnamese by default or expanded to another language later. Adding a small, catalog-focused i18n layer solves this without over-modeling technical values like size codes and color swatches.

## What Changes

- Add supported locales with Vietnamese (`vi`) as the default locale.
- Add localized product content for names, slugs, descriptions, material/care text, and SEO fields.
- Add localized category content for names, slugs, descriptions, and SEO fields.
- Resolve product and category API responses using the requested locale with fallback to the default locale when a translation is missing.
- Keep color and size as stable technical data in the core schema; do not add database translation tables for color or size in this phase.
- Establish the backend API contract for locale passing so frontend catalog work can be implemented separately.

## Capabilities

### New Capabilities

- `localized-catalog-content`: Catalog products and categories can be stored and served with locale-specific content, defaulting to Vietnamese.

### Modified Capabilities

- None.

## Impact

- Database migrations for `locales`, `product_translations`, and `category_translations`.
- Catalog query and DTO mapping changes for products and categories.
- Product/category seed data updates to include Vietnamese default translations and optional secondary-language rows.
- API locale resolution via query parameter or request header.
- Frontend API client/request flow is tracked separately in the `commercial-fe` project.
