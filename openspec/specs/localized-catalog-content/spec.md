# localized-catalog-content Specification

## Purpose
TBD - created by archiving change add-i18n-translations. Update Purpose after archive.
## Requirements
### Requirement: Supported catalog locales
The system SHALL maintain a set of supported catalog locales and MUST treat Vietnamese (`vi`) as the default enabled locale.

#### Scenario: Default locale exists
- **WHEN** the application is seeded or migrated for localized catalog content
- **THEN** the locale set contains an enabled `vi` locale marked as the default

#### Scenario: Unsupported locale is requested
- **WHEN** a catalog request specifies a locale that is not enabled
- **THEN** the system resolves the request using the default `vi` locale

### Requirement: Product localized content
The system SHALL store product user-facing content separately per locale, including name, slug, short description, description, material, care instruction, SEO title, and SEO description.

#### Scenario: Product has Vietnamese translation
- **WHEN** a product has a `vi` translation
- **THEN** the product can be returned with Vietnamese name, slug, descriptions, material/care text, and SEO metadata

#### Scenario: Product slug uniqueness per locale
- **WHEN** two product translations use the same locale
- **THEN** their slugs MUST be unique within that locale

### Requirement: Category localized content
The system SHALL store category user-facing content separately per locale, including name, slug, description, SEO title, and SEO description.

#### Scenario: Category has Vietnamese translation
- **WHEN** a category has a `vi` translation
- **THEN** the category can be returned with Vietnamese name, slug, description, and SEO metadata

#### Scenario: Category slug uniqueness per locale
- **WHEN** two category translations use the same locale
- **THEN** their slugs MUST be unique within that locale

### Requirement: Catalog locale resolution
The system SHALL resolve catalog content using the requested locale when available and MUST fall back to the default Vietnamese locale when the requested translation is missing.

#### Scenario: Explicit locale is provided
- **WHEN** a catalog request provides an enabled locale parameter
- **THEN** product and category content is resolved using that locale when matching translations exist

#### Scenario: Requested translation is missing
- **WHEN** a catalog request asks for an enabled locale but a product or category lacks that translation
- **THEN** the system returns the default `vi` translation for the missing localized content

#### Scenario: No locale is provided
- **WHEN** a catalog request does not provide a locale
- **THEN** product and category content is resolved using the default `vi` locale

### Requirement: Localized catalog API response
The system SHALL return localized product and category DTO fields for the resolved locale instead of returning all translations to the storefront.

#### Scenario: Product detail response is localized
- **WHEN** the storefront requests a product detail page with a resolved locale
- **THEN** the response contains localized `name`, `slug`, description fields, material/care text, and SEO fields for that locale or its fallback

#### Scenario: Category response is localized
- **WHEN** the storefront requests category data with a resolved locale
- **THEN** the response contains localized `name`, `slug`, description, and SEO fields for that locale or its fallback

### Requirement: Language-neutral variant attributes
The system SHALL keep color and size as language-neutral product variant attributes and MUST NOT require database translation tables for color or size in this phase.

#### Scenario: Product variant includes color
- **WHEN** a product variant is returned by a catalog API
- **THEN** its color is represented using stable technical data such as color code and hex value

#### Scenario: Product variant includes size
- **WHEN** a product variant is returned by a catalog API
- **THEN** its size is represented using a stable size code such as `XS`, `M`, `XXL`, or `35`

